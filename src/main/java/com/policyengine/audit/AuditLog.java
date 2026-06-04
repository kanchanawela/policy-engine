package com.policyengine.audit;

import com.policyengine.core.EvaluationRequest;
import com.policyengine.model.Effect;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, complete audit record for a single policy evaluation.
 * Every field needed to reconstruct the full decision rationale is present.
 *
 * @param requestId           UUID, unique per evaluation
 * @param timestamp           wall-clock time at evaluation start
 * @param request             the original evaluation request
 * @param finalDecision       ALLOW or DENY
 * @param strategyUsed        name of the conflict resolution strategy
 * @param policyTraces        per-policy, per-statement evaluation details
 * @param decidingStatementId the statement that determined the outcome; null on implicit deny
 * @param implicitDenyReason  non-null only when no statement matched
 * @param evaluationDuration  total time spent in evaluate()
 */
public record AuditLog(
        String requestId,
        Instant timestamp,
        EvaluationRequest request,
        Effect finalDecision,
        String strategyUsed,
        List<PolicyTrace> policyTraces,
        String decidingStatementId,
        String implicitDenyReason,
        Duration evaluationDuration
) {
    public AuditLog {
        policyTraces = Collections.unmodifiableList(policyTraces);
    }
}
