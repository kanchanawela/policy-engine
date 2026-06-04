package com.policyengine.time;

import com.policyengine.condition.ConditionResult;
import com.policyengine.condition.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Evaluates leaf conditions whose key starts with "time.".
 * Reads the current time from the injected {@link TimeProvider} — fully testable.
 */
public class TimeConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TimeConditionEvaluator.class);

    private final TimeProvider timeProvider;

    public TimeConditionEvaluator(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public ConditionResult evaluate(String key, Operator op, List<String> values,
                                    ZonedDateTime now) {
        String suffix = key.substring("time.".length());
        return switch (suffix) {
            case "hour"       -> evaluateInt(now.getHour(), op, values, key);
            case "minute"     -> evaluateInt(now.getMinute(), op, values, key);
            case "dayOfWeek"  -> evaluateString(now.getDayOfWeek().name(), op, values, key, true);
            case "dayOfMonth" -> evaluateInt(now.getDayOfMonth(), op, values, key);
            case "month"      -> evaluateString(now.getMonth().name(), op, values, key, true);
            case "year"       -> evaluateInt(now.getYear(), op, values, key);
            case "date"       -> evaluateDate(now.toLocalDate(), op, values, key);
            case "datetime"   -> evaluateDateTime(now, op, values, key);
            case "timezone"   -> evaluateString(now.getZone().getId(), op, values, key, false);
            default -> ConditionResult.deny("Unknown time key: " + key);
        };
    }

    private ConditionResult evaluateInt(int actual, Operator op, List<String> values, String key) {
        return switch (op) {
            case EQUALS -> {
                int v = parseInt(values.get(0));
                boolean match = actual == v;
                yield result(match, key + "=" + actual + " EQUALS " + v);
            }
            case NOT_EQUALS -> {
                int v = parseInt(values.get(0));
                boolean match = actual != v;
                yield result(match, key + "=" + actual + " NOT_EQUALS " + v);
            }
            case GT  -> { int v = parseInt(values.get(0)); yield result(actual > v,  key + "=" + actual + " GT " + v); }
            case GTE -> { int v = parseInt(values.get(0)); yield result(actual >= v, key + "=" + actual + " GTE " + v); }
            case LT  -> { int v = parseInt(values.get(0)); yield result(actual < v,  key + "=" + actual + " LT " + v); }
            case LTE -> { int v = parseInt(values.get(0)); yield result(actual <= v, key + "=" + actual + " LTE " + v); }
            case BETWEEN -> {
                if (values.size() < 2) yield ConditionResult.deny("BETWEEN requires 2 values for " + key);
                int lo = parseInt(values.get(0));
                int hi = parseInt(values.get(1));
                if (lo > hi) {
                    log.warn("BETWEEN range reversed for key {}: [{}, {}]; always false", key, lo, hi);
                    yield ConditionResult.deny(key + " BETWEEN reversed range [" + lo + "," + hi + "] -> false");
                }
                boolean match = actual >= lo && actual <= hi;
                yield result(match, key + "=" + actual + " BETWEEN [" + lo + "," + hi + "]");
            }
            case IN -> {
                boolean match = values.stream().mapToInt(Integer::parseInt).anyMatch(v -> v == actual);
                yield result(match, key + "=" + actual + " IN " + values);
            }
            case NOT_IN -> {
                boolean match = values.stream().mapToInt(Integer::parseInt).noneMatch(v -> v == actual);
                yield result(match, key + "=" + actual + " NOT_IN " + values);
            }
            default -> ConditionResult.deny("Operator " + op + " not supported for numeric time key " + key);
        };
    }

    private ConditionResult evaluateString(String actual, Operator op, List<String> values,
                                           String key, boolean caseInsensitive) {
        return switch (op) {
            case EQUALS -> {
                boolean match = caseInsensitive
                        ? actual.equalsIgnoreCase(values.get(0))
                        : actual.equals(values.get(0));
                yield result(match, key + "=" + actual + " EQUALS " + values.get(0));
            }
            case NOT_EQUALS -> {
                boolean match = caseInsensitive
                        ? !actual.equalsIgnoreCase(values.get(0))
                        : !actual.equals(values.get(0));
                yield result(match, key + "=" + actual + " NOT_EQUALS " + values.get(0));
            }
            case IN -> {
                boolean match = values.stream()
                        .anyMatch(v -> caseInsensitive ? v.equalsIgnoreCase(actual) : v.equals(actual));
                yield result(match, key + "=" + actual + " IN " + values);
            }
            case NOT_IN -> {
                boolean match = values.stream()
                        .noneMatch(v -> caseInsensitive ? v.equalsIgnoreCase(actual) : v.equals(actual));
                yield result(match, key + "=" + actual + " NOT_IN " + values);
            }
            default -> ConditionResult.deny("Operator " + op + " not supported for string time key " + key);
        };
    }

    private ConditionResult evaluateDate(LocalDate actual, Operator op, List<String> values, String key) {
        return switch (op) {
            case EQUALS  -> result(actual.equals(parseDate(values.get(0))), key + "=" + actual + " EQUALS " + values.get(0));
            case GT      -> result(actual.isAfter(parseDate(values.get(0))),              key + "=" + actual + " GT " + values.get(0));
            case GTE     -> result(!actual.isBefore(parseDate(values.get(0))),            key + "=" + actual + " GTE " + values.get(0));
            case LT      -> result(actual.isBefore(parseDate(values.get(0))),             key + "=" + actual + " LT " + values.get(0));
            case LTE     -> result(!actual.isAfter(parseDate(values.get(0))),             key + "=" + actual + " LTE " + values.get(0));
            case BETWEEN -> {
                if (values.size() < 2) yield ConditionResult.deny("BETWEEN requires 2 values for " + key);
                LocalDate lo = parseDate(values.get(0));
                LocalDate hi = parseDate(values.get(1));
                if (lo.isAfter(hi)) {
                    log.warn("BETWEEN date range reversed for key {}", key);
                    yield ConditionResult.deny(key + " BETWEEN reversed range -> false");
                }
                boolean match = !actual.isBefore(lo) && !actual.isAfter(hi);
                yield result(match, key + "=" + actual + " BETWEEN [" + lo + "," + hi + "]");
            }
            default -> ConditionResult.deny("Operator " + op + " not supported for date key " + key);
        };
    }

    private ConditionResult evaluateDateTime(ZonedDateTime actual, Operator op,
                                             List<String> values, String key) {
        return switch (op) {
            case EQUALS  -> result(actual.isEqual(parseDateTime(values.get(0))),           key + " EQUALS " + values.get(0));
            case GT      -> result(actual.isAfter(parseDateTime(values.get(0))),           key + " GT " + values.get(0));
            case GTE     -> result(!actual.isBefore(parseDateTime(values.get(0))),         key + " GTE " + values.get(0));
            case LT      -> result(actual.isBefore(parseDateTime(values.get(0))),          key + " LT " + values.get(0));
            case LTE     -> result(!actual.isAfter(parseDateTime(values.get(0))),          key + " LTE " + values.get(0));
            case BETWEEN -> {
                if (values.size() < 2) yield ConditionResult.deny("BETWEEN requires 2 values for " + key);
                ZonedDateTime lo = parseDateTime(values.get(0));
                ZonedDateTime hi = parseDateTime(values.get(1));
                boolean match = !actual.isBefore(lo) && !actual.isAfter(hi);
                yield result(match, key + " BETWEEN [" + lo + "," + hi + "]");
            }
            default -> ConditionResult.deny("Operator " + op + " not supported for datetime key " + key);
        };
    }

    private static ConditionResult result(boolean matched, String description) {
        return ConditionResult.of(matched, description + " -> " + matched);
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s.trim());
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date value '" + s + "'; expected ISO-8601 (yyyy-MM-dd)", e);
        }
    }

    private static ZonedDateTime parseDateTime(String s) {
        try {
            return ZonedDateTime.parse(s.trim());
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s.trim()).atZone(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid datetime value '" + s + "'", e);
            }
        }
    }
}
