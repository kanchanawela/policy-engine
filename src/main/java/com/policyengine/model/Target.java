package com.policyengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Target {

    private List<String> principals = List.of("*");
    private List<String> resources  = List.of("*");
    private List<String> actions    = List.of("*");

    public List<String> getPrincipals() { return principals; }
    public void setPrincipals(List<String> principals) { this.principals = principals; }

    public List<String> getResources() { return resources; }
    public void setResources(List<String> resources) { this.resources = resources; }

    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
}
