package com.policyengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyStatement {

    private String id;
    private Effect effect = Effect.DENY;
    private Target target = new Target();
    /** Raw JSON node; compiled into a typed Condition tree by PolicyCompiler. */
    private JsonNode conditions;

    public String getId()                   { return id; }
    public void setId(String id)            { this.id = id; }

    public Effect getEffect()               { return effect; }
    public void setEffect(Effect effect)    { this.effect = effect; }

    public Target getTarget()               { return target; }
    public void setTarget(Target target)    { this.target = target; }

    public JsonNode getConditions()                  { return conditions; }
    public void setConditions(JsonNode conditions)   { this.conditions = conditions; }
}
