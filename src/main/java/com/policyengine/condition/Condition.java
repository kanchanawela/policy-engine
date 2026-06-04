package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

/**
 * A node in the condition tree. Both composite (AND/OR/NOT) and leaf conditions implement this.
 */
public interface Condition {

    /**
     * Evaluates this condition against the given context.
     * For performance, composite conditions may short-circuit.
     */
    ConditionResult evaluate(EvaluationContext ctx);

    /**
     * Evaluates this condition and produces a full recursive trace for audit purposes.
     * Unlike {@link #evaluate}, composites do NOT short-circuit — all children are traced.
     */
    ConditionTrace trace(EvaluationContext ctx);
}
