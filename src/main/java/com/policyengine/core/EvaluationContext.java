package com.policyengine.core;

import java.time.ZonedDateTime;

/**
 * Internal per-request evaluation context. Wraps the request and a fixed evaluation timestamp
 * so every condition within the same request sees the same clock value.
 */
public record EvaluationContext(
        EvaluationRequest request,
        ZonedDateTime evaluationTime
) {
    /** Returns the context value for the given key, or null if absent. */
    public String get(String key) {
        return request.context().get(key);
    }

    /** Returns true if the context map contains the given key (regardless of value). */
    public boolean hasKey(String key) {
        return request.context().containsKey(key);
    }
}
