package com.policyengine.core;

import com.policyengine.audit.AuditLog;
import com.policyengine.cache.CaffeineCompiledPolicyCache;
import com.policyengine.condition.ConditionEvaluator;
import com.policyengine.conflict.ConflictResolutionStrategy;
import com.policyengine.conflict.DenyOverrideStrategy;
import com.policyengine.parse.ConditionParser;
import com.policyengine.parse.PolicyCompiler;
import com.policyengine.parse.PolicyParser;
import com.policyengine.specificity.DefaultSpecificityScorer;
import com.policyengine.time.SystemTimeProvider;
import com.policyengine.time.TimeConditionEvaluator;
import com.policyengine.time.TimeProvider;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Builder for configuring and constructing a {@link PolicyEngine}.
 *
 * <pre>{@code
 * PolicyEngine engine = EngineConfig.builder()
 *     .strategy(new DenyOverrideStrategy())
 *     .auditConsumer(log -> System.out.println(AuditSerializer.toSummaryLine(log)))
 *     .build()
 *     .buildEngine();
 * }</pre>
 */
public class EngineConfig {

    private final ConflictResolutionStrategy strategy;
    private final TimeProvider timeProvider;
    private final int cacheMaxSize;
    private final Duration cacheExpiry;
    private final Consumer<AuditLog> auditConsumer;
    private final int asyncAuditThreads;

    private EngineConfig(Builder b) {
        this.strategy         = b.strategy;
        this.timeProvider     = b.timeProvider;
        this.cacheMaxSize     = b.cacheMaxSize;
        this.cacheExpiry      = b.cacheExpiry;
        this.auditConsumer    = b.auditConsumer;
        this.asyncAuditThreads = b.asyncAuditThreads;
    }

    /** Constructs the fully wired {@link PolicyEngine}. */
    public PolicyEngine buildEngine() {
        TimeConditionEvaluator timeEval  = new TimeConditionEvaluator(timeProvider);
        ConditionEvaluator condEval      = new ConditionEvaluator(timeEval);
        ConditionParser condParser       = new ConditionParser(condEval);
        PolicyParser policyParser        = new PolicyParser();
        PolicyCompiler compiler          = new PolicyCompiler(condParser);
        DefaultSpecificityScorer scorer  = new DefaultSpecificityScorer();
        CaffeineCompiledPolicyCache cache =
                new CaffeineCompiledPolicyCache(cacheMaxSize, cacheExpiry);
        PolicyRepository repository      = new PolicyRepository();

        return new PolicyEngineImpl(repository, cache, policyParser, compiler,
                strategy, scorer, timeProvider, auditConsumer, asyncAuditThreads);
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private ConflictResolutionStrategy strategy     = new DenyOverrideStrategy();
        private TimeProvider               timeProvider = new SystemTimeProvider();
        private int                        cacheMaxSize = 500;
        private Duration                   cacheExpiry  = Duration.ofMinutes(30);
        private Consumer<AuditLog>         auditConsumer = null;
        private int                        asyncAuditThreads = 2;

        private Builder() {}

        /** Sets the conflict resolution strategy. Default: {@link DenyOverrideStrategy}. */
        public Builder strategy(ConflictResolutionStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /** Overrides the clock; useful for injecting a fixed clock in tests. */
        public Builder timeProvider(TimeProvider timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }

        /** Maximum number of compiled policies to keep in the Caffeine cache. Default: 500. */
        public Builder cacheMaxSize(int maxSize) {
            this.cacheMaxSize = maxSize;
            return this;
        }

        /** How long a compiled policy stays in cache after it was written. Default: 30 min. */
        public Builder cacheExpiry(Duration expiry) {
            this.cacheExpiry = expiry;
            return this;
        }

        /**
         * Optional consumer called asynchronously after each evaluation.
         * Use this to write structured audit records to a log, database, or queue.
         */
        public Builder auditConsumer(Consumer<AuditLog> consumer) {
            this.auditConsumer = consumer;
            return this;
        }

        /** Number of threads in the async audit delivery pool. Default: 2. */
        public Builder asyncAuditThreads(int threads) {
            this.asyncAuditThreads = threads;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(this);
        }
    }
}
