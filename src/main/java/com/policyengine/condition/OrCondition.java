package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Composite OR: at least one child must match. */
public class OrCondition implements Condition {

    private final List<Condition> children;

    public OrCondition(List<Condition> children) {
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    public ConditionResult evaluate(EvaluationContext ctx) {
        for (Condition child : children) {
            if (child.evaluate(ctx).matched()) {
                return ConditionResult.allow("OR passed: at least one condition was true");
            }
        }
        return ConditionResult.deny("OR failed: no child condition matched");
    }

    @Override
    public ConditionTrace trace(EvaluationContext ctx) {
        List<ConditionTrace> childTraces = new ArrayList<>();
        boolean anyTrue = false;
        for (Condition child : children) {
            ConditionTrace ct = child.trace(ctx);
            childTraces.add(ct);
            if (ct.result()) anyTrue = true;
        }
        String reason = anyTrue
                ? "OR passed: at least one condition was true"
                : "OR failed: no child condition matched";
        return ConditionTrace.composite("OR", anyTrue, reason, childTraces);
    }

    public List<Condition> getChildren() { return children; }
}
