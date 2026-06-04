package com.policyengine.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.policyengine.condition.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursively parses a raw Jackson {@link JsonNode} into a typed {@link Condition} tree.
 * Called by {@link PolicyCompiler} at compile time — never on the evaluation hot path.
 */
public class ConditionParser {

    private final ConditionEvaluator evaluator;

    public ConditionParser(ConditionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Parses a condition node. Returns {@link AlwaysTrueCondition} for null/missing nodes.
     */
    public Condition parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return new AlwaysTrueCondition();
        }

        // Composite: has "operator" field
        if (node.has("operator")) {
            String op = node.get("operator").asText().toUpperCase().trim();
            return switch (op) {
                case "AND" -> new AndCondition(parseChildren(node));
                case "OR"  -> new OrCondition(parseChildren(node));
                case "NOT" -> {
                    JsonNode child = node.has("condition")
                            ? node.get("condition")
                            : (node.has("conditions") && node.get("conditions").isArray()
                               ? node.get("conditions").get(0)
                               : node.get("conditions"));
                    yield new NotCondition(parse(child));
                }
                default -> throw new PolicyParseException(
                        "Unknown composite operator: '" + op + "'. Expected AND, OR, or NOT");
            };
        }

        // Leaf: has "key" and "op" fields
        if (node.has("key") && node.has("op")) {
            String key = node.get("key").asText();
            String opStr = node.get("op").asText().toUpperCase().trim();
            Operator operator;
            try {
                operator = Operator.valueOf(opStr);
            } catch (IllegalArgumentException e) {
                throw new PolicyParseException(
                        "Unknown operator '" + opStr + "' for key '" + key + "'");
            }

            List<String> values = new ArrayList<>();
            if (node.has("values") && node.get("values").isArray()) {
                for (JsonNode v : node.get("values")) {
                    values.add(v.asText());
                }
            } else if (node.has("value")) {
                values.add(node.get("value").asText());
            }

            return new LeafCondition(key, operator, values, evaluator);
        }

        throw new PolicyParseException(
                "Invalid condition node — must have either 'operator' (composite) or 'key'+'op' (leaf): "
                + node.toString().substring(0, Math.min(node.toString().length(), 200)));
    }

    private List<Condition> parseChildren(JsonNode composite) {
        JsonNode conditionsNode = composite.get("conditions");
        List<Condition> children = new ArrayList<>();
        if (conditionsNode != null && conditionsNode.isArray()) {
            for (JsonNode child : conditionsNode) {
                children.add(parse(child));
            }
        }
        if (children.isEmpty()) {
            throw new PolicyParseException(
                    "Composite condition '" + composite.get("operator").asText()
                    + "' must have at least one child in 'conditions'");
        }
        return children;
    }
}
