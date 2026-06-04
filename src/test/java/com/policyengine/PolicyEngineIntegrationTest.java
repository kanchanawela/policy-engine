package com.policyengine;

import com.policyengine.audit.AuditLog;
import com.policyengine.audit.AuditSerializer;
import com.policyengine.conflict.DenyOverrideStrategy;
import com.policyengine.conflict.FirstMatchStrategy;
import com.policyengine.conflict.MostSpecificWinsStrategy;
import com.policyengine.core.EngineConfig;
import com.policyengine.core.EvaluationRequest;
import com.policyengine.core.EvaluationResult;
import com.policyengine.core.PolicyEngine;
import com.policyengine.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineIntegrationTest {

    private PolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = EngineConfig.builder()
                .strategy(new DenyOverrideStrategy())
                .build()
                .buildEngine();
    }

    // =========================================================================
    // Basic Allow / Deny
    // =========================================================================

    @Test
    void basicAllow() {
        engine.loadPolicy("""
                {
                  "id": "p1", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s1", "effect": "Allow",
                    "target": {
                      "principals": ["user:alice"],
                      "resources":  ["resource:docs/*"],
                      "actions":    ["read"]
                    }
                  }]
                }
                """);

        EvaluationResult r = engine.evaluate(
                new EvaluationRequest("user:alice", "resource:docs/report.pdf", "read"));
        assertTrue(r.isAllowed());
        assertEquals("s1", r.auditLog().decidingStatementId());
    }

    @Test
    void wrongResourceImplicitDeny() {
        engine.loadPolicy("""
                {
                  "id": "p2", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s2", "effect": "Allow",
                    "target": {
                      "principals": ["user:alice"],
                      "resources":  ["resource:docs/*"],
                      "actions":    ["read"]
                    }
                  }]
                }
                """);

        EvaluationResult r = engine.evaluate(
                new EvaluationRequest("user:alice", "resource:other/file", "read"));
        assertTrue(r.isDenied());
        assertNull(r.auditLog().decidingStatementId());
        assertNotNull(r.auditLog().implicitDenyReason());
    }

    @Test
    void noPoliciesImplicitDeny() {
        EvaluationResult r = engine.evaluate(
                new EvaluationRequest("user:alice", "resource:x", "read"));
        assertTrue(r.isDenied());
    }

    // =========================================================================
    // Glob / Wildcard Target Matching
    // =========================================================================

    @Test
    void groupPrefixGlobMatchesSubgroups() {
        engine.loadPolicy("""
                {
                  "id": "p3", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s3", "effect": "Allow",
                    "target": {
                      "principals": ["group:finance-*"],
                      "resources":  ["*"],
                      "actions":    ["read"]
                    }
                  }]
                }
                """);

        assertTrue(engine.evaluate(
                new EvaluationRequest("group:finance-london", "resource:x", "read")).isAllowed());
        assertFalse(engine.evaluate(
                new EvaluationRequest("group:financelondon", "resource:x", "read")).isAllowed());
    }

    @Test
    void actionWildcard() {
        engine.loadPolicy("""
                {
                  "id": "p4", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s4", "effect": "Allow",
                    "target": {
                      "principals": ["*"],
                      "resources":  ["*"],
                      "actions":    ["export:*"]
                    }
                  }]
                }
                """);

        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "export:csv")).isAllowed());
        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "export:pdf")).isAllowed());
        assertFalse(engine.evaluate(new EvaluationRequest("u", "r", "delete")).isAllowed());
    }

    // =========================================================================
    // Conditions
    // =========================================================================

    @Test
    void conditionEqualsAllow() {
        engine.loadPolicy("""
                {
                  "id": "p5", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s5", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": { "key": "user.department", "op": "EQUALS", "value": "finance" }
                  }]
                }
                """);

        assertTrue(engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("user.department", "finance"))).isAllowed());
        assertFalse(engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("user.department", "ops"))).isAllowed());
    }

    @Test
    void compositeAndCondition() {
        engine.loadPolicy("""
                {
                  "id": "p6", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s6", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "operator": "AND",
                      "conditions": [
                        { "key": "user.department", "op": "EQUALS", "value": "finance" },
                        { "key": "user.clearanceLevel", "op": "GTE",   "value": "2" }
                      ]
                    }
                  }]
                }
                """);

        assertTrue(engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("user.department", "finance", "user.clearanceLevel", "3"))).isAllowed());
        assertFalse(engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("user.department", "finance", "user.clearanceLevel", "1"))).isAllowed());
        assertFalse(engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("user.department", "ops", "user.clearanceLevel", "3"))).isAllowed());
    }

    // =========================================================================
    // Time-based Conditions
    // =========================================================================

    @Test
    void timeBasedConditionBusinessHours() {
        ZonedDateTime mon10am = ZonedDateTime.of(2024, 3, 4, 10, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime sat10am = ZonedDateTime.of(2024, 3, 9, 10, 0, 0, 0, ZoneId.of("UTC"));

        PolicyEngine e = EngineConfig.builder()
                .timeProvider(() -> mon10am)
                .build()
                .buildEngine();

        e.loadPolicy("""
                {
                  "id": "pt", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "st", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "operator": "AND",
                      "conditions": [
                        { "key": "time.hour",      "op": "BETWEEN", "values": ["8","18"] },
                        { "key": "time.dayOfWeek", "op": "IN",
                          "values": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"] }
                      ]
                    }
                  }]
                }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isAllowed());

        // Saturday — same policy but clock at Saturday should deny
        String policyJson = """
                {
                  "id": "pt-sat", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "st-sat", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "operator": "AND",
                      "conditions": [
                        { "key": "time.hour",      "op": "BETWEEN", "values": ["8","18"] },
                        { "key": "time.dayOfWeek", "op": "IN",
                          "values": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"] }
                      ]
                    }
                  }]
                }
                """;
        PolicyEngine weekend = EngineConfig.builder()
                .timeProvider(() -> sat10am)
                .build()
                .buildEngine();
        weekend.loadPolicy(policyJson);
        assertTrue(weekend.evaluate(new EvaluationRequest("u", "r", "read")).isDenied());
    }

    @Test
    void timeConditionMonday() {
        ZonedDateTime mon = ZonedDateTime.of(2024, 3, 4, 10, 0, 0, 0, ZoneId.of("UTC"));
        PolicyEngine e = EngineConfig.builder().timeProvider(() -> mon).build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "tmon", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "smon", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "key": "time.dayOfWeek", "op": "EQUALS", "value": "MONDAY"
                    }
                  }]
                }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isAllowed());
    }

    @Test
    void timeConditionSaturdayDenied() {
        ZonedDateTime sat = ZonedDateTime.of(2024, 3, 9, 10, 0, 0, 0, ZoneId.of("UTC"));
        PolicyEngine e = EngineConfig.builder().timeProvider(() -> sat).build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "tweek", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "sweek", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "key": "time.dayOfWeek", "op": "IN",
                      "values": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"]
                    }
                  }]
                }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isDenied());
    }

    // =========================================================================
    // Conflict Resolution
    // =========================================================================

    @Test
    void denyOverride_denyWins() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new DenyOverrideStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "pa", "version": "1.0", "priority": 0,
                  "statements": [
                    { "id": "allow1", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } },
                    { "id": "deny1",  "effect": "Deny",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);

        EvaluationResult r = e.evaluate(new EvaluationRequest("u", "r", "read"));
        assertTrue(r.isDenied());
        assertEquals("deny1", r.auditLog().decidingStatementId());
    }

    @Test
    void denyOverride_allowWinsWhenNoDeny() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new DenyOverrideStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "pb", "version": "1.0", "priority": 0,
                  "statements": [
                    { "id": "allow1", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } },
                    { "id": "allow2", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isAllowed());
    }

    @Test
    void firstMatch_highPriorityDenyWins() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new FirstMatchStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "pc-allow", "version": "1.0", "priority": 5,
                  "statements": [
                    { "id": "low-allow", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);
        e.loadPolicy("""
                {
                  "id": "pc-deny", "version": "1.0", "priority": 20,
                  "statements": [
                    { "id": "high-deny", "effect": "Deny",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);

        EvaluationResult r = e.evaluate(new EvaluationRequest("u", "r", "read"));
        assertTrue(r.isDenied());
        assertEquals("high-deny", r.auditLog().decidingStatementId());
    }

    @Test
    void firstMatch_higherPriorityAllowWins() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new FirstMatchStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "pd-allow", "version": "1.0", "priority": 20,
                  "statements": [
                    { "id": "high-allow", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);
        e.loadPolicy("""
                {
                  "id": "pd-deny", "version": "1.0", "priority": 5,
                  "statements": [
                    { "id": "low-deny", "effect": "Deny",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);

        EvaluationResult r = e.evaluate(new EvaluationRequest("u", "r", "read"));
        assertTrue(r.isAllowed());
        assertEquals("high-allow", r.auditLog().decidingStatementId());
    }

    @Test
    void mostSpecificWins_exactPrincipalBeatsWildcard() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new MostSpecificWinsStrategy())
                .build().buildEngine();

        // Generic wildcard ALLOW
        e.loadPolicy("""
                {
                  "id": "pe-wild", "version": "1.0", "priority": 0,
                  "statements": [
                    { "id": "wild-allow", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);
        // Exact DENY for alice
        e.loadPolicy("""
                {
                  "id": "pe-exact", "version": "1.0", "priority": 0,
                  "statements": [
                    { "id": "exact-deny", "effect": "Deny",
                      "target": {
                        "principals": ["user:alice"],
                        "resources":  ["*"],
                        "actions":    ["*"]
                      }
                    }
                  ]
                }
                """);

        EvaluationResult r = e.evaluate(new EvaluationRequest("user:alice", "r", "read"));
        assertTrue(r.isDenied());
        assertEquals("exact-deny", r.auditLog().decidingStatementId());
    }

    @Test
    void mostSpecificWins_tieBreakerFavorsDeny() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new MostSpecificWinsStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                {
                  "id": "pf", "version": "1.0", "priority": 0,
                  "statements": [
                    { "id": "tie-allow", "effect": "Allow",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } },
                    { "id": "tie-deny",  "effect": "Deny",
                      "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] } }
                  ]
                }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isDenied());
    }

    // =========================================================================
    // Audit Log
    // =========================================================================

    @Test
    void auditLogContainsUniqueRequestId() {
        engine.loadPolicy("""
                { "id": "pa1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        EvaluationRequest r1 = new EvaluationRequest("u", "r", "read");
        EvaluationRequest r2 = new EvaluationRequest("u", "r", "read");

        AuditLog log1 = engine.evaluate(r1).auditLog();
        AuditLog log2 = engine.evaluate(r2).auditLog();

        assertNotEquals(log1.requestId(), log2.requestId());
    }

    @Test
    void auditLogHasConditionTrace() {
        engine.loadPolicy("""
                {
                  "id": "pa2", "version": "1.0", "priority": 0,
                  "statements": [{
                    "id": "s", "effect": "Allow",
                    "target": { "principals": ["*"], "resources": ["*"], "actions": ["*"] },
                    "conditions": {
                      "operator": "AND",
                      "conditions": [
                        { "key": "a", "op": "EQUALS", "value": "1" },
                        { "key": "b", "op": "EQUALS", "value": "2" }
                      ]
                    }
                  }]
                }
                """);

        AuditLog log = engine.evaluate(new EvaluationRequest(
                "u", "r", "read", Map.of("a", "1", "b", "2"))).auditLog();

        var stmtTrace = log.policyTraces().get(0).statementTraces().get(0);
        assertEquals("AND", stmtTrace.conditionTrace().nodeType());
        assertEquals(2, stmtTrace.conditionTrace().children().size());
    }

    @Test
    void auditLogTargetNotMatchedTrace() {
        engine.loadPolicy("""
                { "id": "pa3", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["user:alice"],"resources":["*"],"actions":["*"]} }] }
                """);

        AuditLog log = engine.evaluate(
                new EvaluationRequest("user:bob", "r", "read")).auditLog();
        var stmtTrace = log.policyTraces().get(0).statementTraces().get(0);
        assertFalse(stmtTrace.targetMatched());
        assertNull(stmtTrace.targetMatchDetail());
    }

    @Test
    void auditSerializerProducesValidJson() throws Exception {
        engine.loadPolicy("""
                { "id": "pa4", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        AuditLog log = engine.evaluate(
                new EvaluationRequest("u", "r", "read")).auditLog();
        String json = AuditSerializer.toJson(log);
        assertFalse(json.isBlank());
        assertTrue(json.contains("ALLOW") || json.contains("DENY"));
    }

    @Test
    void auditSummaryLineFormat() {
        engine.loadPolicy("""
                { "id": "pa5", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        AuditLog log = engine.evaluate(
                new EvaluationRequest("user:alice", "resource:docs", "read")).auditLog();
        String summary = AuditSerializer.toSummaryLine(log);
        assertTrue(summary.contains("DECISION="));
        assertTrue(summary.contains("principal=user:alice"));
        assertTrue(summary.contains("resource=resource:docs"));
        assertTrue(summary.contains("action=read"));
        assertTrue(summary.contains("duration_ms="));
    }

    @Test
    void implicitDenyHasReason() {
        AuditLog log = engine.evaluate(
                new EvaluationRequest("u", "r", "read")).auditLog();
        assertEquals(com.policyengine.model.Effect.DENY, log.finalDecision());
        assertNotNull(log.implicitDenyReason());
    }

    // =========================================================================
    // Caching
    // =========================================================================

    @Test
    void cacheInvalidationForcesRecompile() {
        engine.loadPolicy("""
                { "id": "cache1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "a")).isAllowed());
        engine.invalidateCache("cache1");
        // Should still work after cache invalidation (recompiles from repository)
        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "a")).isAllowed());
    }

    @Test
    void concurrentEvaluationNoCrash() throws InterruptedException {
        engine.loadPolicy("""
                { "id": "conc1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        int threads = 10;
        int requestsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        EvaluationResult r = engine.evaluate(
                                new EvaluationRequest("u", "r", "read"));
                        assertTrue(r.isAllowed());
                    }
                } catch (Throwable e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(errors.isEmpty(), "Errors during concurrent evaluation: " + errors);
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    void emptyConditionsAlwaysMatch() {
        engine.loadPolicy("""
                { "id": "edge1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]}
                  }]
                }
                """);
        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "read")).isAllowed());
    }

    @Test
    void nullContextTreatedAsEmpty() {
        engine.loadPolicy("""
                { "id": "edge2", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        // EvaluationRequest with null context is converted to empty map
        EvaluationRequest req = new EvaluationRequest(null, "u", "r", "read", null);
        assertDoesNotThrow(() -> engine.evaluate(req));
    }

    @Test
    void removePolicyStopsMatching() {
        engine.loadPolicy("""
                { "id": "rem1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "read")).isAllowed());
        engine.removePolicy("rem1");
        assertTrue(engine.evaluate(new EvaluationRequest("u", "r", "read")).isDenied());
    }

    @Test
    void conflictingPoliciesTwoPoliciesOneAllowOneDeny_denyOverride() {
        PolicyEngine e = EngineConfig.builder()
                .strategy(new DenyOverrideStrategy())
                .build().buildEngine();

        e.loadPolicy("""
                { "id": "conf-allow", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s-allow", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);
        e.loadPolicy("""
                { "id": "conf-deny", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s-deny", "effect": "Deny",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        assertTrue(e.evaluate(new EvaluationRequest("u", "r", "read")).isDenied());
    }

    @Test
    void auditConsumerIsCalledAsync() throws InterruptedException {
        List<AuditLog> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        PolicyEngine e = EngineConfig.builder()
                .auditConsumer(log -> {
                    received.add(log);
                    latch.countDown();
                })
                .build()
                .buildEngine();

        e.loadPolicy("""
                { "id": "audit1", "version": "1.0", "priority": 0,
                  "statements": [{ "id": "s", "effect": "Allow",
                    "target": {"principals":["*"],"resources":["*"],"actions":["*"]} }] }
                """);

        e.evaluate(new EvaluationRequest("u", "r", "read"));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Audit consumer was not called within 5s");
        assertEquals(1, received.size());
    }
}
