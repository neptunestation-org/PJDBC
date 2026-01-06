# AI Agent Guide for PJDBC

This guide helps AI agents effectively use PJDBC for database connectivity tasks. PJDBC provides machine-readable metadata for programmatic driver discovery and URL construction.

## Quick Reference

| Task | Approach |
|------|----------|
| Find a driver | Query `pjdbc.capabilities.json` or use `PjdbcCapabilities` API |
| Build a URL | `jdbc:<prefix>[params]:jdbc:<database-url>` |
| Chain drivers | Nest URLs: `jdbc:retry:jdbc:timeout:jdbc:postgresql://...` |
| Validate URL | Use CLI: `java -jar PJDBC.jar validate "<url>"` |

## Issue Tracking

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

---

## Capabilities Manifest

The `pjdbc.capabilities.json` file (in classpath at runtime, or `target/classes/` after build) describes all drivers:

```json
{
  "version": "1.0",
  "drivers": [
    {
      "name": "RetryDriver",
      "prefix": "retry",
      "description": "Automatically retries failed queries on transient errors",
      "capabilities": ["resilience"],
      "parameters": [
        {"name": "maxRetries", "type": "integer", "default": 3, "min": 0}
      ],
      "composable": true,
      "terminal": false
    }
  ]
}
```

### Key Fields

| Field | Meaning |
|-------|---------|
| `prefix` | URL prefix (e.g., `retry` → `jdbc:retry:...`) |
| `capabilities` | Tags for discovery (resilience, security, testing, etc.) |
| `parameters` | Configurable options with types and defaults |
| `composable` | Can be chained with other drivers |
| `terminal` | Does not delegate (e.g., `mock`, `sink`) |

---

## Prompt Templates

### Driver Selection

```
User wants: [describe requirement]

Find appropriate PJDBC driver(s) by:
1. Check capabilities manifest for drivers matching these tags: [resilience|security|testing|transformation|routing]
2. Verify driver parameters meet requirements
3. Check if driver is composable (can chain) or terminal (standalone)

Output: Driver name, prefix, and recommended parameters
```

**Example prompts:**

```
"I need to add automatic retry logic for transient database failures"
→ Use RetryDriver (prefix: retry)
→ Parameters: maxRetries=3, initialDelay=100, backoffMultiplier=2.0

"I need to mask sensitive columns in query results for a dev environment"
→ Use DataMaskingDriver (prefix: mask)
→ Parameters: columns=ssn;credit_card, strategy=PARTIAL

"I need to prevent any write operations on this connection"
→ Use ReadonlyDriver (prefix: readonly)
→ Parameters: (defaults are fine, or set custom message)
```

### URL Construction

```
Given:
- Database URL: [original jdbc url]
- Required drivers: [list of prefixes]
- Parameters: [key=value pairs]

Construct PJDBC URL using pattern:
jdbc:<prefix1>[param1=val1,param2=val2]:jdbc:<prefix2>:jdbc:<database-url>

Rules:
1. Outermost driver processes first (closest to application)
2. Parameters go in brackets after prefix
3. Each driver prefix needs its own jdbc: prefix
4. Terminal drivers must be last
```

**Example:**

```
Database: jdbc:postgresql://localhost:5432/mydb
Drivers: retry, timeout, readonly
Parameters: maxRetries=5, queryTimeout=30

Result:
jdbc:retry[maxRetries=5]:jdbc:timeout[queryTimeout=30]:jdbc:readonly:jdbc:postgresql://localhost:5432/mydb
```

### Chain Order Recommendation

```
For a production database connection, recommend driver order:

1. Resilience (outermost)
   - retry (retries wrap everything)
   - circuitbreaker (fail-fast when backend down)

2. Timeout
   - timeout (individual query limits)

3. Access Control
   - readonly (block writes)
   - schema (table/column restrictions)
   - mask (data masking)

4. Transformation
   - filter (SQL rewriting)

5. Terminal/Database (innermost)
   - postgresql, mysql, etc.
   - OR mock, sink for testing
```

### Troubleshooting

```
User reports: [error message or behavior]

Diagnostic steps:
1. Validate URL syntax: java -jar PJDBC.jar validate "<url>"
2. Check driver chain: java -jar PJDBC.jar chain "<url>"
3. Verify driver is on classpath
4. Check parameter constraints (min/max values)
5. Verify chain order (retry outside timeout, logging outside transformation)

Common issues:
- "No suitable driver": PJDBC jar not on classpath
- Parameter out of range: Check min/max in capabilities
- Timeout includes retry time: Move timeout inside retry
- SQL not logged as expected: Move logger outside PJDBC
```

---

## Code Generation Templates

### Spring Boot Configuration

```java
// Template for application.properties
spring.datasource.url=jdbc:${drivers}:jdbc:${database}://${host}:${port}/${dbname}
spring.datasource.username=${username}
spring.datasource.password=${password}

// Example with retry + timeout
spring.datasource.url=jdbc:retry[maxRetries=3]:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost:5432/mydb
```

### HikariCP with PJDBC

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:${pjdbc_chain}:jdbc:${database_url}");
config.setUsername("${username}");
config.setPassword("${password}");
config.setMaximumPoolSize(${pool_size});
DataSource dataSource = new HikariDataSource(config);
```

### Test Configuration with MockDriver

```java
// Unit test setup
@BeforeEach
void setUp() {
    MockDriver.reset();
    MockDriver.when("SELECT * FROM users")
        .thenReturn(MockResultSet.create()
            .columns("id", "name", "email")
            .row(1, "Alice", "alice@example.com")
            .row(2, "Bob", "bob@example.com")
            .build());
}

@Test
void testUserQuery() throws SQLException {
    Connection conn = DriverManager.getConnection("jdbc:mock:test");
    // ... test code
}
```

### Programmatic Driver Discovery

```java
import org.pjdbc.capabilities.PjdbcCapabilities;
import org.pjdbc.capabilities.DriverCapability;

// Find drivers by capability
PjdbcCapabilities caps = PjdbcCapabilities.load();
List<DriverCapability> resilienceDrivers = caps.findByCapability("resilience");

// Find driver by prefix
Optional<DriverCapability> retry = caps.findByPrefix("retry");

// Build URL programmatically
String url = "jdbc:" + retry.get().prefix() + "[maxRetries=5]:jdbc:postgresql://localhost/mydb";
```

---

## Driver Quick Reference

### Resilience

| Driver | Prefix | Use When |
|--------|--------|----------|
| RetryDriver | `retry` | Transient failures (connection drops, deadlocks) |
| CircuitBreakerDriver | `circuitbreaker` | Prevent cascading failures |
| TimeoutDriver | `timeout` | Enforce query time limits |
| ChaosDriver | `chaos` | Test failure handling |

### Security

| Driver | Prefix | Use When |
|--------|--------|----------|
| ReadonlyDriver | `readonly` | Block write operations |
| SchemaValidationDriver | `schema` | Restrict table/column access |
| DataMaskingDriver | `mask` | Hide sensitive data in results |
| UserMapDriver | `mapuser` | Map app users to DB credentials |

### Testing

| Driver | Prefix | Use When |
|--------|--------|----------|
| MockDriver | `mock` | Unit tests without database |
| SinkDriver | `sink` | Benchmarking, dry runs |
| ChaosDriver | `chaos` | Resilience testing |

### Transformation & Routing

| Driver | Prefix | Use When |
|--------|--------|----------|
| FilterDriver | `filter` | SQL rewriting |
| FederatingDriver | `federate` | Query multiple databases |
| TeeDriver | `tee` | Replicate writes |

---

## Common Patterns

### Production Read Replica

```
Primary (writes):
jdbc:retry[maxRetries=3]:jdbc:timeout[queryTimeout=60]:jdbc:postgresql://primary:5432/db

Replica (reads):
jdbc:retry[maxRetries=3]:jdbc:timeout[queryTimeout=30]:jdbc:readonly:jdbc:postgresql://replica:5432/db
```

### Development with Masked Data

```
jdbc:mask[columns=ssn;credit_card;email,strategy=PARTIAL]:jdbc:postgresql://dev:5432/db
```

### Resilience Testing

```
jdbc:chaos[failureRate=0.1,latency=100]:jdbc:postgresql://test:5432/db
```

### Unit Test Mock

```java
MockDriver.when("SELECT COUNT(*) FROM orders").thenReturn(
    MockResultSet.create().columns("count").row(42).build());
```

---

## CLI Commands for Agents

```bash
# List all available drivers
java -jar PJDBC.jar list

# Show driver details and parameters
java -jar PJDBC.jar show retry

# Validate URL syntax and parameters
java -jar PJDBC.jar validate "jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/db"

# Visualize driver chain
java -jar PJDBC.jar chain "jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/db"

# Test database connectivity
java -jar PJDBC.jar test "jdbc:postgresql://localhost/db" -u user -p pass
```

---

## Decision Tree

```
Need database connectivity?
├── Testing without real DB?
│   └── Use: jdbc:mock:testname
├── Need SQL logging?
│   └── Use p6spy outside PJDBC (see INTEROP.md)
├── Need resilience?
│   ├── Retry on transient failures?
│   │   └── Add: retry[maxRetries=N]
│   ├── Fail fast when backend down?
│   │   └── Add: circuitbreaker[failureThreshold=N]
│   └── Limit query time?
│       └── Add: timeout[queryTimeout=N]
├── Need access control?
│   ├── Block all writes?
│   │   └── Add: readonly
│   ├── Restrict tables/columns?
│   │   └── Add: schema[allowedTables=...] or schema[blockedTables=...]
│   └── Mask sensitive data?
│       └── Add: mask[columns=...]
├── Need SQL transformation?
│   └── Add: filter (with custom JdbcTransformer)
└── Multiple databases?
    ├── Query across DBs?
    │   └── Add: federate
    └── Replicate writes?
        └── Add: tee
```
