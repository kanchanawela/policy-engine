package com.policyengine.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serializes {@link AuditLog} to JSON and to a compact summary line suitable for logging.
 */
public final class AuditSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AuditSerializer() {}

    /** Returns the full audit log as compact JSON. */
    public static String toJson(AuditLog log) {
        try {
            return MAPPER.writeValueAsString(log);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AuditLog", e);
        }
    }

    /** Returns the full audit log as pretty-printed JSON. */
    public static String toPrettyJson(AuditLog log) {
        try {
            return PRETTY_MAPPER.writeValueAsString(log);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AuditLog", e);
        }
    }

    /**
     * Returns a single-line summary for SLF4J / structured logging.
     * Example:
     *   DECISION=DENY req=abc-123 strategy=DenyOverride principal=user:alice
     *   resource=arn:data:reports/q1 action=read deciding_stmt=stmt-002 duration_ms=4
     */
    public static String toSummaryLine(AuditLog log) {
        return "DECISION=" + log.finalDecision()
                + " req=" + log.requestId()
                + " strategy=" + log.strategyUsed()
                + " principal=" + log.request().principal()
                + " resource=" + log.request().resource()
                + " action=" + log.request().action()
                + " deciding_stmt=" + (log.decidingStatementId() != null
                        ? log.decidingStatementId() : "IMPLICIT_DENY")
                + " duration_ms=" + log.evaluationDuration().toMillis();
    }
}
