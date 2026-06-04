package com.policyengine.audit;

/**
 * Records which glob pattern from each target field matched the request.
 */
public record TargetMatchDetail(
        String matchedPrincipalPattern,
        String matchedResourcePattern,
        String matchedActionPattern
) {}
