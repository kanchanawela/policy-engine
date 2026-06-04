package com.policyengine.wildcard;

import java.util.regex.Pattern;

public final class WildcardMatcher {

    private WildcardMatcher() {}

    /**
     * Converts a glob pattern (supporting * and ?) into a compiled regex Pattern.
     * All regex metacharacters other than * and ? are escaped.
     */
    public static Pattern toPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> {
                    sb.append('\\');
                    sb.append(c);
                }
                default -> sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    public static boolean matches(Pattern pattern, String input) {
        if (input == null) return false;
        return pattern.matcher(input).matches();
    }

    /** Computes a specificity score for a glob pattern. Higher means more specific. */
    public static int specificityScore(String glob) {
        if ("*".equals(glob)) return 0;
        if (!glob.contains("*") && !glob.contains("?")) return 100;
        // prefix glob: only a trailing * with no other wildcards
        if (glob.endsWith("*") && !glob.substring(0, glob.length() - 1).contains("*")
                && !glob.substring(0, glob.length() - 1).contains("?")) return 60;
        if (glob.contains("*")) return 50;
        return 40; // only ? wildcards
    }
}
