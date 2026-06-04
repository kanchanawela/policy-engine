package com.policyengine.cache;

import com.policyengine.wildcard.WildcardMatcher;

import java.util.regex.Pattern;

/**
 * A pre-compiled glob pattern. Stores the original glob string (for audit display)
 * alongside the compiled regex Pattern (for fast matching).
 */
public record CompiledPattern(String glob, Pattern pattern) {

    public static CompiledPattern of(String glob) {
        return new CompiledPattern(glob, WildcardMatcher.toPattern(glob));
    }

    public boolean matches(String value) {
        return WildcardMatcher.matches(pattern, value);
    }
}
