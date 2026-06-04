package com.policyengine.audit;

import java.util.Collections;
import java.util.List;

/**
 * Immutable audit trace for all statements within a single policy.
 */
public record PolicyTrace(
        String policyId,
        String policyVersion,
        List<StatementTrace> statementTraces
) {
    public PolicyTrace {
        statementTraces = Collections.unmodifiableList(statementTraces);
    }
}
