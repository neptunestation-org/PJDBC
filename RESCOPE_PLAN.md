# PJDBC Post-2.0 Improvement Plan

## Executive Summary

PJDBC 2.0 successfully focused the project by removing redundant drivers (caching, pooling, observability, load balancing) and achieving zero runtime dependencies. This plan addresses technical debt and architectural issues that remain.

**What's Good (Keep)**:
- Zero runtime dependencies
- URL-based composable drivers
- Compile-time capability manifest generation
- Explicit "NOT for" documentation
- CLI for driver discovery

**What Needs Work**:
- Massive code duplication across drivers
- Several drivers have correctness bugs
- No observability into driver behavior
- Inconsistent code formatting
- Missing JDBC contract compliance

---

## Phase 1: Architectural Refactoring

### 1.1 Eliminate Driver Boilerplate Duplication

**Problem**: Every driver that needs custom Connection/Statement behavior repeats identical `createStatement`/`prepareStatement` overrides. Compare:
- `RetryDriver.java:251-310`
- `DataMaskingDriver.java:292-351`
- `FederatingDriver.java:171-251`

**Solution**: Extract a `ProxyConnectionTemplate` that accepts lambdas for statement wrapping:

```java
public abstract class ConfigurableProxyDriver<C extends DriverConfig> extends AbstractProxyDriver {

    protected abstract C parseConfig(String url);
    protected abstract Statement wrapStatement(Statement delegate, Connection conn, C config);
    protected abstract PreparedStatement wrapPreparedStatement(PreparedStatement delegate, Connection conn, C config);

    // Single implementation of all createStatement/prepareStatement overloads
    // that delegates to the wrap* methods
}
```

**Files to modify**:
- `src/main/java/org/pjdbc/sql/AbstractProxyDriver.java`
- All 10+ drivers that extend it

**Estimated reduction**: ~800 lines of duplicated code

---

### 1.2 Fix AbstractStatement Formatting

**Problem**: `AbstractStatement.java:64-104` is unreadable single-line methods:

```java
public ResultSet executeQuery (String sql) throws SQLException {ArrayList<ResultSet> rsets = new ArrayList<ResultSet>(); for (Statement s : getStatements()) rsets.add(s.executeQuery(sql)); for (ResultSet r : rsets) return wrap(r); throw new SQLException();}
```

**Solution**: Reformat to standard Java conventions. This is a readability/maintainability issue, not a feature.

**Files to modify**:
- `src/main/java/org/pjdbc/sql/AbstractStatement.java`
- `src/main/java/org/pjdbc/sql/AbstractPreparedStatement.java`
- `src/main/java/org/pjdbc/sql/AbstractCallableStatement.java`
- `src/main/java/org/pjdbc/sql/AbstractConnection.java`

---

## Phase 2: Bug Fixes

### 2.1 RetryDriver: Recreate Statements on Connection Failure

**Problem**: `RetryDriver.java:323-348` retries using the *same* Statement object after connection errors. If the connection died, the statement is invalid and retry will fail.

**Solution**: Detect connection state before retry. If connection is dead:
1. Close the dead connection
2. Obtain new connection from DriverManager
3. Recreate statement on new connection
4. Retry operation

**Complexity**: High - requires tracking the original SQL and connection parameters.

**Alternative**: Document limitation clearly and add `reconnectOnFailure=false` parameter with warning.

---

### 2.2 FederatingDriver: Fix Thread Pool Leak

**Problem**: `FederatingDriver.java:282-298` creates a new `ExecutorService` per query execution:

```java
ExecutorService executor = Executors.newFixedThreadPool(delegates.size());
// ... use executor ...
executor.shutdown();
```

Under high query load, this creates/destroys threads constantly.

**Solution**:
- Option A: Use a shared, connection-scoped thread pool
- Option B: Use virtual threads (Java 21+): `Executors.newVirtualThreadPerTaskExecutor()`
- Option C: Use `CompletableFuture.supplyAsync()` with common pool

**Recommended**: Option B (virtual threads) since we already require Java 21.

---

### 2.3 DataMaskingDriver: Mask All Data Types

**Problem**: `DataMaskingDriver.java:436-469` only masks `getString()` and `getObject()` returning String. Columns retrieved via other methods bypass masking:
- `getBytes()` - binary data
- `getLong()` / `getBigDecimal()` - numeric credit cards
- `getCharacterStream()` - CLOB data
- `getNString()` - national character sets

**Solution**: Override all getters that could return sensitive data:

```java
@Override
public long getLong(int columnIndex) throws SQLException {
    if (shouldMaskColumn(columnIndex)) {
        // Return 0 or throw? Masking numbers is semantically different
        throw new SQLException("Column is masked; use getString() for masked value");
    }
    return super.getLong(columnIndex);
}
```

**Decision needed**: Should masked numeric columns return 0, throw, or convert to masked string?

---

### 2.4 MergingResultSet: Fix JDBC Contract Violations

**Problem**: `FederatingDriver.java:665-668`:

```java
@Override
public int getRow() throws SQLException {
    // Row number tracking across multiple result sets is complex
    // For now, return 0 (unknown)
    return 0;
}
```

Returning 0 violates JDBC contract (0 means "not on a valid row").

**Solution**: Track cumulative row count across merged result sets:

```java
private int cumulativeRow = 0;

@Override
public boolean next() throws SQLException {
    // ... existing logic ...
    if (moved) cumulativeRow++;
    return moved;
}

@Override
public int getRow() throws SQLException {
    return cumulativeRow;
}
```

---

### 2.5 Random Thread Safety in RetryConfig

**Problem**: `RetryDriver.java:172`:

```java
this.random = new Random();
```

`Random` is not thread-safe, but `RetryConfig` is shared across threads via the connection.

**Solution**: Use `ThreadLocalRandom.current()` instead:

```java
public long calculateDelay(int attempt) {
    // ...
    if (jitter) {
        delay = delay + ThreadLocalRandom.current().nextInt((int) Math.max(1, delay / 4));
    }
    return delay;
}
```

---

## Phase 3: Missing Functionality

### 3.1 Add Observability Hooks

**Problem**: No way to know when:
- Retry occurred (and how many times)
- Circuit breaker tripped
- SQL was transformed
- Connection was federated

**Solution**: Add a `PjdbcEventListener` interface:

```java
public interface PjdbcEventListener {
    default void onRetry(String sql, SQLException cause, int attempt, long delayMs) {}
    default void onCircuitBreakerStateChange(String name, State oldState, State newState) {}
    default void onSqlTransformed(String original, String transformed) {}
    default void onFederatedQuery(String sql, List<String> targetUrls) {}
}
```

Registration via:
- URL parameter: `jdbc:retry[listener=com.example.MyListener]:...`
- Programmatic: `PjdbcListeners.register(listener)`
- ServiceLoader: `META-INF/services/org.pjdbc.PjdbcEventListener`

---

### 3.2 FilterDriver: Fix Thread-Local State

**Problem**: `FilterDriver.java:20-21`:

```java
protected ThreadLocal<JdbcTransformer> transformer =
    ThreadLocal.withInitial(() -> new AbstractJdbcTransformer() {});
```

If you share a `FilterDriver` instance (normal JDBC pattern), setting transformer on thread A doesn't affect thread B. This is confusing.

**Solution**: Move transformer to connection scope, not driver scope:

```java
// In URL: jdbc:filter[class=com.example.MyTransformer]:...
// Instantiate per-connection, not per-thread
```

Or document the threading model explicitly and provide `FilterDriver.setTransformer(Connection, JdbcTransformer)`.

---

### 3.3 FederatingDriver: Transaction Warnings

**Problem**: No transaction coordination across federated connections. `commit()` succeeds on some backends, fails on others → split-brain.

**Solution**:
1. Detect when `setAutoCommit(false)` is called
2. Log warning or throw if `strictTransactions=true` parameter set
3. Document limitation prominently

```java
@Override
public void setAutoCommit(boolean autoCommit) throws SQLException {
    if (!autoCommit && config.isStrictTransactions()) {
        throw new SQLException(
            "FederatingDriver does not support transactions across multiple databases. " +
            "Set strictTransactions=false to allow (at your own risk).");
    }
    // ... delegate to all connections ...
}
```

---

## Phase 4: Developer Experience

### 4.1 Improve CLI Usability

**Current state**:
```bash
mvn exec:java -Dexec.args="list"  # Verbose
java -jar target/PJDBC-2.0.0.jar list  # Requires build
```

**Improvements**:
- Add `--help` flag
- Add shell completion scripts (bash, zsh, fish)
- Add `pjdbc` wrapper script in repo root
- Improve error messages with suggestions

**New commands**:
```bash
pjdbc suggest "I need to retry failed queries"
# → Consider: jdbc:retry[maxRetries=3]:...
# → Warning: Only use with idempotent operations

pjdbc compose retry timeout postgresql
# → jdbc:retry:jdbc:timeout:jdbc:postgresql://HOST/DB
```

---

### 4.2 Add Debug Logging

**Problem**: No visibility into driver chain behavior for troubleshooting.

**Solution**: Add optional `java.util.logging` output (no external dependency):

```java
private static final Logger LOG = Logger.getLogger(RetryDriver.class.getName());

// In retry logic:
LOG.fine(() -> String.format("Retry attempt %d/%d after %dms for SQL: %s",
    attempt, maxRetries, delay, sql));
```

Enable via: `-Dorg.pjdbc.level=FINE`

---

### 4.3 Consider Java 17 LTS Support

**Problem**: Java 21 requirement limits adoption. Many enterprises are still on Java 17 LTS.

**Analysis**:
- Virtual threads (Java 21) only used in FederatingDriver parallel execution
- Pattern matching for switch (Java 21) used in a few places
- No other Java 21 features critical to core functionality

**Solution**:
- Make Java 17 the minimum
- Use virtual threads conditionally via reflection when available
- Or: Keep Java 21, document reasoning (it's 2026, Java 21 is LTS)

**Decision**: TBD based on user feedback

---

## Phase 5: Documentation

### 5.1 Add Composition Best Practices

Document common patterns and anti-patterns:

```markdown
## Driver Composition

### Recommended Order (outermost → innermost)
1. retry (catch transient failures from anything below)
2. timeout (enforce limits before retry exhausts)
3. circuitbreaker (fail fast if backend is down)
4. readonly/schema/mask (access control)
5. filter (SQL transformation)
6. actual database driver

### Anti-Patterns
❌ `jdbc:readonly:jdbc:retry:...` - Retrying after readonly check is pointless
❌ `jdbc:retry:jdbc:federate:...` - Retry won't help if one backend is down
❌ `jdbc:mask:jdbc:filter:...` - Filter might expose masked data
```

---

### 5.2 Add Troubleshooting Guide

```markdown
## Troubleshooting

### "Connection is closed" after retry
RetryDriver reuses the same Statement after connection failures.
**Workaround**: Implement retry at application level for connection errors.
**Status**: Known limitation, fix planned for v2.1.

### Masked column returns wrong type
DataMaskingDriver only masks String columns.
**Workaround**: Cast sensitive numeric columns to VARCHAR in your query.
**Status**: Known limitation, fix planned for v2.1.

### Federated query returns inconsistent row count
MergingResultSet.getRow() returns 0.
**Workaround**: Track row count in application code.
**Status**: Known limitation, fix planned for v2.1.
```

---

## Execution Checklist

### v2.1.0 (Bug Fixes)
- [ ] Fix Random thread safety in RetryConfig
- [ ] Fix MergingResultSet.getRow()
- [ ] Fix FederatingDriver thread pool leak (use virtual threads)
- [ ] Add transaction warnings to FederatingDriver
- [ ] Reformat AbstractStatement/AbstractPreparedStatement/etc.

### v2.2.0 (Observability)
- [ ] Add PjdbcEventListener interface
- [ ] Add debug logging with java.util.logging
- [ ] Add CLI `--help` and improved error messages

### v2.3.0 (Architecture)
- [ ] Extract ConfigurableProxyDriver to reduce boilerplate
- [ ] Fix FilterDriver thread-local confusion
- [ ] Expand DataMaskingDriver to cover all getters

### v3.0.0 (Breaking Changes if Needed)
- [ ] RetryDriver connection recreation (API change)
- [ ] Java 17 support decision
- [ ] CLI wrapper scripts and shell completion

---

## Appendix: Code Quality Metrics

### Current State (v2.0.0)
| Metric | Value | Target |
|--------|-------|--------|
| Duplicated lines | ~800 | <100 |
| Known JDBC contract violations | 3 | 0 |
| Thread-safety issues | 2 | 0 |
| Uncovered getter methods (DataMaskingDriver) | 15+ | 0 |

### After v2.3.0
| Metric | Value | Target |
|--------|-------|--------|
| Duplicated lines | <100 | ✓ |
| Known JDBC contract violations | 0 | ✓ |
| Thread-safety issues | 0 | ✓ |
| Uncovered getter methods (DataMaskingDriver) | 0 | ✓ |
