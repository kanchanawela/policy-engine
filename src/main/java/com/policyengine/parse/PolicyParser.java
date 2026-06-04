package com.policyengine.parse;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.policyengine.model.Policy;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses JSON into {@link Policy} objects using Jackson.
 * The {@code conditions} field in each statement is kept as a raw {@link com.fasterxml.jackson.databind.JsonNode};
 * actual Condition tree construction happens in {@link PolicyCompiler}.
 */
public class PolicyParser {

    private final ObjectMapper mapper;

    public PolicyParser() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public Policy parse(String json) {
        try {
            Policy policy = mapper.readValue(json, Policy.class);
            validate(policy);
            return policy;
        } catch (IOException e) {
            throw new PolicyParseException("Failed to parse policy JSON: " + e.getMessage(), e);
        }
    }

    public Policy parse(InputStream stream) {
        try {
            Policy policy = mapper.readValue(stream, Policy.class);
            validate(policy);
            return policy;
        } catch (IOException e) {
            throw new PolicyParseException("Failed to parse policy from stream: " + e.getMessage(), e);
        }
    }

    private void validate(Policy policy) {
        if (policy.getId() == null || policy.getId().isBlank()) {
            throw new PolicyParseException("Policy must have a non-blank 'id' field");
        }
    }
}
