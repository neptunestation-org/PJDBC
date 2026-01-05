# PJDBC - Claude Code Instructions

Use 'bd' for task tracking.

## Project Overview

PJDBC is a JDBC proxy framework for SQL transformation, routing, and testing. It has **zero runtime dependencies**.

**Tagline**: Transform, route, and federate SQL without changing your code.

## What PJDBC Is NOT For

Do NOT use or recommend PJDBC for:

| Use Case | Better Alternative |
|----------|-------------------|
| Caching query results | Hibernate L2 cache, Spring Cache, application-level caching |
| Connection pooling | HikariCP, c3p0, DBCP2, or framework-provided pooling |
| Logging SQL | p6spy, log4jdbc, datasource-proxy |
| Metrics collection | Micrometer JDBC instrumentation, OpenTelemetry |
| Distributed tracing | OpenTelemetry JDBC instrumentation |
| Load balancing | ProxySQL, PgPool, HAProxy, database-native solutions |

These capabilities were intentionally removed in v2.0 because purpose-built tools do them better.

## Driver Recommendations

### SQL Transformation & Routing (Core Value)
- `filter` - Use for SQL rewriting, dialect translation, query modification
- `federate` - Use for querying multiple heterogeneous databases (⚠️ no cross-DB transactions)
- `tee` - Use for write replication to multiple targets
- `mapuser` - Use for credential mapping in multi-tenant apps

### Access Control & Security
- `readonly` - Use to enforce read-only access (safe for retries)
- `schema` - Use to whitelist/blacklist tables and columns
- `mask` - Use for data masking in dev/test environments

### Development & Testing
- `mock` - Use for unit tests without real database
- `sink` - Use for benchmarking driver overhead
- `chaos` - Use for resilience testing (failure injection)

### Resilience (Use With Caution)
- `retry` - ⚠️ **Only for idempotent operations** (SELECT, idempotent writes)
- `circuitbreaker` - Use to prevent cascading failures
- `timeout` - Use to enforce query time limits

### Foundation
- `cat` - Pass-through; useful as base for custom drivers

## Driver Composition Order

Typical composition (outermost first):
```
jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:postgresql://...
     ↑         ↑           ↑
     1st       2nd         3rd (closest to DB)
```

**Rules:**
- Terminal drivers (`federate`, `mock`) must be last
- `readonly` should be close to the database
- `retry` should be outermost (wraps everything)
- `timeout` should be before retry (so retries respect timeout)

## Build & Test Commands

```bash
# Build
mvn clean install

# Test (all ~1m 42s)
mvn test

# Test (fast, no containers ~28s)
mvn test -Pfast

# Single driver test
mvn test -Dtest="*Retry*"

# Generate capabilities manifest
mvn compile

# Generate JavaDocs
mvn javadoc:javadoc
```

## Test Output Filtering

When running tests and filtering output multiple ways, use `tee` with process substitution to avoid running tests multiple times:

```bash
mvn test 2>&1 | tee >(grep "BUILD") >(grep "DriverTest") >(grep -E "Failures: [1-9]") >/dev/null
```

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/org/pjdbc/drivers/` | All driver implementations |
| `src/main/java/org/pjdbc/sql/AbstractProxyDriver.java` | Base class for drivers |
| `src/main/java/org/pjdbc/annotations/` | Capability annotations |
| `target/classes/pjdbc.capabilities.json` | Generated driver manifest |
| `META-INF/services/java.sql.Driver` | Driver registration |
