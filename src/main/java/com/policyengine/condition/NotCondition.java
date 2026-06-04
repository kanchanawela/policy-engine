package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

import java.util.List;

/** Composite NOT: inverts its single delegate condition. */
public class NotCondition implements Condition {

    private final Condition delegate;

    public NotCondition(Condition delegate) {
        this.delegate = delegate;
    }

    @Override
    public ConditionResult evaluate(EvaluationContext ctx) {
        boolean inner = delegate.evaluate(ctx).matched();
        boolean result = !inner;
        return ConditionResult.of(result, "NOT(" + inner + ") -> " + result);
    }

    @Override
    public ConditionTrace trace(EvaluationContext ctx) {
        ConditionTrace inner = delegate.trace(ctx);
        boolean result = !inner.result();
        return ConditionTrace.composite("NOT", result,
                "NOT(" + inner.result() + ") -> " + result, List.of(inner));
    }

    public Condition getDelegate() { return delegate; }
}
