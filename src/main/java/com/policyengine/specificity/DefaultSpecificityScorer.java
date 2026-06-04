package com.policyengine.specificity;

import com.policyengine.cache.CompiledStatement;
import com.policyengine.condition.*;
import com.policyengine.core.EvaluationRequest;

/**
 * Scores statement specificity as:
 *   baseSpecificityScore (from target patterns) + 5 × (leaf condition count)
 *
 * Target pattern scores (per field, best pattern wins):
 *   Exact match          = 100
 *   Prefix glob (*-end)  = 60
 *   Mid-wildcard (*)     = 50
 *   ? wildcard only      = 40
 *   Bare *               = 0
 */
public class DefaultSpecificityScorer implements SpecificityScorer {

    @Override
    public int score(CompiledStatement statement, EvaluationRequest request) {
        int leafCount = countLeaves(statement.conditionTree());
        return statement.baseSpecificityScore() + 5 * leafCount;
    }

    private int countLeaves(Condition condition) {
        if (condition instanceof LeafCondition) return 1;
        if (condition instanceof AlwaysTrueCondition) return 0;
        if (condition instanceof AndCondition ac) {
            return ac.getChildren().stream().mapToInt(this::countLeaves).sum();
        }
        if (condition instanceof OrCondition oc) {
            return oc.getChildren().stream().mapToInt(this::countLeaves).sum();
        }
        if (condition instanceof NotCondition nc) {
            return countLeaves(nc.getDelegate());
        }
        return 0;
    }
}
