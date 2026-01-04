# PJDBC Re-scope Implementation Plan

## New Project Identity

**Tagline**: "Transform, route, and federate SQL without changing your code"

**Description**: A JDBC proxy framework for SQL transformation, multi-database routing, and development/testing utilities.

**Version**: 2.0.0 (breaking change from 1.7.0)

---

## Phase 1: Remove Redundant Drivers

Remove drivers that duplicate well-established tools.

### Files to Delete

#### Caching Drivers (transaction semantics issues, better alternatives exist)
```
src/main/java/org/pjdbc/drivers/CachingDriver.java
src/main/java/org/pjdbc/drivers/RedisCachingDriver.java
src/main/java/org/pjdbc/drivers/MemcachedCachingDriver.java
src/main/java/org/pjdbc/drivers/HazelcastCachingDriver.java
src/main/java/org/pjdbc/drivers/CacheKeyBuilder.java
src/main/java/org/pjdbc/drivers/SafeResultSetSerializer.java
src/main/java/org/pjdbc/drivers/TableExtractor.java
src/test/java/org/pjdbc/drivers/CachingDriverTest.java
src/test/java/org/pjdbc/drivers/CachingDriverSecurityTest.java
src/test/java/org/pjdbc/drivers/RedisCachingDriverTest.java
src/test/java/org/pjdbc/drivers/RedisCachingDriverSecurityTest.java
src/test/java/org/pjdbc/drivers/MemcachedCachingDriverTest.java
src/test/java/org/pjdbc/drivers/MemcachedCachingDriverSecurityTest.java
src/test/java/org/pjdbc/drivers/HazelcastCachingDriverTest.java
src/test/java/org/pjdbc/drivers/HazelcastCachingDriverSecurityTest.java
src/test/java/org/pjdbc/drivers/CacheKeyBuilderTest.java
src/test/java/org/pjdbc/drivers/TableExtractorTest.java
```

#### Connection Pooling (just use HikariCP/etc directly)
```
src/main/java/org/pjdbc/drivers/PoolDriver.java
src/main/java/org/pjdbc/drivers/HikariPoolDriver.java
src/test/java/org/pjdbc/drivers/HikariPoolDriverTest.java
```

#### Observability (OpenTelemetry, p6spy, Micrometer are better)
```
src/main/java/org/pjdbc/drivers/LogDriver.java
src/main/java/org/pjdbc/drivers/MetricsDriver.java
src/main/java/org/pjdbc/drivers/TracingDriver.java
src/main/java/org/pjdbc/drivers/AuditDriver.java
src/test/java/org/pjdbc/drivers/MetricsDriverTest.java
src/test/java/org/pjdbc/drivers/TracingDriverTest.java
src/test/java/org/pjdbc/drivers/AuditDriverTest.java
```

#### Infrastructure-level concerns (ProxySQL, HAProxy, app-level libraries)
```
src/main/java/org/pjdbc/drivers/LoadBalancingDriver.java
src/main/java/org/pjdbc/drivers/RateLimitDriver.java
src/test/java/org/pjdbc/drivers/LoadBalancingDriverTest.java
src/test/java/org/pjdbc/drivers/RateLimitDriverTest.java
```

### Dependencies to Remove from pom.xml

```xml
<!-- Remove: no longer needed -->
<dependency>
  <groupId>com.zaxxer</groupId>
  <artifactId>HikariCP</artifactId>
</dependency>
<dependency>
  <groupId>redis.clients</groupId>
  <artifactId>jedis</artifactId>
</dependency>
<dependency>
  <groupId>net.spy</groupId>
  <artifactId>spymemcached</artifactId>
</dependency>
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast</artifactId>
</dependency>
```

### Update java.sql.Driver service file

Update `src/main/resources/META-INF/services/java.sql.Driver` to remove deleted drivers.

---

## Phase 2: Evaluate and Decide on Borderline Drivers

### Keep with Enhanced Documentation

| Driver | Decision | Rationale |
|--------|----------|-----------|
| **RetryDriver** | KEEP | Useful, but add clear idempotency warnings |
| **CircuitBreakerDriver** | KEEP | Unique at JDBC level, well-implemented |
| **TimeoutDriver** | KEEP | Complements JDBC's setQueryTimeout |

### Action Items
- Add prominent warnings in RetryDriver javadoc about idempotency
- Document that RetryDriver should only wrap read-only operations or idempotent writes
- Consider adding `idempotentOnly=true` parameter that blocks non-SELECT statements

---

## Phase 3: Retained Driver Inventory

After cleanup, the project will have **15 drivers** in 4 categories:

### Category 1: SQL Transformation & Routing (Core Value)
| Driver | Prefix | Purpose |
|--------|--------|---------|
| FilterDriver | `filter` | SQL transformation via JdbcTransformer |
| FederatingDriver | `federating` | Query multiple heterogeneous databases |
| TeeDriver | `tee` | Replicate writes to multiple targets |
| UserMapDriver | `usermap` | Map application users to DB credentials |

### Category 2: Access Control & Security
| Driver | Prefix | Purpose |
|--------|--------|---------|
| ReadonlyDriver | `readonly` | Enforce read-only access |
| SchemaValidationDriver | `schema` | Whitelist/blacklist tables/columns |
| DataMaskingDriver | `mask` | Mask sensitive data in results |

### Category 3: Development & Testing
| Driver | Prefix | Purpose |
|--------|--------|---------|
| MockDriver | `mock` | In-memory mock for testing |
| SinkDriver | `sink` | Discard all operations (benchmarking) |
| ChaosDriver | `chaos` | Fault injection for resilience testing |

### Category 4: Resilience (with caveats)
| Driver | Prefix | Purpose |
|--------|--------|---------|
| RetryDriver | `retry` | Retry transient failures |
| CircuitBreakerDriver | `circuitbreaker` | Circuit breaker pattern |
| TimeoutDriver | `timeout` | Query timeout enforcement |

### Category 5: Foundation (minimal overhead)
| Driver | Prefix | Purpose |
|--------|--------|---------|
| CatDriver | `cat` | Pass-through (baseline/testing) |

---

## Phase 4: Update Documentation

### README.md - Complete Rewrite

```markdown
# PJDBC

Transform, route, and federate SQL without changing your code.

PJDBC is a JDBC proxy framework that lets you intercept and modify database
operations through composable URL-based drivers. Unlike general-purpose
middleware, PJDBC focuses on capabilities that don't have better alternatives:

- **SQL Transformation**: Rewrite queries on the fly
- **Multi-Database Routing**: Federate queries across heterogeneous databases
- **Write Replication**: Mirror writes to multiple targets
- **Access Control**: Enforce read-only, schema validation, data masking
- **Testing Utilities**: Mock databases, inject faults, benchmark

## What PJDBC Is NOT

PJDBC intentionally excludes:
- **Caching** - Use Hibernate L2 cache, Spring Cache, or application-level caching
- **Connection Pooling** - Use HikariCP, c3p0, or your framework's pooling
- **Logging/Metrics/Tracing** - Use p6spy, OpenTelemetry, Micrometer
- **Load Balancing** - Use ProxySQL, PgPool, HAProxy, or database-native solutions

These concerns are better served by purpose-built tools.

## Quick Start

```java
// Transform SQL on the fly
String url = "jdbc:filter[transformer=com.example.MyTransformer]:jdbc:postgresql://localhost/db";

// Federate across multiple databases
String url = "jdbc:federating[urls=jdbc:postgresql://a/db|jdbc:mysql://b/db]:";

// Replicate writes to multiple targets
String url = "jdbc:tee[urls=jdbc:postgresql://primary/db|jdbc:postgresql://replica/db]:jdbc:postgresql://primary/db";

// Enforce read-only access
String url = "jdbc:readonly:jdbc:postgresql://localhost/db";
```

## Drivers

### SQL Transformation & Routing
- `filter` - Transform SQL via custom JdbcTransformer implementation
- `federating` - Query multiple databases as one (terminal driver)
- `tee` - Replicate operations to multiple databases
- `usermap` - Map application credentials to database credentials

### Access Control
- `readonly` - Block all DML/DDL operations
- `schema` - Whitelist/blacklist table and column access
- `mask` - Mask sensitive columns in result sets

### Testing & Development
- `mock` - In-memory database mock
- `sink` - Discard all operations (for benchmarking)
- `chaos` - Inject failures for resilience testing

### Resilience
- `retry` - Retry on transient failures (⚠️ use only for idempotent operations)
- `circuitbreaker` - Circuit breaker pattern
- `timeout` - Enforce query timeouts

### Foundation
- `cat` - Pass-through with no modifications
```

### CLAUDE.md - Update for AI Agents

Focus on:
- Which drivers to recommend for which use cases
- Explicit warnings about composition order
- Clear "don't use PJDBC for X" guidance

---

## Phase 5: Update pom.xml

### Version Bump
```xml
<version>2.0.0</version>
```

### Updated Description
```xml
<description>JDBC proxy framework for SQL transformation, routing, and testing</description>
```

### Simplified Dependencies
```xml
<dependencies>
  <!-- Test dependencies only - no runtime dependencies! -->
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.4.240</version>
    <scope>test</scope>
  </dependency>
  <!-- ... other test dependencies ... -->
</dependencies>
```

### Remove Fast Profile Exclusions
The `fast` profile exclusions for Redis/Memcached/Hazelcast tests become unnecessary.

---

## Phase 6: Update Capability Annotations

Review remaining drivers and update their `@DriverCapability` annotations:
- Remove obsolete capability tags (e.g., `caching`)
- Update descriptions to reflect new positioning
- Ensure side effects are accurately documented

---

## Phase 7: Consider New Capabilities

With the reduced scope, consider enhancing the core value drivers:

### FilterDriver Enhancements
- Ship example transformers (SQL dialect translation, query rewriting)
- Add `transformerConfig` parameter for transformer-specific settings

### FederatingDriver Enhancements
- Document limitations clearly (no cross-database transactions)
- Add query routing hints

### Testing Driver Enhancements
- MockDriver: Add support for schema definition via parameters
- ChaosDriver: Add more failure modes (latency injection, partial failures)

---

## Execution Checklist

### Pre-flight
- [ ] Create `v1.7.0` tag for last pre-rescope version
- [ ] Create `rescope` branch

### Phase 1: Deletions
- [ ] Delete caching drivers and tests (8 files)
- [ ] Delete caching utilities (3 files)
- [ ] Delete pooling drivers and tests (3 files)
- [ ] Delete observability drivers and tests (7 files)
- [ ] Delete infrastructure drivers and tests (4 files)
- [ ] Update META-INF/services/java.sql.Driver
- [ ] Remove dependencies from pom.xml
- [ ] Run `mvn test` - verify build still works

### Phase 2: Documentation
- [ ] Add idempotency warnings to RetryDriver
- [ ] Update README.md
- [ ] Update CLAUDE.md

### Phase 3: Project Metadata
- [ ] Bump version to 2.0.0
- [ ] Update pom.xml description
- [ ] Update any badges/shields in README

### Phase 4: Verification
- [ ] Run full test suite
- [ ] Verify capability manifest generation
- [ ] Test CLI with new driver set
- [ ] Review generated javadocs

### Phase 5: Release
- [ ] Merge to main
- [ ] Tag v2.0.0
- [ ] Update release notes with breaking changes
- [ ] Publish to Maven Central

---

## Breaking Changes Summary (for Release Notes)

### Removed Drivers
- `CachingDriver`, `RedisCachingDriver`, `MemcachedCachingDriver`, `HazelcastCachingDriver`
- `PoolDriver`, `HikariPoolDriver`
- `LogDriver`, `MetricsDriver`, `TracingDriver`, `AuditDriver`
- `LoadBalancingDriver`, `RateLimitDriver`

### Removed Dependencies
- HikariCP
- Jedis (Redis)
- Spymemcached
- Hazelcast

### Migration Guide
| Old Driver | Recommended Alternative |
|------------|------------------------|
| CachingDriver | Hibernate L2 cache, Spring Cache |
| HikariPoolDriver | Use HikariCP directly |
| LogDriver | p6spy, log4jdbc |
| MetricsDriver | Micrometer JDBC instrumentation |
| TracingDriver | OpenTelemetry JDBC instrumentation |
| LoadBalancingDriver | ProxySQL, PgPool, HAProxy |

---

## File Count Summary

| Category | Before | After | Removed |
|----------|--------|-------|---------|
| Main drivers | 29 | 14 | 15 |
| Test files | 24+ | ~10 | 14+ |
| Dependencies | 4 runtime | 0 runtime | 4 |

The project becomes **zero runtime dependencies** (test-only), which is a significant improvement for library consumers.
