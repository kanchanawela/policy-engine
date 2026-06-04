package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Composite AND: all children must match. */
public class AndCondition implements Condition {

    private final List<Condition> children;

    public AndCondition(List<Condition> children) {
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    public ConditionResult evaluate(EvaluationContext ctx) {
        for (Condition child : children) {
            if (!child.evaluate(ctx).matched()) {
                return ConditionResult.deny("AND failed: a child condition was false");
            }
        }
        return ConditionResult.allow("AND passed: all " + children.size() + " conditions satisfied");
    }

    @Override
    public ConditionTrace trace(EvaluationContext ctx) {
        List<ConditionTrace> childTraces = new ArrayList<>();
        boolean allTrue = true;
        for (Condition child : children) {
            ConditionTrace ct = child.trace(ctx);
            childTraces.add(ct);
            if (!ct.result()) allTrue = false;
        }
        String reason = allTrue
                ? "AND passed: all " + children.size() + " conditions satisfied"
                : "AND failed: one or more conditions were false";
        return ConditionTrace.composite("AND", allTrue, reason, childTraces);
    }

    public List<Condition> getChildren() { return children; }
}
