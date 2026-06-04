package com.policyengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Policy {

    private String id;
    private String version     = "1.0";
    private String description = "";
    private int    priority    = 0;
    private List<PolicyStatement> statements = List.of();

    public String getId()                                { return id; }
    public void setId(String id)                         { this.id = id; }

    public String getVersion()                           { return version; }
    public void setVersion(String version)               { this.version = version; }

    public String getDescription()                       { return description; }
    public void setDescription(String description)       { this.description = description; }

    public int getPriority()                             { return priority; }
    public void setPriority(int priority)                { this.priority = priority; }

    public List<PolicyStatement> getStatements()                         { return statements; }
    public void setStatements(List<PolicyStatement> statements)          { this.statements = statements; }
}
