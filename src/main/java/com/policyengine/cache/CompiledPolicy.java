package com.policyengine.cache;

import com.policyengine.model.Policy;

import java.util.Collections;
import java.util.List;

/**
 * Pre-compiled form of an entire policy. Immutable — safely shared across threads.
 */
public record CompiledPolicy(
        Policy original,
        List<CompiledStatement> statements
) {
    public CompiledPolicy {
        statements = Collections.unmodifiableList(statements);
    }
}
