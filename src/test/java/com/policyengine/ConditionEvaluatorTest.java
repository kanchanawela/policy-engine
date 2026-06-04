package com.policyengine;

import com.policyengine.condition.*;
import com.policyengine.core.EvaluationContext;
import com.policyengine.core.EvaluationRequest;
import com.policyengine.time.SystemTimeProvider;
import com.policyengine.time.TimeConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator(new TimeConditionEvaluator(new SystemTimeProvider()));
    }

    private EvaluationContext ctx(Map<String, String> context) {
        EvaluationRequest req = new EvaluationRequest("user:test", "resource:x", "read", context);
        return new EvaluationContext(req, new SystemTimeProvider().now());
    }

    private ConditionResult eval(String key, Operator op, List<String> values,
                                  Map<String, String> context) {
        return evaluator.evaluate(key, op, values, ctx(context));
    }

    // --- EQUALS / NOT_EQUALS ---

    @Test
    void equalsTrue() {
        assertTrue(eval("user.department", Operator.EQUALS,
                List.of("finance"), Map.of("user.department", "finance")).matched());
    }

    @Test
    void equalsIsCaseSensitive() {
        assertFalse(eval("user.department", Operator.EQUALS,
                List.of("Finance"), Map.of("user.department", "finance")).matched());
    }

    @Test
    void notEquals() {
        assertTrue(eval("user.role", Operator.NOT_EQUALS,
                List.of("guest"), Map.of("user.role", "admin")).matched());
        assertFalse(eval("user.role", Operator.NOT_EQUALS,
                List.of("admin"), Map.of("user.role", "admin")).matched());
    }

    // --- IN / NOT_IN ---

    @Test
    void inMatch() {
        assertTrue(eval("request.ipRegion", Operator.IN,
                List.of("US", "GB", "DE"), Map.of("request.ipRegion", "GB")).matched());
    }

    @Test
    void inNoMatch() {
        assertFalse(eval("request.ipRegion", Operator.IN,
                List.of("US", "GB", "DE"), Map.of("request.ipRegion", "CN")).matched());
    }

    @Test
    void notIn() {
        assertTrue(eval("request.ipRegion", Operator.NOT_IN,
                List.of("CN", "RU", "KP"), Map.of("request.ipRegion", "US")).matched());
        assertFalse(eval("request.ipRegion", Operator.NOT_IN,
                List.of("CN", "RU", "KP"), Map.of("request.ipRegion", "CN")).matched());
    }

    // --- STARTS_WITH / ENDS_WITH / CONTAINS ---

    @Test
    void startsWith() {
        assertTrue(eval("resource.path", Operator.STARTS_WITH,
                List.of("/data/reports"), Map.of("resource.path", "/data/reports/q1")).matched());
        assertFalse(eval("resource.path", Operator.STARTS_WITH,
                List.of("/data/reports"), Map.of("resource.path", "/other/path")).matched());
    }

    @Test
    void endsWith() {
        assertTrue(eval("file.name", Operator.ENDS_WITH,
                List.of(".pdf"), Map.of("file.name", "report.pdf")).matched());
    }

    @Test
    void contains() {
        assertTrue(eval("user.email", Operator.CONTAINS,
                List.of("@company.com"), Map.of("user.email", "alice@company.com")).matched());
    }

    // --- REGEX ---

    @Test
    void regexMatch() {
        assertTrue(eval("user.email", Operator.REGEX,
                List.of(".*@company\\.com$"), Map.of("user.email", "alice@company.com")).matched());
        assertFalse(eval("user.email", Operator.REGEX,
                List.of(".*@company\\.com$"), Map.of("user.email", "alice@other.org")).matched());
    }

    // --- NUMERIC COMPARISONS ---

    @Test
    void numericGtLt() {
        assertTrue(eval("user.clearanceLevel", Operator.GT,
                List.of("2"), Map.of("user.clearanceLevel", "3")).matched());
        assertFalse(eval("user.clearanceLevel", Operator.GT,
                List.of("5"), Map.of("user.clearanceLevel", "3")).matched());
        assertTrue(eval("user.clearanceLevel", Operator.LT,
                List.of("5"), Map.of("user.clearanceLevel", "3")).matched());
        assertTrue(eval("user.clearanceLevel", Operator.GTE,
                List.of("3"), Map.of("user.clearanceLevel", "3")).matched());
        assertTrue(eval("user.clearanceLevel", Operator.LTE,
                List.of("3"), Map.of("user.clearanceLevel", "3")).matched());
    }

    @Test
    void betweenInclusive() {
        assertTrue(eval("user.clearanceLevel", Operator.BETWEEN,
                List.of("2", "4"), Map.of("user.clearanceLevel", "3")).matched());
        assertTrue(eval("user.clearanceLevel", Operator.BETWEEN,
                List.of("2", "4"), Map.of("user.clearanceLevel", "2")).matched());  // inclusive low
        assertTrue(eval("user.clearanceLevel", Operator.BETWEEN,
                List.of("2", "4"), Map.of("user.clearanceLevel", "4")).matched());  // inclusive high
        assertFalse(eval("user.clearanceLevel", Operator.BETWEEN,
                List.of("2", "4"), Map.of("user.clearanceLevel", "5")).matched());
    }

    @Test
    void betweenReversedRangeAlwaysFalse() {
        assertFalse(eval("x", Operator.BETWEEN,
                List.of("10", "1"), Map.of("x", "5")).matched());
    }

    // --- EXISTS / NOT_EXISTS ---

    @Test
    void existsTrue() {
        assertTrue(eval("user.mfaVerified", Operator.EXISTS,
                List.of(), Map.of("user.mfaVerified", "true")).matched());
    }

    @Test
    void existsFalse() {
        assertFalse(eval("user.mfaVerified", Operator.EXISTS,
                List.of(), Map.of()).matched());
    }

    @Test
    void notExists() {
        assertTrue(eval("user.mfaVerified", Operator.NOT_EXISTS,
                List.of(), Map.of()).matched());
        assertFalse(eval("user.mfaVerified", Operator.NOT_EXISTS,
                List.of(), Map.of("user.mfaVerified", "true")).matched());
    }

    // --- Missing key ---

    @Test
    void missingKeyReturnsFalse() {
        assertFalse(eval("nonexistent.key", Operator.EQUALS,
                List.of("value"), Map.of()).matched());
    }

    // --- Composite conditions ---

    @Test
    void andConditionAllTrue() {
        EvaluationContext ctx = ctx(Map.of("a", "1", "b", "2"));
        Condition and = new AndCondition(List.of(
                new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
        ));
        assertTrue(and.evaluate(ctx).matched());
    }

    @Test
    void andConditionOneFalse() {
        EvaluationContext ctx = ctx(Map.of("a", "1", "b", "wrong"));
        Condition and = new AndCondition(List.of(
                new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
        ));
        assertFalse(and.evaluate(ctx).matched());
    }

    @Test
    void orConditionOneTrue() {
        EvaluationContext ctx = ctx(Map.of("a", "wrong", "b", "2"));
        Condition or = new OrCondition(List.of(
                new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
        ));
        assertTrue(or.evaluate(ctx).matched());
    }

    @Test
    void orConditionAllFalse() {
        EvaluationContext ctx = ctx(Map.of("a", "x", "b", "y"));
        Condition or = new OrCondition(List.of(
                new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
        ));
        assertFalse(or.evaluate(ctx).matched());
    }

    @Test
    void notCondition() {
        EvaluationContext ctx = ctx(Map.of("a", "1"));
        Condition not = new NotCondition(new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator));
        assertFalse(not.evaluate(ctx).matched());

        Condition not2 = new NotCondition(new LeafCondition("a", Operator.EQUALS, List.of("2"), evaluator));
        assertTrue(not2.evaluate(ctx).matched());
    }

    @Test
    void deeplyNestedCondition() {
        // AND( OR(a=1, b=2), NOT(OR(c=3, d=4)) )
        EvaluationContext ctx = ctx(Map.of("a", "1", "c", "99", "d", "99")); // a=1 (OR true), c,d != 3,4 (NOT OR true)
        Condition cond = new AndCondition(List.of(
                new OrCondition(List.of(
                        new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                        new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
                )),
                new NotCondition(new OrCondition(List.of(
                        new LeafCondition("c", Operator.EQUALS, List.of("3"), evaluator),
                        new LeafCondition("d", Operator.EQUALS, List.of("4"), evaluator)
                )))
        ));
        assertTrue(cond.evaluate(ctx).matched());
    }

    @Test
    void traceContainsFullTree() {
        EvaluationContext ctx = ctx(Map.of("a", "1", "b", "x"));
        Condition and = new AndCondition(List.of(
                new LeafCondition("a", Operator.EQUALS, List.of("1"), evaluator),
                new LeafCondition("b", Operator.EQUALS, List.of("2"), evaluator)
        ));
        ConditionTrace trace = and.trace(ctx);
        assertEquals("AND", trace.nodeType());
        assertFalse(trace.result());
        assertEquals(2, trace.children().size());
        assertTrue(trace.children().get(0).result());   // a=1 ✓
        assertFalse(trace.children().get(1).result());  // b=x ≠ 2
    }
}
