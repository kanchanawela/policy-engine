package com.policyengine.cache;

import com.policyengine.condition.Condition;
import com.policyengine.model.PolicyStatement;

import java.util.Collections;
import java.util.List;

/**
 * Pre-compiled form of a single policy statement.
 * All glob patterns are compiled to regex; the condition JSON is parsed into a typed AST.
 * Immutable — safely shared across threads.
 */
public record CompiledStatement(
        PolicyStatement original,
        List<CompiledPattern> principalPatterns,
        List<CompiledPattern> resourcePatterns,
        List<CompiledPattern> actionPatterns,
        Condition conditionTree,
        int baseSpecificityScore,
        int statementIndex
) {
    public CompiledStatement {
        principalPatterns = Collections.unmodifiableList(principalPatterns);
        resourcePatterns  = Collections.unmodifiableList(resourcePatterns);
        actionPatterns    = Collections.unmodifiableList(actionPatterns);
    }
}
