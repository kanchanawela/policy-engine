package com.policyengine.condition;

import java.util.Collections;
import java.util.List;

/**
 * Immutable tree node capturing the evaluation trace of a condition.
 * Leaf nodes have an empty children list.
 */
public record ConditionTrace(
        String nodeType,
        boolean result,
        String reason,
        List<ConditionTrace> children
) {
    public ConditionTrace {
        children = Collections.unmodifiableList(children);
    }

    public static ConditionTrace leaf(boolean result, String reason) {
        return new ConditionTrace("LEAF", result, reason, List.of());
    }

    public static ConditionTrace timeleaf(boolean result, String reason) {
        return new ConditionTrace("TIME", result, reason, List.of());
    }

    public static ConditionTrace composite(String type, boolean result, String reason,
                                           List<ConditionTrace> children) {
        return new ConditionTrace(type, result, reason, children);
    }
}
