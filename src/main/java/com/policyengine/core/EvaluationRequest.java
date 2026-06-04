package com.policyengine.core;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable input to the policy engine.
 *
 * @param requestId unique ID for this evaluation; auto-generated if null/blank
 * @param principal the identity making the request (e.g. "user:alice", "role:admin")
 * @param resource  the resource being accessed (e.g. "arn:data:reports/q1")
 * @param action    the action being performed (e.g. "read", "delete")
 * @param context   additional key-value attributes (e.g. user.department, request.ip)
 */
public record EvaluationRequest(
        String requestId,
        String principal,
        String resource,
        String action,
        Map<String, String> context
) {
    public EvaluationRequest {
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        context = (context != null) ? Map.copyOf(context) : Map.of();
    }

    public EvaluationRequest(String principal, String resource, String action,
                              Map<String, String> context) {
        this(null, principal, resource, action, context);
    }

    public EvaluationRequest(String principal, String resource, String action) {
        this(null, principal, resource, action, Map.of());
    }
}
