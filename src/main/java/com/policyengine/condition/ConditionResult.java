package com.policyengine.condition;

public record ConditionResult(boolean matched, String reason) {

    public static ConditionResult of(boolean matched, String reason) {
        return new ConditionResult(matched, reason);
    }

    public static ConditionResult allow(String reason) {
        return new ConditionResult(true, reason);
    }

    public static ConditionResult deny(String reason) {
        return new ConditionResult(false, reason);
    }
}
