# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Maven is not on PATH. Use the bundled IntelliJ Maven:

```bash
# Run all tests
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" test

# Run a single test class
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" test -Dtest=PolicyEngineIntegrationTest

# Run a single test method
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" test -Dtest=PolicyEngineIntegrationTest#basicAllow

# Compile only
"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" compile
```

Java 17+ is required (JDK 24 is installed at `/Users/kanchi/Library/Java/JavaVirtualMachines/openjdk-24.0.2`).

## Architecture

The engine evaluates access policies against a request `(principal, resource, action, context)` and returns `ALLOW` or `DENY` with a full audit trace.

### Evaluation hot path

```
PolicyEngineImpl.evaluate(request)
  → sort policies by priority DESC
  → for each policy: cache.get(id, () -> compiler.compile(policy))   ← zero JSON parse on hot path
  → for each CompiledStatement: matchTarget() → condition.trace()
  → ConflictResolutionStrategy.resolve(allEvaluations)
  → build AuditLog → async deliver to Consumer<AuditLog>
```

### Two-tier caching

- `PolicyRepository` (`ConcurrentHashMap`) — ground truth of parsed `Policy` objects
- `CaffeineCompiledPolicyCache` — compiled forms: pre-compiled glob `Pattern[]`, typed `Condition` AST, pre-computed specificity scores
- `loadPolicy()` eagerly compiles and caches; `invalidateCache(id)` evicts from Caffeine but keeps in repository (recompiles on next evaluate)

### Condition tree

JSON conditions are parsed recursively by `ConditionParser` into a typed tree at compile time:
- Composites: `AndCondition`, `OrCondition`, `NotCondition`
- Leaf: `LeafCondition(key, Operator, values)` — dispatches to `TimeConditionEvaluator` if `key.startsWith("time.")`, else to `ConditionEvaluator`
- `AlwaysTrueCondition` sentinel when a statement has no conditions

`Condition.trace()` always evaluates all children (no short-circuit) to produce full `ConditionTrace` trees for audit. `Condition.evaluate()` short-circuits for performance.

### Conflict resolution strategies (switchable via `EngineConfig.builder().strategy(...)`)

| Strategy | Behavior |
|---|---|
| `DenyOverrideStrategy` | Evaluate all; any DENY wins (default) |
| `FirstMatchStrategy` | Stop at first match; policies sorted by `priority` DESC |
| `MostSpecificWinsStrategy` | Score all matches; highest specificity wins; ties → DENY |

Specificity score = sum of per-field pattern scores (exact=100, prefix glob=60, mid-glob=50, ?-only=40, bare `*`=0) + `5 × leaf condition count`.

### Time conditions

Keys prefixed `time.*` (e.g. `time.hour`, `time.dayOfWeek`, `time.date`) are resolved against `TimeProvider.now()` — never against the request context map. `TimeProvider` is an interface; inject a fixed `ZonedDateTime` in tests via `EngineConfig.builder().timeProvider(() -> myTime)`.

### Audit log

Every evaluation produces an immutable `AuditLog` record containing nested `PolicyTrace → StatementTrace → ConditionTrace` trees. Delivered asynchronously via a bounded `LinkedBlockingQueue` + `ThreadPoolExecutor` (caller-runs overflow policy). Use `AuditSerializer.toJson(log)` or `toSummaryLine(log)` for output.

### Policy JSON schema

```json
{
  "id": "...", "version": "1.0", "priority": 10,
  "statements": [{
    "id": "...", "effect": "Allow",
    "target": {
      "principals": ["user:alice", "group:finance-*"],
      "resources":  ["arn:data:reports/*"],
      "actions":    ["read", "export:*"]
    },
    "conditions": {
      "operator": "AND",
      "conditions": [
        { "key": "time.hour", "op": "BETWEEN", "values": ["8","18"] },
        { "key": "user.department", "op": "EQUALS", "value": "finance" }
      ]
    }
  }]
}
```

- `effect` is case-insensitive (`"Allow"` or `"ALLOW"`)
- Target patterns support `*` (match-all) and globs (`*`, `?`)
- Leaf condition: `key` + `op` + `value` (single) or `values` (list)
- Composite condition: `operator` (AND|OR|NOT) + `conditions` array
- NOT uses single `condition` or first element of `conditions`
- Absent/null `conditions` → `AlwaysTrueCondition`

### Engine construction

```java
PolicyEngine engine = EngineConfig.builder()
    .strategy(new DenyOverrideStrategy())        // or FirstMatch / MostSpecificWins
    .timeProvider(new SystemTimeProvider())       // or () -> fixedTime for tests
    .cacheMaxSize(500)
    .cacheExpiry(Duration.ofMinutes(30))
    .auditConsumer(log -> ...)                   // optional async sink
    .build()
    .buildEngine();
```

`EngineConfig.buildEngine()` wires all components; do not construct `PolicyEngineImpl` directly.
