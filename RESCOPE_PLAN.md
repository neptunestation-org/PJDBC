# PJDBC Post-2.0 Improvement Plan

## Executive Summary

PJDBC 2.0 successfully focused the project by removing redundant drivers and achieving zero runtime dependencies. The URL-based composition model and agent-first introspection are genuinely novel. This plan addresses technical debt, correctness bugs, and strategic gaps.

**What's Good (Keep)**:
- Zero runtime dependencies
- URL-based composable driver chaining
- Compile-time capability manifest generation
- Explicit "NOT for" scope documentation
- Composition validation at connect-time

**What Needs Work**:
- Several drivers have correctness bugs that make them unreliable
- PreparedStatement retry is fundamentally broken
- FederatingDriver isn't real federation
- No SQL parsing means security drivers are bypassable
- No observability into driver behavior
- Missing real-world integration patterns

---

## Priority Classification

### P0: Correctness Bugs (Users hitting these now)
These are bugs that cause incorrect behavior or data loss.

### P1: Architectural Gaps (Limits usefulness)
These prevent the tool from being used for its stated purpose.

### P2: Developer Experience (Friction)
These make the tool harder to use but don't break it.

### P3: Strategic Gaps (Future concerns)
These limit adoption or long-term viability.

---

## P0: Correctness Bugs

### 0.1 RetryDriver: PreparedStatement Parameter Bindings Lost

**Problem**: `RetryDriver.java:517-535` recreates PreparedStatements on connection failure, but parameter bindings are lost. The driver logs a warning but proceeds anyway, meaning the retried query runs with NULL/default parameters.

```java
LOG.warning(() -> "RetryDriver: Recreated PreparedStatement. Parameter bindings were lost and must be re-applied.");
```

This is silent data corruption. A query like `UPDATE users SET balance = ? WHERE id = ?` becomes `UPDATE users SET balance = NULL WHERE id = NULL`.

**Impact**: High - most real applications use PreparedStatements.

**Options**:
1. **Track parameters**: Intercept all `setXxx()` calls and replay them after recreation. Complex, ~200 lines.
2. **Fail loudly**: Throw SQLException on connection error with PreparedStatement. At least it's honest.
3. **Document limitation**: Add prominent warning that retry only works safely with plain Statement.

**Recommendation**: Option 2 (fail loudly) for 2.1, Option 1 for 3.0.

---

### 0.2 MergingResultSet: JDBC Contract Violations

**Problem**: `FederatingDriver.java` MergingResultSet has multiple JDBC contract violations:

```java
@Override
public int getRow() throws SQLException {
    if (closed) throw new SQLException("ResultSet is closed");
    return currentRow;  // Returns 0 before first next() - violates contract
}
```

Actually, looking at the current code, `currentRow` is tracked but initialized to 0 and only incremented after `next()`. The issue is that row numbers should be 1-indexed after first `next()`, and the implementation seems correct now.

**Actual remaining issues**:
- `beforeFirst()` resets all delegates but doesn't verify they support `TYPE_SCROLL_INSENSITIVE`
- `isFirst()` only checks first delegate, not cumulative position
- No implementation of `absolute()`, `relative()`, `previous()` for scrollable result sets

**Impact**: Medium - affects applications that use cursor positioning.

**Solution**: Either fully implement scrollable ResultSet contract or throw `SQLFeatureNotSupportedException` for unsupported operations.

---

### 0.3 Random Thread Safety in RetryConfig

**Problem**: Already fixed in current code - uses `ThreadLocalRandom.current()`:

```java
delay = delay + ThreadLocalRandom.current().nextInt((int) Math.max(1, delay / 4));
```

**Status**: ✅ Already fixed.

---

### 0.4 FederatingDriver: Thread Pool per Query

**Problem**: Already fixed in current code - uses virtual threads:

```java
private static ExecutorService createParallelExecutor() {
    if (VIRTUAL_THREADS_AVAILABLE) {
        return Executors.newVirtualThreadPerTaskExecutor();
    } else {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
}
```

**Status**: ✅ Already fixed.

---

### 0.5 DataMaskingDriver: Incomplete Getter Coverage

**Problem**: Only `getString()` and `getObject()` are masked. Sensitive data retrieved via other methods bypasses masking:
- `getBytes()` - binary representation
- `getLong()` / `getBigDecimal()` - numeric credit cards, SSNs
- `getCharacterStream()` - CLOB data
- `getNString()` - national character sets

**Impact**: High - masking is a security control that can be bypassed.

**Solution**: Override all getters. For numeric types, either:
- Return 0 (loses information that data exists)
- Throw SQLException (breaks code expecting numbers)
- Document that numeric columns must be cast to VARCHAR in query

**Recommendation**: Throw SQLException with helpful message for masked numeric columns.

---

## P1: Architectural Gaps

### 1.1 No SQL Parsing

**Problem**: `ReadonlyDriver`, `SchemaValidationDriver`, `DataMaskingDriver` all need to understand SQL structure but use regex/string matching. This means:

- `SELECT * FROM (SELECT secret FROM passwords) AS x` bypasses SchemaValidationDriver
- `WITH cte AS (DELETE FROM users RETURNING *) SELECT * FROM cte` bypasses ReadonlyDriver
- Views, CTEs, subqueries, and database-specific syntax break assumptions

**Impact**: High for security drivers - they provide false confidence.

**Options**:
1. **Add SQL parser**: JSqlParser is Apache-licensed, ~500KB. Breaks zero-dependency goal.
2. **Document limitations**: Be honest that these are "best effort" not security boundaries.
3. **Restrict to simple queries**: Reject queries with parentheses, WITH clauses, etc.

**Recommendation**: Option 2 for now. These drivers are useful for preventing accidents, not malicious actors. Update README to clarify.

---

### 1.2 FederatingDriver Isn't Real Federation

**Problem**: The driver just broadcasts the same query to all backends and concatenates results. There's no:
- Table-to-database routing (query `users` from db1, `orders` from db2)
- Query rewriting for heterogeneous schemas
- Cross-database joins
- Shard key routing

It's "parallel query execution" not "federation."

**Impact**: Medium - the name misleads users about capabilities.

**Options**:
1. **Rename**: `ParallelQueryDriver` or `BroadcastDriver`
2. **Add routing**: Parse queries, extract table names, route to configured backends
3. **Document clearly**: "This driver runs the same query on all backends"

**Recommendation**: Option 3 for 2.1, consider Option 2 for 3.0 if there's demand.

---

### 1.3 FilterDriver Has No Built-in Transformers

**Problem**: To use FilterDriver, you must:
1. Write a class implementing `JdbcTransformer`
2. Compile it
3. Put it on classpath
4. Reference via `class=com.example.MyTransformer`

There are no built-in transformers for common cases:
- Table/schema renaming
- Adding WHERE clauses (row-level security)
- Dialect translation
- Query prefixing/logging

**Impact**: Medium - reduces utility for common use cases.

**Solution**: Add URL-configurable transformers:
```
jdbc:filter[rename.OLD_TABLE=NEW_TABLE]:...
jdbc:filter[prefix.schema=tenant_123]:...
jdbc:filter[append.where=deleted=false]:...
```

---

### 1.4 No Transaction Awareness in FederatingDriver

**Problem**: Current code warns but allows transactions:

```java
if (!autoCommit) {
    String msg = "FederatingDriver does not support coordinated transactions...";
    if (config.isStrictTransactions()) {
        throw new SQLException(msg);
    } else {
        LOG.warning(msg);
    }
}
```

The default is `strictTransactions=false`, silently allowing data inconsistency.

**Impact**: High - split-brain on partial commit failure.

**Recommendation**: Change default to `strictTransactions=true`. Users who understand the risk can opt out.

---

## P2: Developer Experience

### 2.1 No Observability Hooks

**Problem**: `PjdbcListeners` exists but:
- No way to know when retry occurred (count, delays)
- No circuit breaker state change notifications
- No SQL transformation audit trail
- No federation routing decisions

**Impact**: Medium - hard to debug/monitor in production.

**Solution**: Expand `PjdbcEventListener` interface and add registration mechanisms:
- URL parameter: `jdbc:retry[listener=com.example.MyListener]:...`
- Programmatic: `PjdbcListeners.register(listener)`
- ServiceLoader: `META-INF/services/org.pjdbc.sql.PjdbcEventListener`

---

### 2.2 MockDriver Can't Configure Expected Results

**Problem**: MockDriver records what was executed but can't return configured results:

```java
String log = MockDriver.getLog("jdbc:mock:testdb");  // What was run
// But no way to say: "When SELECT * FROM users, return these rows"
```

**Impact**: Medium - limits testing utility.

**Solution**: Add result configuration:
```java
MockDriver.when("SELECT * FROM users")
    .thenReturn(new String[]{"id", "name"}, new Object[][]{{1, "Alice"}, {2, "Bob"}});
```

---

### 2.3 No Spring/Hibernate Integration Examples

**Problem**: Real-world usage is via DataSource, not raw DriverManager. No examples of:
- `PjdbcDataSource` wrapper
- Spring Boot auto-configuration
- Hibernate dialect considerations
- Connection pool integration (HikariCP wrapping PJDBC)

**Impact**: Medium - users have to figure out integration themselves.

**Solution**: Add `docs/INTEGRATION.md` with tested examples.

---

### 2.4 CLI Disconnected from Runtime

**Problem**: CLI validates URLs and shows chains, but can't inspect running drivers:
- No JMX MBeans for circuit breaker state
- No actuator endpoints
- No way to query active connections/retries

**Impact**: Low - operational visibility is nice-to-have.

**Solution**: Add optional JMX registration (behind flag to maintain zero-dep default).

---

### 2.5 Code Formatting Inconsistency

**Problem**: AbstractStatement and related files use unreadable single-line methods:

```java
public ResultSet executeQuery (String sql) throws SQLException {ArrayList<ResultSet> rsets = new ArrayList<ResultSet>(); for (Statement s : getStatements()) rsets.add(s.executeQuery(sql)); for (ResultSet r : rsets) return wrap(r); throw new SQLException();}
```

**Impact**: Low - maintainability/readability issue only.

**Solution**: Reformat to standard Java conventions.

---

## P3: Strategic Gaps

### 3.1 No Migration Path from Replaced Tools

**Problem**: CLAUDE.md says "use p6spy for logging, HikariCP for pooling" but doesn't explain how to use them *with* PJDBC:
- Does HikariCP wrap PJDBC URLs or vice versa?
- Can p6spy intercept PJDBC-transformed SQL?
- What's the recommended composition?

**Impact**: Low - but hurts adoption from users of those tools.

**Solution**: Add `docs/INTEROP.md` documenting tested configurations.

---

### 3.2 Agent-First Design is Aspirational

**Problem**: The capabilities manifest exists, but:
- No example of an AI agent using it
- No prompt templates for agent consumption
- No validation that manifest is agent-usable

**Impact**: Low - marketing claim without evidence.

**Solution**: Add `examples/agent-usage/` with working demo of agent composing drivers from manifest.

---

### 3.3 Java 17 LTS Support

**Problem**: Java 21 requirement limits enterprise adoption. Many organizations are on Java 17 LTS.

**Analysis**:
- Virtual threads (Java 21): Only in FederatingDriver, already has fallback
- No other Java 21 features are critical

**Status**: Already handled with runtime detection and fallback.

---

## Execution Roadmap

### v2.1.0 - Honesty Release ✅ COMPLETE
Focus: Make existing features reliable or fail clearly.

- [x] **P0.1**: RetryDriver throws on PreparedStatement connection error (fail loudly)
- [x] **P0.5**: DataMaskingDriver throws on masked numeric column access
- [x] **P1.4**: Change `strictTransactions` default to `true`
- [x] **P1.1**: Add "Security Limitations" section to README for regex-based drivers
- [x] **P1.2**: Add "Limitations" section to FederatingDriver docs clarifying it's broadcast, not federation
- [x] **P2.5**: Reformat AbstractStatement/AbstractPreparedStatement/AbstractCallableStatement (already done)

### v2.2.0 - Observability Release ✅ IN PROGRESS
Focus: Make behavior visible.

- [x] **P2.1**: Expand PjdbcEventListener with retry/circuit breaker/transform events (already existed)
- [x] Add debug logging throughout (PjdbcDebug, DebugEventListener already existed)
- [x] Wire FederatingDriver to fire federated query events
- [x] Wire ChaosDriver to fire chaos injection events
- [x] CLI `--verbose` flag for detailed output
- [ ] Add JMX MBeans for CircuitBreakerDriver state (optional, flag-enabled)

### v2.3.0 - Usability Release
Focus: Make common tasks easier.

- [ ] **P1.3**: Add built-in FilterDriver transformers (rename, prefix, append)
- [ ] **P2.2**: MockDriver result configuration API
- [ ] **P2.3**: Add docs/INTEGRATION.md with Spring/Hibernate/HikariCP examples
- [ ] **P3.1**: Add docs/INTEROP.md for p6spy/etc. coexistence

### v3.0.0 - Breaking Changes (if needed)
Focus: Fix things that require API changes.

- [ ] **P0.1**: RetryDriver parameter tracking and replay (proper fix)
- [ ] **P0.2**: Full scrollable ResultSet support or explicit feature rejection
- [ ] **P1.2**: Optional table-based routing for FederatingDriver
- [ ] **P3.2**: Agent usage examples and prompt templates

---

## Appendix: Current State Metrics

### After v2.1.0 (Current)

| Category | Issue Count | Status |
|----------|-------------|--------|
| Silent data corruption | 0 | ✅ Fixed (PreparedStatement throws, strictTransactions=true) |
| Security bypassable | 3 (regex-based drivers) | ⚠️ Documented in README |
| JDBC contract violations | 2 (MergingResultSet) | Deferred to v3.0 |
| Missing functionality | 4 (observability, mocking, etc.) | Planned for v2.2-v2.3 |
| Documentation gaps | 3 (integration, interop, agent) | Planned for v2.3 |
| Code quality | 0 | ✅ Already formatted |

### Before v2.1.0

| Category | Issue Count | Severity |
|----------|-------------|----------|
| Silent data corruption | 1 (PreparedStatement retry) | Critical |
| Security bypassable | 3 (regex-based drivers) | High |
| JDBC contract violations | 2 (MergingResultSet) | Medium |
| Missing functionality | 4 (observability, mocking, etc.) | Medium |
| Documentation gaps | 3 (integration, interop, agent) | Low |
| Code quality | 1 (formatting) | Low |

---

## Recommendation: What to Fix First

**Fix P0.1 (RetryDriver PreparedStatement) immediately.**

Rationale:
1. It's silent data corruption - the worst kind of bug
2. Most real applications use PreparedStatements
3. The fix is simple: throw SQLException instead of proceeding with lost parameters
4. It's a 10-line change with huge safety improvement

```java
private void recreateStatement() throws SQLException {
    if (sql == null) {
        throw new SQLException(
            "RetryDriver: Cannot retry PreparedStatement after connection failure. " +
            "Parameter bindings cannot be preserved. Use application-level retry " +
            "or plain Statement for retryable operations.");
    }
    // ... rest of recreation logic
}
```

After that, tackle P1.4 (strictTransactions default) because it's another silent-corruption issue with a one-line fix.
