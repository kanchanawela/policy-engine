package com.policyengine.specificity;

import com.policyengine.cache.CompiledStatement;
import com.policyengine.core.EvaluationRequest;

public interface SpecificityScorer {
    /**
     * Returns a non-negative integer representing how specifically this statement
     * matches the given request. Higher values win in MostSpecificWins strategy.
     */
    int score(CompiledStatement statement, EvaluationRequest request);
}
