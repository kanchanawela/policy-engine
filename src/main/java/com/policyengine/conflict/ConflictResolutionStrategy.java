package com.policyengine.conflict;

import java.util.List;

/**
 * Determines the final Allow/Deny decision from all per-statement evaluation results.
 * Implementations must be stateless and thread-safe.
 */
public interface ConflictResolutionStrategy {

    /**
     * @param evaluations all statement evaluations for the request, in policy-priority order
     *                    (highest priority first, then statement index order within same priority)
     */
    ConflictResult resolve(List<StatementEvaluation> evaluations);

    String name();
}
