package com.policyengine.parse;

import com.policyengine.cache.CompiledPattern;
import com.policyengine.cache.CompiledPolicy;
import com.policyengine.cache.CompiledStatement;
import com.policyengine.condition.AlwaysTrueCondition;
import com.policyengine.condition.Condition;
import com.policyengine.model.Policy;
import com.policyengine.model.PolicyStatement;
import com.policyengine.model.Target;
import com.policyengine.wildcard.WildcardMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a parsed {@link Policy} into an immutable {@link CompiledPolicy}.
 * All glob patterns are converted to regex; condition JSON is parsed into a typed AST.
 * Called once per policy load — never on the evaluation hot path.
 */
public class PolicyCompiler {

    private final ConditionParser conditionParser;

    public PolicyCompiler(ConditionParser conditionParser) {
        this.conditionParser = conditionParser;
    }

    public CompiledPolicy compile(Policy policy) {
        List<CompiledStatement> compiledStmts = new ArrayList<>();
        List<PolicyStatement> statements = policy.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            compiledStmts.add(compileStatement(statements.get(i), i));
        }
        return new CompiledPolicy(policy, compiledStmts);
    }

    private CompiledStatement compileStatement(PolicyStatement stmt, int index) {
        Target target = stmt.getTarget() != null ? stmt.getTarget() : new Target();

        List<CompiledPattern> principalPatterns = compilePatterns(target.getPrincipals());
        List<CompiledPattern> resourcePatterns  = compilePatterns(target.getResources());
        List<CompiledPattern> actionPatterns    = compilePatterns(target.getActions());

        Condition conditionTree = (stmt.getConditions() == null || stmt.getConditions().isNull())
                ? new AlwaysTrueCondition()
                : conditionParser.parse(stmt.getConditions());

        int baseScore = computeBaseScore(target);

        return new CompiledStatement(stmt, principalPatterns, resourcePatterns,
                actionPatterns, conditionTree, baseScore, index);
    }

    private List<CompiledPattern> compilePatterns(List<String> globs) {
        List<CompiledPattern> result = new ArrayList<>();
        for (String glob : globs) {
            result.add(CompiledPattern.of(glob));
        }
        return result;
    }

    /**
     * Base specificity score computed from target patterns (before condition bonus).
     * Uses the best (most specific) pattern for each field.
     */
    private int computeBaseScore(Target target) {
        return bestScore(target.getPrincipals())
             + bestScore(target.getResources())
             + bestScore(target.getActions());
    }

    private int bestScore(List<String> patterns) {
        return patterns.stream()
                .mapToInt(WildcardMatcher::specificityScore)
                .max()
                .orElse(0);
    }
}
