package com.policyengine.conflict;

import com.policyengine.audit.TargetMatchDetail;
import com.policyengine.cache.CompiledStatement;
import com.policyengine.condition.ConditionTrace;
import com.policyengine.model.Effect;

/**
 * The result of evaluating one policy statement against a request.
 * Passed to the conflict resolution strategy along with all other statement evaluations.
 */
public record StatementEvaluation(
        CompiledStatement statement,
        String policyId,
        String policyVersion,
        int policyPriority,
        int statementIndex,
        boolean targetMatched,
        TargetMatchDetail targetMatchDetail,   // null when targetMatched == false
        boolean conditionMatched,
        ConditionTrace conditionTrace,
        int specificityScore
) {
    /** True only when both target and all conditions matched. */
    public boolean matches() {
        return targetMatched && conditionMatched;
    }

    public Effect effect() {
        return statement.original().getEffect();
    }
}
