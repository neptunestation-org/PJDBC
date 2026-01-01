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
