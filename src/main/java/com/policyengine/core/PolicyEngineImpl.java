package com.policyengine.core;

import com.policyengine.audit.AuditLog;
import com.policyengine.audit.PolicyTrace;
import com.policyengine.audit.StatementTrace;
import com.policyengine.audit.TargetMatchDetail;
import com.policyengine.cache.CaffeineCompiledPolicyCache;
import com.policyengine.cache.CompiledPattern;
import com.policyengine.cache.CompiledPolicy;
import com.policyengine.cache.CompiledStatement;
import com.policyengine.cache.PolicyCache;
import com.policyengine.condition.ConditionTrace;
import com.policyengine.conflict.ConflictResolutionStrategy;
import com.policyengine.conflict.ConflictResult;
import com.policyengine.conflict.StatementEvaluation;
import com.policyengine.model.Policy;
import com.policyengine.parse.PolicyCompiler;
import com.policyengine.parse.PolicyParser;
import com.policyengine.specificity.SpecificityScorer;
import com.policyengine.time.TimeProvider;
import com.policyengine.wildcard.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Main policy engine implementation.
 *
 * <p>Per-request evaluation flow:
 * <ol>
 *   <li>Sort policies by priority (DESC).</li>
 *   <li>For each policy, look up or compile the {@link CompiledPolicy} from cache.</li>
 *   <li>For each compiled statement: check target (glob matching), then evaluate conditions.</li>
 *   <li>Pass all {@link StatementEvaluation} objects to the {@link ConflictResolutionStrategy}.</li>
 *   <li>Build the immutable {@link AuditLog} and deliver it asynchronously to the audit consumer.</li>
 * </ol>
 *
 * <p>Thread safety: PolicyRepository and the Caffeine cache are both thread-safe.
 * EvaluationContext is created per-request and never shared.
 */
public class PolicyEngineImpl implements PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngineImpl.class);

    private final PolicyRepository repository;
    private final PolicyCache cache;
    private final PolicyParser parser;
    private final PolicyCompiler compiler;
    private final ConflictResolutionStrategy strategy;
    private final SpecificityScorer scorer;
    private final TimeProvider timeProvider;
    private final Consumer<AuditLog> auditConsumer;
    private final ExecutorService auditExecutor;

    PolicyEngineImpl(PolicyRepository repository,
                     PolicyCache cache,
                     PolicyParser parser,
                     PolicyCompiler compiler,
                     ConflictResolutionStrategy strategy,
                     SpecificityScorer scorer,
                     TimeProvider timeProvider,
                     Consumer<AuditLog> auditConsumer,
                     int asyncAuditThreads) {
        this.repository   = repository;
        this.cache        = cache;
        this.parser       = parser;
        this.compiler     = compiler;
        this.strategy     = strategy;
        this.scorer       = scorer;
        this.timeProvider = timeProvider;
        this.auditConsumer = auditConsumer;
        this.auditExecutor = auditConsumer != null
                ? new ThreadPoolExecutor(1, asyncAuditThreads, 60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(1000),
                        new ThreadPoolExecutor.CallerRunsPolicy())
                : null;
    }

    // -------------------------------------------------------------------------
    // PolicyEngine interface
    // -------------------------------------------------------------------------

    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        Instant start = Instant.now();
        EvaluationContext ctx = new EvaluationContext(request, timeProvider.now());

        // Collect all policies sorted by priority descending
        List<Policy> sortedPolicies = repository.getAll().stream()
                .sorted(Comparator.comparingInt(Policy::getPriority).reversed())
                .toList();

        List<StatementEvaluation> allEvaluations = new ArrayList<>();

        for (Policy policy : sortedPolicies) {
            CompiledPolicy compiled = cache.get(policy.getId(), () -> compiler.compile(policy));
            for (CompiledStatement stmt : compiled.statements()) {
                allEvaluations.add(evaluateStatement(stmt, policy, ctx));
            }
        }

        ConflictResult conflictResult = strategy.resolve(allEvaluations);
        Duration duration = Duration.between(start, Instant.now());
        AuditLog auditLog = buildAuditLog(request, conflictResult, allEvaluations, duration);

        log.debug(com.policyengine.audit.AuditSerializer.toSummaryLine(auditLog));

        if (auditConsumer != null) {
            auditExecutor.submit(() -> {
                try {
                    auditConsumer.accept(auditLog);
                } catch (Exception e) {
                    log.warn("Audit consumer threw an exception", e);
                }
            });
        }

        return new EvaluationResult(conflictResult.decision(), auditLog);
    }

    @Override
    public void loadPolicy(String policyJson) {
        Policy policy = parser.parse(policyJson);
        loadPolicy(policy);
    }

    @Override
    public void loadPolicy(Policy policy) {
        repository.add(policy);
        // Eagerly compile and cache
        cache.put(policy.getId(), compiler.compile(policy));
    }

    @Override
    public void removePolicy(String policyId) {
        repository.remove(policyId);
        cache.invalidate(policyId);
    }

    @Override
    public void invalidateCache(String policyId) {
        cache.invalidate(policyId);
    }

    // -------------------------------------------------------------------------
    // Internal evaluation helpers
    // -------------------------------------------------------------------------

    private StatementEvaluation evaluateStatement(CompiledStatement stmt, Policy policy,
                                                   EvaluationContext ctx) {
        // Step 1: target match (principal, resource, action)
        TargetMatchDetail targetDetail = matchTarget(stmt, ctx.request());
        boolean targetMatched = targetDetail != null;

        // Step 2: condition evaluation (only if target matches — avoid unnecessary work)
        ConditionTrace condTrace;
        boolean condMatched;
        if (targetMatched) {
            condTrace = stmt.conditionTree().trace(ctx);
            condMatched = condTrace.result();
        } else {
            condTrace = ConditionTrace.leaf(false, "Target did not match; conditions not evaluated");
            condMatched = false;
        }

        // Step 3: specificity score
        int score = targetMatched ? scorer.score(stmt, ctx.request()) : 0;

        return new StatementEvaluation(
                stmt,
                policy.getId(),
                policy.getVersion(),
                policy.getPriority(),
                stmt.statementIndex(),
                targetMatched,
                targetDetail,
                condMatched,
                condTrace,
                score
        );
    }

    /**
     * Returns {@link TargetMatchDetail} if all three target fields match, null otherwise.
     */
    private TargetMatchDetail matchTarget(CompiledStatement stmt, EvaluationRequest request) {
        String matchedPrincipal = matchFirst(stmt.principalPatterns(), request.principal());
        if (matchedPrincipal == null) return null;

        String matchedResource = matchFirst(stmt.resourcePatterns(), request.resource());
        if (matchedResource == null) return null;

        String matchedAction = matchFirst(stmt.actionPatterns(), request.action());
        if (matchedAction == null) return null;

        return new TargetMatchDetail(matchedPrincipal, matchedResource, matchedAction);
    }

    /** Returns the glob string of the first pattern that matches value, or null. */
    private String matchFirst(List<CompiledPattern> patterns, String value) {
        if (value == null) return null;
        for (CompiledPattern cp : patterns) {
            if (cp.matches(value)) return cp.glob();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Audit log construction
    // -------------------------------------------------------------------------

    private AuditLog buildAuditLog(EvaluationRequest request,
                                   ConflictResult conflict,
                                   List<StatementEvaluation> evaluations,
                                   Duration duration) {
        // Group evaluations by policyId, preserving priority order
        Map<String, List<StatementEvaluation>> byPolicy = new LinkedHashMap<>();
        for (StatementEvaluation eval : evaluations) {
            byPolicy.computeIfAbsent(eval.policyId(), k -> new ArrayList<>()).add(eval);
        }

        List<PolicyTrace> policyTraces = new ArrayList<>();
        for (Map.Entry<String, List<StatementEvaluation>> entry : byPolicy.entrySet()) {
            String policyId = entry.getKey();
            List<StatementEvaluation> policyEvals = entry.getValue();
            String version = policyEvals.get(0).policyVersion();

            List<StatementTrace> stmtTraces = policyEvals.stream()
                    .map(eval -> new StatementTrace(
                            eval.statement().original().getId(),
                            eval.effect(),
                            eval.targetMatched(),
                            eval.targetMatchDetail(),
                            eval.conditionMatched(),
                            eval.conditionTrace(),
                            eval.specificityScore(),
                            isDeciding(eval, conflict)
                    ))
                    .toList();

            policyTraces.add(new PolicyTrace(policyId, version, stmtTraces));
        }

        return new AuditLog(
                request.requestId(),
                Instant.now(),
                request,
                conflict.decision(),
                strategy.name(),
                policyTraces,
                conflict.decidingStatementId(),
                conflict.implicitDenyReason(),
                duration
        );
    }

    private boolean isDeciding(StatementEvaluation eval, ConflictResult conflict) {
        String decidingId = conflict.decidingStatementId();
        if (decidingId == null) return false;
        String stmtId = eval.statement().original().getId();
        return decidingId.equals(stmtId);
    }
}
