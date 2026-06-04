package com.policyengine;

import com.policyengine.condition.ConditionResult;
import com.policyengine.condition.Operator;
import com.policyengine.time.TimeConditionEvaluator;
import com.policyengine.time.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeConditionTest {

    private TimeConditionEvaluator evaluatorAt(ZonedDateTime time) {
        TimeProvider fixed = () -> time;
        return new TimeConditionEvaluator(fixed);
    }

    private static final ZonedDateTime MON_10AM =
            ZonedDateTime.of(2024, 3, 4, 10, 30, 0, 0, ZoneId.of("UTC")); // Monday

    private static final ZonedDateTime SAT_10AM =
            ZonedDateTime.of(2024, 3, 9, 10, 30, 0, 0, ZoneId.of("UTC")); // Saturday

    @Test
    void hourBetweenMatch() {
        TimeConditionEvaluator eval = evaluatorAt(MON_10AM);
        ConditionResult result = eval.evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), MON_10AM);
        assertTrue(result.matched());
    }

    @Test
    void hourBetweenInclusive() {
        TimeConditionEvaluator eval = evaluatorAt(MON_10AM.withHour(8));
        assertTrue(eval.evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), MON_10AM.withHour(8)).matched());
        assertTrue(eval.evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), MON_10AM.withHour(18)).matched());
    }

    @Test
    void hourBeforeBusiness() {
        ZonedDateTime before = MON_10AM.withHour(7);
        assertFalse(evaluatorAt(before).evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), before).matched());
    }

    @Test
    void dayOfWeekWeekdaysTrue() {
        // MON_10AM is a Monday
        assertTrue(evaluatorAt(MON_10AM).evaluate("time.dayOfWeek", Operator.IN,
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), MON_10AM).matched());
    }

    @Test
    void dayOfWeekSaturdayFalse() {
        assertFalse(evaluatorAt(SAT_10AM).evaluate("time.dayOfWeek", Operator.IN,
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), SAT_10AM).matched());
    }

    @Test
    void combinedTimeAndDayViaAnd() {
        // MON 10:30 — should pass business-hours AND weekday check
        assertTrue(evaluatorAt(MON_10AM).evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), MON_10AM).matched());
        assertTrue(evaluatorAt(MON_10AM).evaluate("time.dayOfWeek", Operator.IN,
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), MON_10AM).matched());

        // SAT 10:30 — fails weekday
        assertTrue(evaluatorAt(SAT_10AM).evaluate("time.hour", Operator.BETWEEN,
                List.of("8", "18"), SAT_10AM).matched());
        assertFalse(evaluatorAt(SAT_10AM).evaluate("time.dayOfWeek", Operator.IN,
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), SAT_10AM).matched());
    }

    @Test
    void dateBetweenMatch() {
        ZonedDateTime mid2024 = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneId.of("UTC"));
        assertTrue(evaluatorAt(mid2024).evaluate("time.date", Operator.BETWEEN,
                List.of("2024-01-01", "2024-12-31"), mid2024).matched());
    }

    @Test
    void dateOutsideRange() {
        ZonedDateTime jan2025 = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        assertFalse(evaluatorAt(jan2025).evaluate("time.date", Operator.BETWEEN,
                List.of("2024-01-01", "2024-12-31"), jan2025).matched());
    }

    @Test
    void monthIn() {
        ZonedDateTime nov = ZonedDateTime.of(2024, 11, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
        assertTrue(evaluatorAt(nov).evaluate("time.month", Operator.IN,
                List.of("NOVEMBER", "DECEMBER"), nov).matched());
        ZonedDateTime jan = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
        assertFalse(evaluatorAt(jan).evaluate("time.month", Operator.IN,
                List.of("NOVEMBER", "DECEMBER"), jan).matched());
    }

    @Test
    void reversedHourBetweenAlwaysFalse() {
        ZonedDateTime noon = MON_10AM.withHour(12);
        ConditionResult result = evaluatorAt(noon).evaluate("time.hour", Operator.BETWEEN,
                List.of("18", "8"), noon);  // reversed
        assertFalse(result.matched());
    }

    @Test
    void caseInsensitiveDayOfWeek() {
        assertTrue(evaluatorAt(MON_10AM).evaluate("time.dayOfWeek", Operator.EQUALS,
                List.of("monday"), MON_10AM).matched());
        assertTrue(evaluatorAt(MON_10AM).evaluate("time.dayOfWeek", Operator.EQUALS,
                List.of("Monday"), MON_10AM).matched());
    }
}
