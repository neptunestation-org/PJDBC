# PJDBC User's Guide

PJDBC (Proxying JDBC) is a framework for intercepting and transforming JDBC calls. This guide covers how to use the built-in drivers.

## Quick Start

```java
// Chain a pool driver with your database driver
Connection conn = DriverManager.getConnection(
    "jdbc:pool:jdbc:postgresql://localhost/mydb", props);
```

## Available Drivers

### CatDriver (Passthrough)

Simple passthrough driver that forwards all calls unchanged. Useful as a base for chaining.

```java
Connection c = DriverManager.getConnection("jdbc:cat:jdbc:postgresql://localhost/db");
```

### PoolDriver (Connection Pooling)

Provides connection pooling with configurable parameters.

**URL Format:** `jdbc:pool[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `min` | Minimum pool size | 0 |
| `max` | Maximum pool size | unlimited |
| `timeout` | Wait timeout (ms) | 1000 |

**Examples:**
```java
// Basic pooling with defaults
Connection c = DriverManager.getConnection("jdbc:pool:jdbc:postgresql://localhost/db");

// Configured pool
Connection c = DriverManager.getConnection(
    "jdbc:pool[min=2,max=10,timeout=5000]:jdbc:postgresql://localhost/db");
```

### FilterDriver (SQL Transformation)

Transforms SQL statements before execution using a `JdbcTransformer`.

```java
FilterDriver driver = new FilterDriver();
driver.setTransformer(new AbstractJdbcTransformer() {
    @Override
    public String transformSql(String sql) {
        return sql.toUpperCase(); // Example: uppercase all SQL
    }
});
Connection c = driver.connect("jdbc:filter:jdbc:postgresql://localhost/db", props);
```

### LogDriver (SQL Logging)

Logs all SQL statements using `java.util.logging`.

```java
Connection c = DriverManager.getConnection("jdbc:log:jdbc:postgresql://localhost/db");
```

Configure logging:
```java
Logger.getLogger("jdbc:postgresql://localhost/db").setLevel(Level.INFO);
```

### TeeDriver (Dual Write)

Sends all operations to two databases simultaneously. Useful for migrations or replication.

**URL Format:** `jdbc:tee:url1;url2`

```java
Connection c = DriverManager.getConnection(
    "jdbc:tee:jdbc:postgresql://primary/db;jdbc:postgresql://replica/db");
```

### UserMapDriver (Username Mapping)

Maps usernames to different credentials based on a properties file.

```java
Connection c = DriverManager.getConnection("jdbc:mapuser:jdbc:postgresql://localhost/db", props);
```

Requires a properties file `org.pjdbc.UserMapDriver.UserMapFile` in the classpath.

### ReadonlyDriver (Read-Only Access)

Enforces read-only database access by blocking write operations.

**URL Format:** `jdbc:readonly[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `allowDDL` | Allow DDL statements (CREATE, ALTER, DROP) | false |
| `allowDML` | Allow DML statements (INSERT, UPDATE, DELETE) | false |
| `message` | Custom error message for blocked operations | (default message) |

**Blocked Operations:**
- DML: INSERT, UPDATE, DELETE, MERGE, UPSERT, REPLACE, TRUNCATE
- DDL: CREATE, ALTER, DROP, RENAME
- DCL: GRANT, REVOKE (always blocked)

**Examples:**
```java
// Block all writes
Connection c = DriverManager.getConnection(
    "jdbc:readonly:jdbc:postgresql://localhost/db");

// Allow DDL for schema migrations
Connection c = DriverManager.getConnection(
    "jdbc:readonly[allowDDL=true]:jdbc:postgresql://localhost/db");

// Custom error message
Connection c = DriverManager.getConnection(
    "jdbc:readonly[message=Reporting database is read-only]:jdbc:postgresql://localhost/db");
```

### RetryDriver (Automatic Retries)

Automatically retries failed queries on transient errors.

**URL Format:** `jdbc:retry[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `maxRetries` | Maximum retry attempts | 3 |
| `initialDelay` | Initial delay in ms | 100 |
| `maxDelay` | Maximum delay cap in ms | 5000 |
| `backoffMultiplier` | Multiplier for exponential backoff | 2.0 |
| `jitter` | Add random jitter to delays | true |
| `retryOnSqlStates` | Semicolon-separated SQL states to retry | (transient errors) |

**Default Retryable SQL States:**
- 08001, 08003, 08004, 08006, 08007 - Connection errors
- 40001, 40P01 - Deadlock/serialization failures
- 57P01 - Admin shutdown
- HYT00, HYT01 - Timeout errors

**Examples:**
```java
// Basic usage with defaults
Connection c = DriverManager.getConnection(
    "jdbc:retry:jdbc:postgresql://localhost/db");

// Custom retry settings
Connection c = DriverManager.getConnection(
    "jdbc:retry[maxRetries=5,initialDelay=200,maxDelay=10000]:jdbc:postgresql://localhost/db");

// Custom retryable states (only retry on deadlock)
Connection c = DriverManager.getConnection(
    "jdbc:retry[retryOnSqlStates=40001;40P01]:jdbc:postgresql://localhost/db");

// Combine with other drivers
Connection c = DriverManager.getConnection(
    "jdbc:retry[maxRetries=3]:jdbc:log:jdbc:postgresql://localhost/db");
```

### DataMaskingDriver (Sensitive Data Anonymization)

Masks sensitive data in query results on-the-fly for data privacy and security.

**URL Format:** `jdbc:mask[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `columns` | Semicolon-separated column name patterns (regex) to mask | (none) |
| `strategy` | Masking strategy: FULL, PARTIAL, EMAIL, REDACT, HASH | PARTIAL |
| `mask` | Character to use for masking | * |
| `showLast` | Characters to show at end for PARTIAL strategy | 4 |
| `showFirst` | Characters to show at start for PARTIAL strategy | 0 |

**Masking Strategies:**
- **FULL** - Replace entire value with mask characters (e.g., "********")
- **PARTIAL** - Show first/last N characters (e.g., "****1234")
- **EMAIL** - Mask email preserving first char and domain (e.g., "j***@example.com")
- **REDACT** - Replace with "[REDACTED]"
- **HASH** - Replace with hash prefix (e.g., "a1b2c3d4...")

**Examples:**
```java
// Mask SSN and credit card columns (show last 4 digits)
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=ssn;credit_card,showLast=4]:jdbc:postgresql://localhost/db");

// Full masking for passwords
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=password;secret,strategy=FULL]:jdbc:postgresql://localhost/db");

// Email masking (preserves domain)
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=.*email.*,strategy=EMAIL]:jdbc:postgresql://localhost/db");

// Redact sensitive columns
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=ssn;salary,strategy=REDACT]:jdbc:postgresql://localhost/db");

// Show first and last 4 characters of credit card
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=card_number,showFirst=4,showLast=4]:jdbc:mysql://localhost/db");

// Use X instead of * for masking
Connection c = DriverManager.getConnection(
    "jdbc:mask[columns=ssn,strategy=FULL,mask=X]:jdbc:postgresql://localhost/db");
```

### TracingDriver (Distributed Tracing)

Provides distributed tracing for JDBC operations with pluggable tracer support.

**URL Format:** `jdbc:trace[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `tracerName` | Name of registered tracer to use | jdbc |
| `spanPrefix` | Prefix for span names | db. |
| `includeSql` | Include SQL statement in span attributes | true |
| `includeParams` | Include parameter values (security risk) | false |
| `includeRowCount` | Include row counts in span attributes | true |

**Span Attributes:**
- `db.system` - Always "jdbc"
- `db.operation` - Operation type (query, update, execute, batch, call)
- `db.statement` - SQL statement (if includeSql=true)
- `db.parameters` - Parameter values (if includeParams=true)
- `db.rows_affected` - Row count for updates (if includeRowCount=true)
- `db.batch_size` - Batch size for batch operations
- `error`, `error.type`, `error.message` - On errors

**Examples:**
```java
// Basic tracing
Connection c = DriverManager.getConnection(
    "jdbc:trace:jdbc:postgresql://localhost/db");

// Custom span prefix
Connection c = DriverManager.getConnection(
    "jdbc:trace[spanPrefix=sql.]:jdbc:postgresql://localhost/db");

// Include parameters (use with caution)
Connection c = DriverManager.getConnection(
    "jdbc:trace[includeParams=true]:jdbc:postgresql://localhost/db");

// Disable SQL for security
Connection c = DriverManager.getConnection(
    "jdbc:trace[includeSql=false]:jdbc:postgresql://localhost/db");

// Access spans from default tracer (for testing)
List<SpanData> spans = TracingDriver.getDefaultTracer().getSpans();

// Register custom tracer (e.g., for OpenTelemetry)
TracingDriver.setTracer("otel", myOpenTelemetryTracer);
Connection c = DriverManager.getConnection(
    "jdbc:trace[tracerName=otel]:jdbc:postgresql://localhost/db");
```

### ChaosDriver (Resilience Testing)

Injects failures and latency to test application resilience.

**URL Format:** `jdbc:chaos[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `failureRate` | Probability (0.0-1.0) of throwing SQLException | 0.0 |
| `latency` | Fixed delay in ms before each query | 0 |
| `latencyVariance` | Random additional delay up to this value in ms | 0 |
| `connectionDropRate` | Probability of closing connection unexpectedly | 0.0 |
| `resultSetLatency` | Delay in ms for each ResultSet.next() call | 0 |
| `exceptionMessage` | Custom exception message | "ChaosDriver: Induced failure" |

**Examples:**
```java
// 10% failure rate
Connection c = DriverManager.getConnection(
    "jdbc:chaos[failureRate=0.1]:jdbc:postgresql://localhost/db");

// Simulate slow network (100-200ms latency)
Connection c = DriverManager.getConnection(
    "jdbc:chaos[latency=100,latencyVariance=100]:jdbc:postgresql://localhost/db");

// Slow result iteration for large result sets
Connection c = DriverManager.getConnection(
    "jdbc:chaos[resultSetLatency=10]:jdbc:postgresql://localhost/db");

// Combination for chaos testing
Connection c = DriverManager.getConnection(
    "jdbc:chaos[failureRate=0.05,latency=50,connectionDropRate=0.01]:jdbc:postgresql://localhost/db");
```

## Chaining Drivers

Drivers can be chained together:

```java
// Pool -> Log -> Filter -> PostgreSQL
Connection c = DriverManager.getConnection(
    "jdbc:pool[max=10]:jdbc:log:jdbc:filter:jdbc:postgresql://localhost/db");
```

## Transformation API

The `JdbcTransformer` interface provides hooks for transforming:
- SQL statements (`transformSql`)
- PreparedStatement parameters (`transformParameter`)
- ResultSet values (`transformResultValue`)

```java
public class MyTransformer extends AbstractJdbcTransformer {
    @Override
    public String transformSql(String sql) throws SQLException {
        // Transform SQL before execution
        return sql.replace("OLD_TABLE", "NEW_TABLE");
    }

    @Override
    public Object transformParameter(int index, Object value, int sqlType) throws SQLException {
        // Transform input parameters
        if (value instanceof String) {
            return ((String) value).trim();
        }
        return value;
    }

    @Override
    public Object transformResultValue(int columnIndex, String columnName,
                                        Object value, int sqlType) throws SQLException {
        // Transform output values
        return value;
    }
}
```
