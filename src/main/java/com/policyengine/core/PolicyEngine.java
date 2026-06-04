package com.policyengine.core;

import com.policyengine.model.Policy;

public interface PolicyEngine {

    /**
     * Evaluates the given request against all loaded policies.
     *
     * @param request the access request (principal, resource, action, context)
     * @return the decision (ALLOW or DENY) together with a full audit log
     */
    EvaluationResult evaluate(EvaluationRequest request);

    /** Parses and loads a policy from its JSON representation. */
    void loadPolicy(String policyJson);

    /** Loads an already-parsed policy. */
    void loadPolicy(Policy policy);

    /** Removes a policy and invalidates its cache entry. */
    void removePolicy(String policyId);

    /** Invalidates the compiled cache for a policy (forces recompilation on next evaluate). */
    void invalidateCache(String policyId);
}
