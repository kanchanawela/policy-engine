package com.policyengine.conflict;

import com.policyengine.model.Effect;

import java.util.List;

/**
 * Deny Override: evaluates ALL matching statements.
 * If ANY matching statement is a DENY, the final decision is DENY.
 * Only when every matching statement is ALLOW does ALLOW win.
 * No match → implicit DENY.
 *
 * This is the most conservative and typically the safest default.
 */
public class DenyOverrideStrategy implements ConflictResolutionStrategy {

    @Override
    public ConflictResult resolve(List<StatementEvaluation> evaluations) {
        List<StatementEvaluation> matching = evaluations.stream()
                .filter(StatementEvaluation::matches)
                .toList();

        // Any DENY wins
        for (StatementEvaluation eval : matching) {
            if (eval.effect() == Effect.DENY) {
                return ConflictResult.explicit(Effect.DENY, eval.statement().original().getId());
            }
        }

        // At least one ALLOW and no DENY
        for (StatementEvaluation eval : matching) {
            if (eval.effect() == Effect.ALLOW) {
                return ConflictResult.explicit(Effect.ALLOW, eval.statement().original().getId());
            }
        }

        return ConflictResult.implicitDeny();
    }

    @Override
    public String name() { return "DenyOverride"; }
}
