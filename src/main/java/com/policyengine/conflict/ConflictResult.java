package com.policyengine.conflict;

import com.policyengine.model.Effect;

/**
 * The outcome of conflict resolution: a decision and the reason for it.
 *
 * @param decision             ALLOW or DENY
 * @param decidingStatementId  the statement that determined the decision; null on implicit deny
 * @param implicitDenyReason   non-null only when no statement matched (implicit deny)
 */
public record ConflictResult(
        Effect decision,
        String decidingStatementId,
        String implicitDenyReason
) {
    public static ConflictResult implicitDeny() {
        return new ConflictResult(Effect.DENY, null,
                "No matching policy statement found; implicit deny applied");
    }

    public static ConflictResult explicit(Effect decision, String statementId) {
        return new ConflictResult(decision, statementId, null);
    }
}
