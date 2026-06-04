package com.policyengine.audit;

import com.policyengine.condition.ConditionTrace;
import com.policyengine.model.Effect;

/**
 * Immutable audit trace for a single statement evaluation.
 */
public record StatementTrace(
        String statementId,
        Effect effect,
        boolean targetMatched,
        TargetMatchDetail targetMatchDetail,   // null when targetMatched == false
        boolean conditionMatched,
        ConditionTrace conditionTrace,
        int specificityScore,
        boolean isDecidingStatement
) {}
