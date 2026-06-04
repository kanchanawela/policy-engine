package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

import java.util.List;

/** Sentinel used when a policy statement has no conditions — always matches. */
public final class AlwaysTrueCondition implements Condition {

    @Override
    public ConditionResult evaluate(EvaluationContext ctx) {
        return ConditionResult.allow("No conditions specified; always matches");
    }

    @Override
    public ConditionTrace trace(EvaluationContext ctx) {
        return ConditionTrace.composite("ALWAYS_TRUE", true,
                "No conditions specified; always matches", List.of());
    }
}
