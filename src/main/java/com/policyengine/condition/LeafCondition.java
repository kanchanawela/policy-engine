package com.policyengine.condition;

import com.policyengine.core.EvaluationContext;

import java.util.List;

/** A terminal condition node that evaluates a single key-operator-values predicate. */
public class LeafCondition implements Condition {

    private final String key;
    private final Operator operator;
    private final List<String> values;
    private final ConditionEvaluator evaluator;

    public LeafCondition(String key, Operator operator, List<String> values,
                         ConditionEvaluator evaluator) {
        this.key = key;
        this.operator = operator;
        this.values = List.copyOf(values);
        this.evaluator = evaluator;
    }

    @Override
    public ConditionResult evaluate(EvaluationContext ctx) {
        return evaluator.evaluate(key, operator, values, ctx);
    }

    @Override
    public ConditionTrace trace(EvaluationContext ctx) {
        ConditionResult result = evaluate(ctx);
        boolean isTime = key.startsWith("time.");
        return isTime
                ? ConditionTrace.timeleaf(result.matched(), result.reason())
                : ConditionTrace.leaf(result.matched(), result.reason());
    }

    public String getKey()          { return key; }
    public Operator getOperator()   { return operator; }
    public List<String> getValues() { return values; }
}
