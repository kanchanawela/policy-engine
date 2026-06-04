package com.policyengine.conflict;

import java.util.List;

/**
 * First Match: the first matching statement (in policy-priority order, then statement order)
 * determines the final decision. No further statements are consulted.
 * No match → implicit DENY.
 *
 * The evaluation list passed from PolicyEngineImpl is already sorted by
 * (policyPriority DESC, statementIndex ASC), so this strategy simply picks the first match.
 */
public class FirstMatchStrategy implements ConflictResolutionStrategy {

    @Override
    public ConflictResult resolve(List<StatementEvaluation> evaluations) {
        for (StatementEvaluation eval : evaluations) {
            if (eval.matches()) {
                return ConflictResult.explicit(eval.effect(), eval.statement().original().getId());
            }
        }
        return ConflictResult.implicitDeny();
    }

    @Override
    public String name() { return "FirstMatch"; }
}
