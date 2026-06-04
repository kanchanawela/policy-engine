package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;
import com.policyengine.time.TimeConditionEvaluator;
import com.policyengine.wildcard.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateless (thread-safe) evaluator for leaf conditions.
 * Routes time.* keys to {@link TimeConditionEvaluator}; handles all other operators internally.
 */
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private final TimeConditionEvaluator timeEvaluator;
    private final ConcurrentHashMap<String, Pattern> regexCache = new ConcurrentHashMap<>();

    public ConditionEvaluator(TimeConditionEvaluator timeEvaluator) {
        this.timeEvaluator = timeEvaluator;
    }

    public ConditionResult evaluate(String key, Operator op, List<String> values,
                                    EvaluationContext ctx) {
        if (key.startsWith("time.")) {
            return timeEvaluator.evaluate(key, op, values, ctx.evaluationTime());
        }

        // EXISTS / NOT_EXISTS do not need the context value
        if (op == Operator.EXISTS) {
            boolean has = ctx.hasKey(key);
            return ConditionResult.of(has, key + " EXISTS -> " + has);
        }
        if (op == Operator.NOT_EXISTS) {
            boolean missing = !ctx.hasKey(key);
            return ConditionResult.of(missing, key + " NOT_EXISTS -> " + missing);
        }

        String contextValue = ctx.get(key);
        if (contextValue == null) {
            return ConditionResult.deny("key '" + key + "' not found in context -> false");
        }

        String first = values.isEmpty() ? "" : values.get(0);

        return switch (op) {
            case EQUALS -> {
                boolean m = contextValue.equals(first);
                yield ConditionResult.of(m, key + "=" + contextValue + " EQUALS " + first + " -> " + m);
            }
            case NOT_EQUALS -> {
                boolean m = !contextValue.equals(first);
                yield ConditionResult.of(m, key + "=" + contextValue + " NOT_EQUALS " + first + " -> " + m);
            }
            case IN -> {
                boolean m = values.contains(contextValue);
                yield ConditionResult.of(m, key + "=" + contextValue + " IN " + values + " -> " + m);
            }
            case NOT_IN -> {
                boolean m = !values.contains(contextValue);
                yield ConditionResult.of(m, key + "=" + contextValue + " NOT_IN " + values + " -> " + m);
            }
            case STARTS_WITH -> {
                boolean m = contextValue.startsWith(first);
                yield ConditionResult.of(m, key + "=" + contextValue + " STARTS_WITH " + first + " -> " + m);
            }
            case ENDS_WITH -> {
                boolean m = contextValue.endsWith(first);
                yield ConditionResult.of(m, key + "=" + contextValue + " ENDS_WITH " + first + " -> " + m);
            }
            case CONTAINS -> {
                boolean m = contextValue.contains(first);
                yield ConditionResult.of(m, key + "=" + contextValue + " CONTAINS " + first + " -> " + m);
            }
            case REGEX -> {
                Pattern pattern = compileRegex(first);
                boolean m = pattern.matcher(contextValue).matches();
                yield ConditionResult.of(m, key + "=" + contextValue + " REGEX " + first + " -> " + m);
            }
            case GT, GTE, LT, LTE -> {
                int cmp = compareValues(contextValue, first);
                boolean m = switch (op) {
                    case GT  -> cmp > 0;
                    case GTE -> cmp >= 0;
                    case LT  -> cmp < 0;
                    case LTE -> cmp <= 0;
                    default  -> false;
                };
                yield ConditionResult.of(m, key + "=" + contextValue + " " + op + " " + first + " -> " + m);
            }
            case BETWEEN -> {
                if (values.size() < 2) {
                    yield ConditionResult.deny("BETWEEN requires 2 values for key " + key);
                }
                String lo = values.get(0), hi = values.get(1);
                if (compareValues(lo, hi) > 0) {
                    log.warn("BETWEEN range reversed for key {}: [{}, {}]; always false", key, lo, hi);
                    yield ConditionResult.deny(key + " BETWEEN reversed range -> false");
                }
                boolean m = compareValues(contextValue, lo) >= 0 && compareValues(contextValue, hi) <= 0;
                yield ConditionResult.of(m, key + "=" + contextValue + " BETWEEN [" + lo + "," + hi + "] -> " + m);
            }
            default -> ConditionResult.deny("Unhandled operator: " + op);
        };
    }

    private Pattern compileRegex(String pattern) {
        return regexCache.computeIfAbsent(pattern, p -> {
            try {
                return Pattern.compile(p);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + p, e);
            }
        });
    }

    /**
     * Compares two string values: tries numeric first, falls back to lexicographic.
     */
    static int compareValues(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }
}
