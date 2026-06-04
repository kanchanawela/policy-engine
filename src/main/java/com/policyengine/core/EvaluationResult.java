package com.policyengine.core;

import com.policyengine.audit.AuditLog;
import com.policyengine.model.Effect;

/** Immutable output of a policy evaluation. */
public record EvaluationResult(Effect decision, AuditLog auditLog) {

    public boolean isAllowed() { return decision == Effect.ALLOW; }
    public boolean isDenied()  { return decision == Effect.DENY;  }
}
