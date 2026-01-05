# PJDBC

**Transform, route, and federate SQL without changing your code.**

[![CI](https://github.com/neptunestation-org/PJDBC/actions/workflows/ci.yml/badge.svg)](https://github.com/neptunestation-org/PJDBC/actions/workflows/ci.yml)
[![JavaDoc](https://img.shields.io/badge/JavaDoc-API-blue)](https://neptunestation-org.github.io/PJDBC/)

PJDBC is a JDBC proxy framework that lets you intercept and modify database operations through composable URL-based drivers. Unlike general-purpose middleware, PJDBC focuses on capabilities that don't have better alternatives:

- **SQL Transformation**: Rewrite queries on the fly
- **Multi-Database Routing**: Federate queries across heterogeneous databases
- **Write Replication**: Mirror writes to multiple targets
- **Access Control**: Enforce read-only, schema validation, data masking
- **Testing Utilities**: Mock databases, inject faults, benchmark

## What PJDBC Is NOT

PJDBC intentionally excludes:

- **Caching** — Use Hibernate L2 cache, Spring Cache, or application-level caching
- **Connection Pooling** — Use HikariCP, c3p0, or your framework's built-in pooling
- **Logging/Metrics/Tracing** — Use p6spy, OpenTelemetry, or Micrometer
- **Load Balancing** — Use ProxySQL, PgPool, HAProxy, or database-native solutions

These concerns are better served by purpose-built tools with established ecosystems.

## Requirements

- Java 17 or higher (uses virtual threads on Java 21+ when available)
- Maven 3.6+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>org.pjdbc</groupId>
  <artifactId>PJDBC</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Quick Start

```java
// Chain a retry driver with your actual database driver
Connection conn = DriverManager.getConnection(
    "jdbc:retry:jdbc:postgresql://localhost/mydb",
    props
);
```

PJDBC drivers are chained by nesting JDBC URLs. Each proxy driver handles its prefix and forwards to the next driver in the chain.

## CLI Tool

PJDBC includes a command-line tool for URL validation and driver discovery.

### Commands

| Command | Description |
|---------|-------------|
| `list` | Show all available drivers |
| `show <prefix>` | Display driver details and parameters |
| `validate <url>` | Parse and validate a PJDBC URL |
| `chain <url>` | Visualize the driver chain |
| `test <url>` | Test database connectivity |

### Usage

```bash
# Via Maven
mvn exec:java -Dexec.args="list"
mvn exec:java -Dexec.args="show retry"
mvn exec:java -Dexec.args='validate "jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/db"'

# Via JAR
java -jar target/PJDBC-2.0.0.jar list
java -jar target/PJDBC-2.0.0.jar chain "jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/db"
```

### Examples

**List all drivers:**
```
$ java -jar target/PJDBC-2.0.0.jar list
Available PJDBC Drivers
=======================

  cat              Pass-through identity driver
  retry            Automatically retries failed queries on transient errors
  timeout          Enforces query timeout limits
  circuitbreaker   Circuit breaker pattern for fault tolerance
  ...

Total: 14 drivers
```

**Show driver details:**
```
$ java -jar target/PJDBC-2.0.0.jar show retry
Driver: retry
=============

Description: Automatically retries failed queries on transient errors
Class:       org.pjdbc.drivers.RetryDriver

Parameters:
-----------

  maxRetries
    Type:        integer
    Description: Maximum retry attempts
    Default:     3
    Min:         0
  ...
```

**Visualize driver chain:**
```
$ java -jar target/PJDBC-2.0.0.jar chain "jdbc:retry[maxRetries=5]:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost/db"
Driver Chain Analysis
=====================

Chain (3 layers):
→ RetryDriver [maxRetries=5]
  └─→ TimeoutDriver [queryTimeout=30]
    └─→ postgresql
```

**Validate URL with error detection:**
```
$ java -jar target/PJDBC-2.0.0.jar validate "jdbc:retry[maxRetries=-5]:jdbc:postgresql://localhost/db"
✓ URL structure is valid
  Driver: RetryDriver
  ✗ Parameter validation failed:
    Parameter 'maxRetries' value -5 is less than minimum 0
```

## Available Drivers

PJDBC 2.0 includes 14 drivers in 5 categories:

### SQL Transformation & Routing (Core Value)

| Driver | Prefix | Purpose |
|--------|--------|---------|
| FilterDriver | `filter` | SQL transformation via JdbcTransformer |
| FederatingDriver | `federate` | Query multiple heterogeneous databases |
| TeeDriver | `tee` | Replicate writes to multiple targets |
| UserMapDriver | `mapuser` | Map application users to DB credentials |

### Access Control & Security

| Driver | Prefix | Purpose |
|--------|--------|---------|
| ReadonlyDriver | `readonly` | Enforce read-only access |
| SchemaValidationDriver | `schema` | Whitelist/blacklist tables and columns |
| DataMaskingDriver | `mask` | Mask sensitive data in results |

### Development & Testing

| Driver | Prefix | Purpose |
|--------|--------|---------|
| MockDriver | `mock` | In-memory mock for testing |
| SinkDriver | `sink` | Discard all operations (benchmarking) |
| ChaosDriver | `chaos` | Fault injection for resilience testing |

### Resilience

| Driver | Prefix | Purpose |
|--------|--------|---------|
| RetryDriver | `retry` | Retry transient failures (⚠️ idempotent ops only) |
| CircuitBreakerDriver | `circuitbreaker` | Circuit breaker pattern |
| TimeoutDriver | `timeout` | Query timeout enforcement |

### Foundation

| Driver | Prefix | Purpose |
|--------|--------|---------|
| CatDriver | `cat` | Pass-through (baseline/testing) |

---

### CatDriver (`jdbc:cat:...`)

Pass-through driver that forwards all calls unchanged. Useful as a base for custom drivers or as an identity element in driver chains.

```java
Connection conn = DriverManager.getConnection("jdbc:cat:jdbc:postgresql://localhost/mydb");
```

### FilterDriver (`jdbc:filter:...`)

Transforms SQL statements using a configurable `JdbcTransformer`. Extend this driver to implement custom SQL rewriting.

```java
FilterDriver driver = new FilterDriver();
driver.setTransformer(new AbstractJdbcTransformer() {
    @Override
    public String transformSql(String sql) {
        return sql.replace("OLD_TABLE", "NEW_TABLE");
    }
});
```

### TeeDriver (`jdbc:tee:...`)

Replicates operations across multiple database connections. Specify two JDBC URLs separated by semicolon.

```java
Connection conn = DriverManager.getConnection(
    "jdbc:tee:jdbc:postgresql://primary/mydb;jdbc:postgresql://replica/mydb"
);
```

### UserMapDriver (`jdbc:mapuser:...`)

Maps application-level usernames to database credentials. Configure mappings in a properties file at `org.pjdbc.UserMapDriver.UserMapFile` on the classpath.

Properties file format:
```properties
appuser1=dbuser1/dbpassword1
appuser2=dbuser2/dbpassword2
```

### SinkDriver (`jdbc:sink:...`)

Discards all SQL operations. Useful for testing or dry-run scenarios.

### MockDriver (`jdbc:mock:...`)

In-memory mock driver for testing. Records all operations for later verification.

```java
Connection conn = DriverManager.getConnection("jdbc:mock:testdb");
// ... perform operations ...
String log = MockDriver.getLog("jdbc:mock:testdb");
```

### ReadonlyDriver (`jdbc:readonly:...`)

Enforces read-only database access by blocking write operations.

```java
// Block all writes (DML and DDL)
Connection conn = DriverManager.getConnection(
    "jdbc:readonly:jdbc:postgresql://localhost/mydb"
);

// Allow DDL but block DML
Connection conn = DriverManager.getConnection(
    "jdbc:readonly[allowDDL=true]:jdbc:postgresql://localhost/mydb"
);
```

Parameters:
- `allowDDL`: Allow DDL statements (CREATE, ALTER, DROP) (default: false)
- `allowDML`: Allow DML statements (INSERT, UPDATE, DELETE) (default: false)
- `message`: Custom error message for blocked operations

### RetryDriver (`jdbc:retry:...`)

Automatically retries failed queries on transient errors like connection failures and deadlocks.

```java
// Basic usage with defaults (3 retries, exponential backoff)
Connection conn = DriverManager.getConnection(
    "jdbc:retry:jdbc:postgresql://localhost/mydb"
);

// Custom retry configuration
Connection conn = DriverManager.getConnection(
    "jdbc:retry[maxRetries=5,initialDelay=200,maxDelay=10000]:jdbc:postgresql://localhost/mydb"
);
```

Parameters:
- `maxRetries`: Maximum retry attempts (default: 3)
- `initialDelay`: Initial delay in ms before first retry (default: 100)
- `maxDelay`: Maximum delay cap in ms (default: 5000)
- `backoffMultiplier`: Multiplier for exponential backoff (default: 2.0)
- `jitter`: Add random jitter to delays: true/false (default: true)
- `retryOnSqlStates`: Semicolon-separated SQL states to retry on (default: transient errors)

Default retryable SQL states: 08001, 08003, 08004, 08006, 08007 (connection errors), 40001, 40P01 (deadlock), 57P01 (admin shutdown), HYT00, HYT01 (timeouts).

### CircuitBreakerDriver (`jdbc:circuitbreaker:...`)

Implements the circuit breaker pattern for fault tolerance. Prevents cascading failures by failing fast when a backend is unavailable.

The circuit breaker has three states:
- **CLOSED**: Normal operation, requests pass through
- **OPEN**: Circuit tripped, requests fail immediately without hitting the database
- **HALF_OPEN**: Testing state, allows limited requests to check if backend recovered

```java
// Basic circuit breaker (5 failures to open, 30s reset timeout)
Connection conn = DriverManager.getConnection(
    "jdbc:circuitbreaker:jdbc:postgresql://localhost/mydb"
);

// Custom thresholds
Connection conn = DriverManager.getConnection(
    "jdbc:circuitbreaker[failureThreshold=3,resetTimeout=60000]:jdbc:postgresql://localhost/mydb"
);

// Named circuit breaker for monitoring
Connection conn = DriverManager.getConnection(
    "jdbc:circuitbreaker[name=primary-db,failureThreshold=10]:jdbc:postgresql://localhost/mydb"
);

// Access circuit breaker state
CircuitBreakerDriver.CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);
System.out.println("State: " + cb.getState());  // CLOSED, OPEN, or HALF_OPEN
System.out.println("Failures: " + cb.getFailureCount() + "/" + cb.getFailureThreshold());
System.out.println("Total requests: " + cb.getTotalRequests());
System.out.println("Total rejections: " + cb.getTotalRejections());
```

Parameters:
- `name`: Circuit breaker name for monitoring (default: "default")
- `failureThreshold`: Consecutive failures before opening circuit (default: 5)
- `successThreshold`: Consecutive successes in half-open to close circuit (default: 1)
- `resetTimeout`: Time in ms before open -> half-open transition (default: 30000)

State transitions:
- CLOSED → OPEN: After `failureThreshold` consecutive failures
- OPEN → HALF_OPEN: After `resetTimeout` milliseconds
- HALF_OPEN → CLOSED: After `successThreshold` consecutive successes
- HALF_OPEN → OPEN: On any failure

### TimeoutDriver (`jdbc:timeout:...`)

Enforces query timeout limits on all statements. Uses JDBC's `Statement.setQueryTimeout()` to limit query execution time.

```java
// Basic timeout (30 second default)
Connection conn = DriverManager.getConnection(
    "jdbc:timeout:jdbc:postgresql://localhost/mydb"
);

// Custom timeout (60 seconds)
Connection conn = DriverManager.getConnection(
    "jdbc:timeout[queryTimeout=60]:jdbc:postgresql://localhost/mydb"
);

// No timeout (0 = unlimited)
Connection conn = DriverManager.getConnection(
    "jdbc:timeout[queryTimeout=0]:jdbc:postgresql://localhost/mydb"
);

// Override timeout on specific statement
try (Statement stmt = conn.createStatement()) {
    stmt.setQueryTimeout(120);  // Override to 120 seconds for this statement
    stmt.executeQuery("SELECT * FROM large_table");
}

// Access timeout configuration
TimeoutDriver.TimeoutConfig config = TimeoutDriver.getTimeoutConfig(conn);
System.out.println("Query timeout: " + config.getQueryTimeout() + "s");
```

Parameters:
- `queryTimeout`: Query timeout in seconds (default: 30, 0 = no timeout)
- `cancelOnTimeout`: Whether to attempt statement cancellation on timeout (default: true)

Notes:
- The timeout is applied to all statements created from the connection
- Individual statements can override the timeout using `setQueryTimeout()`
- Actual timeout behavior depends on the underlying JDBC driver and database

### SchemaValidationDriver (`jdbc:schema:...`)

Validates SQL statements against a defined schema. Prevents access to unauthorized tables or columns using whitelist, blacklist, or database metadata validation.

```java
// Whitelist mode - only allow specific tables
Connection conn = DriverManager.getConnection(
    "jdbc:schema[allowedTables=users;orders;products]:jdbc:postgresql://localhost/mydb"
);

// Blacklist mode - block specific sensitive tables
Connection conn = DriverManager.getConnection(
    "jdbc:schema[blockedTables=audit_log;secrets,mode=blacklist]:jdbc:postgresql://localhost/mydb"
);

// Block access to sensitive columns
Connection conn = DriverManager.getConnection(
    "jdbc:schema[blockedColumns=ssn;credit_card;password]:jdbc:postgresql://localhost/mydb"
);

// Metadata mode - validate against actual database schema
Connection conn = DriverManager.getConnection(
    "jdbc:schema[mode=metadata,schemaPattern=public]:jdbc:postgresql://localhost/mydb"
);

// Access schema configuration programmatically
SchemaValidationDriver.SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
config.addBlockedTable("temp_data");  // Block dynamically
config.addBlockedColumn("api_key");   // Block column dynamically
```

Parameters:
- `allowedTables`: Semicolon-separated allowed table names (whitelist mode)
- `blockedTables`: Semicolon-separated blocked table names (blacklist mode)
- `allowedColumns`: Semicolon-separated allowed column patterns
- `blockedColumns`: Semicolon-separated blocked column patterns
- `mode`: Validation mode (default: "whitelist")
  - `whitelist`: Only allow explicitly listed tables
  - `blacklist`: Block explicitly listed tables, allow others
  - `metadata`: Load allowed tables from database metadata
- `caseSensitive`: Case-sensitive matching (default: false)
- `message`: Custom error message prefix
- `loadFromDb`: Load tables from metadata at connection time (default: false)
- `schemaPattern`: Schema pattern for metadata loading
- `tableTypes`: Table types for metadata (default: "TABLE;VIEW")

Notes:
- In whitelist mode with empty allowedTables, all tables are allowed
- Column blocking works regardless of validation mode
- Validates all tables in JOIN queries
- PreparedStatement SQL is validated at prepare time

### ChaosDriver (`jdbc:chaos:...`)

Injects configurable failures and latency for resilience testing. Use this driver to test how your application handles database errors, slow queries, and connection drops.

```java
// 10% failure rate
Connection conn = DriverManager.getConnection(
    "jdbc:chaos[failureRate=0.1]:jdbc:postgresql://localhost/mydb"
);

// 100ms latency with 50ms variance
Connection conn = DriverManager.getConnection(
    "jdbc:chaos[latency=100,latencyVariance=50]:jdbc:postgresql://localhost/mydb"
);

// Slow result set iteration (50ms per row)
Connection conn = DriverManager.getConnection(
    "jdbc:chaos[resultSetLatency=50]:jdbc:postgresql://localhost/mydb"
);
```

Parameters:
- `failureRate`: Probability (0.0-1.0) of throwing SQLException per query (default: 0.0)
- `latency`: Fixed delay in milliseconds before each query (default: 0)
- `latencyVariance`: Random additional delay up to this value in ms (default: 0)
- `connectionDropRate`: Probability (0.0-1.0) of closing connection unexpectedly (default: 0.0)
- `resultSetLatency`: Delay in ms for each ResultSet.next() call (default: 0)
- `exceptionMessage`: Custom exception message (default: "ChaosDriver: Induced failure")

### DataMaskingDriver (`jdbc:mask:...`)

Masks sensitive data in query results on-the-fly. Useful for data privacy, development/testing with production data, or compliance requirements.

```java
// Mask SSN and credit card columns (show last 4 digits)
Connection conn = DriverManager.getConnection(
    "jdbc:mask[columns=ssn;credit_card]:jdbc:postgresql://localhost/mydb"
);

// Redact all password fields
Connection conn = DriverManager.getConnection(
    "jdbc:mask[columns=password;secret,strategy=REDACT]:jdbc:postgresql://localhost/mydb"
);

// Mask emails preserving domain
Connection conn = DriverManager.getConnection(
    "jdbc:mask[columns=.*email.*,strategy=EMAIL]:jdbc:postgresql://localhost/mydb"
);
```

Parameters:
- `columns`: Semicolon-separated column name patterns (regex) to mask
- `strategy`: Masking strategy (default: PARTIAL)
  - `FULL`: Replace entire value with mask chars (e.g., "********")
  - `PARTIAL`: Show first/last N characters (e.g., "****1234")
  - `EMAIL`: Preserve first char and domain (e.g., "j***@example.com")
  - `REDACT`: Replace with "[REDACTED]"
  - `HASH`: Replace with hash prefix (e.g., "a1b2c3d4...")
- `mask`: Mask character (default: *)
- `showFirst`: Characters to show at start for PARTIAL (default: 0)
- `showLast`: Characters to show at end for PARTIAL (default: 4)

### FederatingDriver (`jdbc:federate:...`)

Routes queries across multiple database connections based on table names or query patterns. Useful for sharding, multi-tenancy, or accessing data spread across multiple databases.

```java
// Route based on table prefix
Connection conn = DriverManager.getConnection(
    "jdbc:federate[routing=prefix]:jdbc:postgresql://db1/mydb;jdbc:postgresql://db2/mydb"
);
```

## Chaining Drivers

Drivers can be composed by nesting URLs:

```java
// Retry -> Timeout -> Actual Database
Connection conn = DriverManager.getConnection(
    "jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/mydb"
);
```

## Agent-First Usage

PJDBC includes a capabilities manifest and introspection API designed for AI agents and automated tooling. Agents can programmatically discover available drivers, their parameters, and capabilities without parsing documentation.

### Capabilities Manifest

The `pjdbc.capabilities.json` manifest describes all drivers in a machine-readable format:

```json
{
  "version": "1.0",
  "drivers": [
    {
      "name": "RetryDriver",
      "prefix": "retry",
      "class": "org.pjdbc.drivers.RetryDriver",
      "description": "Automatically retries failed queries on transient errors",
      "capabilities": ["resilience"],
      "parameters": [
        {"name": "maxRetries", "type": "integer", "default": 3, "description": "Maximum retry attempts"},
        {"name": "initialDelay", "type": "integer", "default": 100, "description": "Initial delay in ms"}
      ],
      "sideEffects": {"stateful": true}
    }
  ]
}
```

### Runtime Introspection API

Use `PjdbcCapabilities` to query driver metadata at runtime:

```java
import org.pjdbc.capabilities.PjdbcCapabilities;
import org.pjdbc.capabilities.DriverCapability;

// Load capabilities (cached singleton)
PjdbcCapabilities caps = PjdbcCapabilities.load();

// Find all resilience drivers
List<DriverCapability> resilienceDrivers = caps.findByCapability("resilience");
// Returns: [RetryDriver, TimeoutDriver, CircuitBreakerDriver, ChaosDriver]

// Get a specific driver by prefix
Optional<DriverCapability> retry = caps.findByPrefix("retry");
retry.ifPresent(d -> {
    System.out.println("URL prefix: " + d.getUrlPrefix());  // jdbc:retry:
    System.out.println("Parameters: " + d.parameters());
});

// Find drivers that make network calls
List<DriverCapability> networkDrivers = caps.findBySideEffect("network");

// Check available capability tags
List<String> allTags = caps.getAllCapabilityTags();
// Returns: [masking, passthrough, resilience, routing, security, testing, transformation, validation]
```

### Agent URL Construction

Agents can construct valid JDBC URLs programmatically:

```java
DriverCapability driver = caps.findByPrefix("retry").orElseThrow();

// Build URL with parameters
StringBuilder url = new StringBuilder("jdbc:");
url.append(driver.prefix());
url.append("[");
url.append("maxRetries=5,initialDelay=200");
url.append("]:");
url.append("jdbc:postgresql://localhost/mydb");

// Result: jdbc:retry[maxRetries=5,initialDelay=200]:jdbc:postgresql://localhost/mydb
```

### Capability Tags

Drivers are tagged with capabilities for easy discovery:

| Tag              | Description           | Drivers                                |
|------------------|-----------------------|----------------------------------------|
| `resilience`     | Fault tolerance       | retry, circuitbreaker, timeout, chaos  |
| `security`       | Access control        | readonly, mapuser, mask, schema        |
| `testing`        | Test utilities        | mock, sink, chaos                      |
| `validation`     | Schema validation     | schema                                 |
| `transformation` | SQL modification      | filter                                 |
| `masking`        | Data masking          | mask                                   |
| `routing`        | Query routing         | federate, tee                          |
| `passthrough`    | Identity/passthrough  | cat                                    |

## Creating Custom Drivers

Extend `AbstractProxyDriver` to create custom proxy drivers:

```java
public class MyDriver extends AbstractProxyDriver {
    static {
        try {
            DriverManager.registerDriver(new MyDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "mydriver".equals(subprotocol);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn)
            throws SQLException {
        // Custom statement handling
        return new AbstractStatement(delegate, conn) {
            @Override
            public ResultSet executeQuery(String sql) throws SQLException {
                // Custom logic here
                return super.executeQuery(sql);
            }
        };
    }
}
```

### Annotating Custom Drivers

Annotate your driver class to include it in the capabilities manifest. The annotation processor generates `pjdbc.capabilities.json` at compile time.

#### Available Annotations

| Annotation | Purpose |
|------------|---------|
| `@DriverCapability` | **Required.** Declares driver prefix, description, and capability tags |
| `@DriverParameter` | Declares URL parameters the driver accepts (repeatable) |
| `@DriverDependency` | Declares external library dependencies (repeatable) |
| `@DriverSideEffects` | Declares side effects (stateful, logging, network, etc.) |

#### Complete Example

```java
import org.pjdbc.annotations.*;
import org.pjdbc.annotations.DriverParameter.ParameterType;

@DriverCapability(
    prefix = "mydriver",
    description = "My custom driver with configurable behavior",
    capabilities = {"custom", "transformation"}
)
@DriverParameter(
    name = "timeout",
    type = ParameterType.INTEGER,
    description = "Operation timeout in milliseconds",
    defaultValue = "5000",
    min = 0,
    max = 60000
)
@DriverParameter(
    name = "mode",
    type = ParameterType.STRING,
    description = "Operating mode",
    defaultValue = "normal",
    enumValues = {"normal", "strict", "lenient"}
)
@DriverParameter(
    name = "enabled",
    type = ParameterType.BOOLEAN,
    description = "Enable custom behavior",
    defaultValue = "true"
)
@DriverDependency(
    groupId = "com.example",
    artifactId = "example-lib",
    version = "1.0.0",
    optional = true,
    description = "Required for advanced features"
)
@DriverSideEffects(
    stateful = true,
    logging = true
)
public class MyDriver extends AbstractProxyDriver {
    // ... implementation
}
```

This generates the following manifest entry:

```json
{
  "name": "MyDriver",
  "prefix": "mydriver",
  "class": "com.example.MyDriver",
  "description": "My custom driver with configurable behavior",
  "capabilities": ["custom", "transformation"],
  "parameters": [
    {"name": "timeout", "type": "integer", "description": "Operation timeout in milliseconds", "default": 5000, "min": 0, "max": 60000},
    {"name": "mode", "type": "string", "description": "Operating mode", "default": "normal", "enum": ["normal", "strict", "lenient"]},
    {"name": "enabled", "type": "boolean", "description": "Enable custom behavior", "default": true}
  ],
  "dependencies": [
    {"groupId": "com.example", "artifactId": "example-lib", "version": "1.0.0", "optional": true, "description": "Required for advanced features"}
  ],
  "sideEffects": {"stateful": true, "logging": true},
  "composable": true,
  "terminal": false
}
```

#### @DriverCapability Reference

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `prefix` | String | Yes | - | URL prefix (e.g., "retry" for `jdbc:retry:...`) |
| `description` | String | Yes | - | Human-readable description |
| `capabilities` | String[] | No | `{}` | Capability tags for discovery |
| `name` | String | No | Class name | Override driver name in manifest |
| `composable` | boolean | No | `true` | Can be chained with other drivers |
| `terminal` | boolean | No | `false` | Does not delegate to another driver |

**Validation rules:**
- Prefix must be lowercase alphanumeric, starting with a letter (e.g., `retry`, `pool2`)
- Capability tags must be lowercase with optional hyphens (e.g., `resilience`, `load-balancing`)
- Duplicate prefixes across drivers cause a compilation error

#### @DriverParameter Reference

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `name` | String | Yes | - | Parameter name in URL |
| `type` | ParameterType | No | `STRING` | `STRING`, `INTEGER`, `FLOAT`, or `BOOLEAN` |
| `description` | String | No | `""` | Parameter description |
| `defaultValue` | String | No | `""` | Default value (empty = required) |
| `min` | long | No | `Long.MIN_VALUE` | Minimum for numeric types |
| `max` | long | No | `Long.MAX_VALUE` | Maximum for numeric types |
| `enumValues` | String[] | No | `{}` | Valid values for STRING type |
| `required` | boolean | No | `false` | Parameter is required |

**Validation rules:**
- Parameter names must be unique within a driver
- `min` must not exceed `max`
- Default value must be valid for the declared type
- Default value must be within min/max constraints
- Default value must be in enumValues (if specified)

#### @DriverDependency Reference

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `groupId` | String | Yes | - | Maven group ID |
| `artifactId` | String | Yes | - | Maven artifact ID |
| `version` | String | No | `""` | Version (recommended) |
| `optional` | boolean | No | `false` | Dependency is optional |
| `description` | String | No | `""` | Why this dependency is needed |

#### @DriverSideEffects Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `stateful` | boolean | `false` | Maintains state across calls |
| `logging` | boolean | `false` | Writes to logs |
| `network` | boolean | `false` | Makes network calls |
| `filesystem` | boolean | `false` | Accesses filesystem |
| `metrics` | boolean | `false` | Collects metrics |
| `tracing` | boolean | `false` | Emits trace spans |
| `modifiesQueries` | boolean | `false` | Transforms SQL statements |
| `modifiesResults` | boolean | `false` | Transforms query results |

## JdbcTransformer Interface

For comprehensive input/output transformation, implement `JdbcTransformer`:

```java
public interface JdbcTransformer {
    // Transform SQL before execution
    String transformSql(String sql) throws SQLException;

    // Transform parameters before binding
    Object transformParameter(int index, Object value, int sqlType) throws SQLException;

    // Transform values retrieved from ResultSet
    Object transformResultValue(int columnIndex, String columnName,
                                Object value, int sqlType) throws SQLException;
}
```

## Building

```bash
mvn clean install
```

## Development

### Running Tests

```bash
# Run all tests (~1m 42s)
mvn test

# Run fast tests only (~28s) - excludes container-based integration tests
mvn test -Pfast
```

### Speeding Up Tests

**Testcontainer Reuse**: Enable container reuse to avoid restarting Docker containers between test runs. Add to `~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

This significantly reduces test time when running integration tests repeatedly during development.

### Generating Documentation

PJDBC uses two mechanisms to generate documentation from driver annotations:

**1. Capabilities Manifest** (compile-time)

The `CapabilityProcessor` annotation processor generates `pjdbc.capabilities.json` automatically during compilation. This JSON manifest contains machine-readable metadata for all drivers and is used by the runtime introspection API.

```bash
mvn compile  # Generates target/classes/pjdbc.capabilities.json
```

**2. Enhanced JavaDocs** (javadoc phase)

The `CapabilityDoclet` custom doclet enhances standard JavaDocs by injecting parameter tables into driver class documentation. It reads `@DriverParameter` annotations and generates HTML tables showing each parameter's name, type, default value, constraints, and description.

```bash
mvn javadoc:javadoc  # Generates target/reports/apidocs/ with enhanced docs
mvn javadoc:jar      # Packages enhanced JavaDocs into JAR for deployment
```

The enhanced JavaDoc for each driver class includes a "Driver Parameters" section with:
- URL format example
- Parameter table with type, default, constraints, and description

Both mechanisms read from the same annotations (`@DriverCapability`, `@DriverParameter`, etc.), ensuring documentation stays in sync with the code.

### CI Configuration

The project uses GitHub Actions for CI with the following workflows:

- **CI**: Builds and tests the project
- **SpotBugs**: Static analysis for bug detection
- **OWASP Dependency Check**: Scans dependencies for known vulnerabilities

#### NVD API Key Setup

The OWASP Dependency Check workflow uses the National Vulnerability Database (NVD) API. Without an API key, vulnerability database updates are rate-limited and can take 20+ minutes. With an API key, updates complete in ~4 minutes.

To configure:

1. Request a free API key at https://nvd.nist.gov/developers/request-an-api-key
2. Add the key as a repository secret named `NVD_API_KEY`:
   - Go to Settings → Secrets and variables → Actions → New repository secret
   - Name: `NVD_API_KEY`
   - Value: your API key

## Author

David A. Ventimiglia <davidaventimiglia@neptunestation.com>
