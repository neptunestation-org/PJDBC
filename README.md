# PJDBC - Proxying JDBC Driver

[![CI](https://github.com/neptunestation-org/PJDBC/actions/workflows/ci.yml/badge.svg)](https://github.com/neptunestation-org/PJDBC/actions/workflows/ci.yml)

PJDBC is a pluggable JDBC driver framework that enables intercepting, transforming, and extending database operations through composable proxy drivers.

## Features

- **Chainable Drivers**: Stack multiple proxy drivers to build complex pipelines
- **SQL Transformation**: Modify SQL statements before execution
- **Connection Pooling**: Built-in connection pool driver
- **Query Logging**: Log all SQL statements via Java logging
- **User Mapping**: Map application users to database credentials
- **Extensible**: Create custom drivers by extending base classes

## Requirements

- Java 21 or higher
- Maven 3.6+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>org.pjdbc</groupId>
  <artifactId>PJDBC</artifactId>
  <version>1.3.0</version>
</dependency>
```

## Quick Start

```java
// Chain a logging driver with your actual database driver
Connection conn = DriverManager.getConnection(
    "jdbc:log:jdbc:postgresql://localhost/mydb",
    props
);
```

PJDBC drivers are chained by nesting JDBC URLs. Each proxy driver handles its prefix and forwards to the next driver in the chain.

## Available Drivers

### CatDriver (`jdbc:cat:...`)

Pass-through driver that forwards all calls unchanged. Useful as a base for custom drivers.

```java
Connection conn = DriverManager.getConnection("jdbc:cat:jdbc:postgresql://localhost/mydb");
```

### LogDriver (`jdbc:log:...`)

Logs all SQL statements using `java.util.logging`. The logger name is derived from the underlying connection URL.

```java
Connection conn = DriverManager.getConnection("jdbc:log:jdbc:postgresql://localhost/mydb");
// All SQL statements are now logged
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

### PoolDriver (`jdbc:pool:...`)

Connection pooling driver with configurable parameters:

```java
// Basic usage
Connection conn = DriverManager.getConnection("jdbc:pool:jdbc:postgresql://localhost/mydb");

// With configuration
Connection conn = DriverManager.getConnection(
    "jdbc:pool:jdbc:postgresql://localhost/mydb?min=5&max=20&timeout=5000"
);
```

Parameters:
- `min`: Minimum pool size (default: 0)
- `max`: Maximum pool size (default: unlimited)
- `timeout`: Connection acquisition timeout in milliseconds (default: 1000)

### HikariPoolDriver (`jdbc:hikaricp:...`)

A more advanced connection pooling driver that uses HikariCP.

```java
// Basic usage
Connection conn = DriverManager.getConnection("jdbc:hikaricp:jdbc:postgresql://localhost/mydb");

// With configuration
Connection conn = DriverManager.getConnection(
    "jdbc:hikaricp:jdbc:postgresql://localhost/mydb?maximumPoolSize=10"
);
```

All HikariCP configuration parameters can be passed in the URL query string.

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

### CachingDriver (`jdbc:cache:...`)

Caches SELECT query results in memory to reduce database load.

```java
// Basic caching (60s TTL, 1000 max entries)
Connection conn = DriverManager.getConnection(
    "jdbc:cache:jdbc:postgresql://localhost/mydb"
);

// Custom TTL and size
Connection conn = DriverManager.getConnection(
    "jdbc:cache[ttl=300,maxSize=5000]:jdbc:postgresql://localhost/mydb"
);

// Access cache statistics
QueryCache cache = CachingDriver.getCache(conn);
double hitRatio = cache.getHitRatio();
```

Parameters:
- `ttl`: Time-to-live in seconds (default: 60)
- `maxSize`: Maximum cached queries with LRU eviction (default: 1000)
- `invalidateOnWrite`: Clear cache on writes (default: true)
- `enabled`: Enable caching (default: true)

### RedisCachingDriver (`jdbc:rediscache:...`)

Caches SELECT query results in Redis for distributed caching across multiple application instances.

```java
// Basic Redis caching (localhost:6379)
Connection conn = DriverManager.getConnection(
    "jdbc:rediscache:jdbc:postgresql://localhost/mydb"
);

// Custom Redis host and TTL
Connection conn = DriverManager.getConnection(
    "jdbc:rediscache[host=redis.example.com,ttl=300]:jdbc:postgresql://localhost/mydb"
);

// With authentication and key prefix
Connection conn = DriverManager.getConnection(
    "jdbc:rediscache[host=redis.example.com,password=secret,keyPrefix=myapp:]:jdbc:postgresql://localhost/mydb"
);

// Access cache statistics
RedisQueryCache cache = RedisCachingDriver.getCache(conn);
double hitRatio = cache.getHitRatio();
```

Parameters:
- `host`: Redis server hostname (default: localhost)
- `port`: Redis server port (default: 6379)
- `password`: Redis password (default: none)
- `database`: Redis database number (default: 0)
- `keyPrefix`: Prefix for cache keys (default: "pjdbc:")
- `ttl`: Time-to-live in seconds (default: 60)
- `maxPoolSize`: Maximum connections in pool (default: 8)
- `invalidateOnWrite`: Clear cache on writes (default: true)
- `enabled`: Enable caching (default: true)

**Note:** Requires the `jedis` dependency (included as optional).

### MemcachedCachingDriver (`jdbc:memcache:...`)

Caches SELECT query results in Memcached for distributed caching across multiple application instances.

```java
// Basic Memcached caching (localhost:11211)
Connection conn = DriverManager.getConnection(
    "jdbc:memcache:jdbc:postgresql://localhost/mydb"
);

// Multiple Memcached servers
Connection conn = DriverManager.getConnection(
    "jdbc:memcache[servers=cache1:11211;cache2:11211]:jdbc:postgresql://localhost/mydb"
);

// With custom TTL and key prefix
Connection conn = DriverManager.getConnection(
    "jdbc:memcache[ttl=300,keyPrefix=myapp:]:jdbc:postgresql://localhost/mydb"
);

// Access cache statistics
MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
double hitRatio = cache.getHitRatio();
```

Parameters:
- `servers`: Semicolon-separated list of host:port (default: localhost:11211)
- `keyPrefix`: Prefix for cache keys (default: "pjdbc:")
- `ttl`: Time-to-live in seconds (default: 60)
- `invalidateOnWrite`: Clear cache on writes (default: true)
- `enabled`: Enable caching (default: true)

**Note:** Requires the `spymemcached` dependency (included as optional).

### HazelcastCachingDriver (`jdbc:hazelcast:...`)

Caches SELECT query results in Hazelcast for distributed caching with automatic cluster discovery and replication.

```java
// Basic Hazelcast caching (embedded mode)
Connection conn = DriverManager.getConnection(
    "jdbc:hazelcast:jdbc:postgresql://localhost/mydb"
);

// Client mode connecting to existing cluster
Connection conn = DriverManager.getConnection(
    "jdbc:hazelcast[mode=client,members=hz1:5701;hz2:5701]:jdbc:postgresql://localhost/mydb"
);

// Custom cluster name and map
Connection conn = DriverManager.getConnection(
    "jdbc:hazelcast[clusterName=my-cluster,mapName=query_cache]:jdbc:postgresql://localhost/mydb"
);

// With TTL and max idle time
Connection conn = DriverManager.getConnection(
    "jdbc:hazelcast[ttl=300,maxIdle=60]:jdbc:postgresql://localhost/mydb"
);

// Access cache statistics
HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
double hitRatio = cache.getHitRatio();
```

Parameters:
- `mode`: "embedded" (default) or "client" for connecting to external cluster
- `clusterName`: Hazelcast cluster name (default: "pjdbc-cache")
- `members`: Semicolon-separated member addresses (default: "127.0.0.1:5701")
- `mapName`: IMap name for caching (default: "pjdbc_query_cache")
- `ttl`: Time-to-live in seconds (default: 60)
- `maxIdle`: Maximum idle time in seconds, 0 to disable (default: 0)
- `invalidateOnWrite`: Clear cache on writes (default: true)
- `enabled`: Enable caching (default: true)

**Note:** Requires the `hazelcast` dependency (included as optional). Call `HazelcastCachingDriver.shutdownAll()` on application shutdown to clean up Hazelcast instances.

### TracingDriver (`jdbc:trace:...`)

Provides distributed tracing for JDBC operations. Useful for observability in microservices architectures.

```java
// Basic tracing
Connection conn = DriverManager.getConnection(
    "jdbc:trace:jdbc:postgresql://localhost/mydb"
);

// Custom span prefix
Connection conn = DriverManager.getConnection(
    "jdbc:trace[spanPrefix=sql.]:jdbc:postgresql://localhost/mydb"
);

// Access spans for testing
List<SpanData> spans = TracingDriver.getDefaultTracer().getSpans();

// Register custom tracer (e.g., OpenTelemetry)
TracingDriver.setTracer("otel", myOpenTelemetryTracer);
Connection conn = DriverManager.getConnection(
    "jdbc:trace[tracerName=otel]:jdbc:postgresql://localhost/mydb"
);
```

Parameters:
- `tracerName`: Registered tracer name (default: jdbc)
- `spanPrefix`: Prefix for span names (default: db.)
- `includeSql`: Include SQL in spans (default: true)
- `includeParams`: Include parameter values (default: false, for security)
- `includeRowCount`: Include row counts (default: true)

### MetricsDriver (`jdbc:metrics:...`)

Collects performance metrics for JDBC operations including query counts, timing, error rates, and per-operation-type statistics.

```java
// Basic metrics collection
Connection conn = DriverManager.getConnection(
    "jdbc:metrics:jdbc:postgresql://localhost/mydb"
);

// Custom slow query threshold
Connection conn = DriverManager.getConnection(
    "jdbc:metrics[slowThreshold=500]:jdbc:postgresql://localhost/mydb"
);

// Access metrics
MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);
System.out.println("Queries: " + metrics.getTotalQueries());
System.out.println("Avg time: " + metrics.getAvgTimeMs() + "ms");
System.out.println("Error rate: " + metrics.getErrorRate());

// Global metrics across all connections
MetricsDriver.Metrics global = MetricsDriver.getGlobalMetrics();
System.out.println("Active connections: " + global.getActiveConnections());
```

Parameters:
- `enabled`: Enable metrics collection (default: true)
- `slowThreshold`: Threshold in ms for slow query detection (default: 1000)
- `trackByType`: Track metrics by operation type (default: true)

Metrics include: total operations, queries, updates, errors, slow queries, timing statistics (min/max/avg), rows affected, and per-type breakdowns (SELECT, INSERT, UPDATE, DELETE).

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

## Chaining Drivers

Drivers can be composed by nesting URLs:

```java
// Pool -> Log -> Actual Database
Connection conn = DriverManager.getConnection(
    "jdbc:pool:jdbc:log:jdbc:postgresql://localhost/mydb"
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
      "name": "CachingDriver",
      "prefix": "cache",
      "class": "org.pjdbc.drivers.CachingDriver",
      "description": "Caches SELECT query results in memory",
      "capabilities": ["caching"],
      "parameters": [
        {"name": "ttl", "type": "integer", "default": 60, "description": "Time-to-live in seconds"},
        {"name": "maxSize", "type": "integer", "default": 1000, "description": "Maximum cached queries"}
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

// Find all caching drivers
List<DriverCapability> cachingDrivers = caps.findByCapability("caching");
// Returns: [CachingDriver, RedisCachingDriver, MemcachedCachingDriver, HazelcastCachingDriver]

// Get a specific driver by prefix
Optional<DriverCapability> pool = caps.findByPrefix("pool");
pool.ifPresent(d -> {
    System.out.println("URL prefix: " + d.getUrlPrefix());  // jdbc:pool:
    System.out.println("Parameters: " + d.parameters());
});

// Find drivers with external dependencies
List<DriverCapability> withDeps = caps.findWithDependencies();
// Returns: [HikariPoolDriver, RedisCachingDriver, MemcachedCachingDriver, HazelcastCachingDriver]

// Find drivers that make network calls
List<DriverCapability> networkDrivers = caps.findBySideEffect("network");

// Check available capability tags
List<String> allTags = caps.getAllCapabilityTags();
// Returns: [caching, filtering, logging, masking, metrics, passthrough, pooling, ...]
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
| `caching`        | Query result caching  | cache, rediscache, memcache, hazelcast |
| `pooling`        | Connection pooling    | pool, hikaricp                         |
| `logging`        | SQL statement logging | log                                    |
| `tracing`        | Distributed tracing   | trace                                  |
| `metrics`        | Performance metrics   | metrics                                |
| `resilience`     | Fault tolerance       | retry, circuitbreaker, chaos           |
| `security`       | Access control        | readonly, mapuser, mask                |
| `testing`        | Test utilities        | mock, sink, chaos                      |
| `transformation` | SQL modification      | filter                                 |
| `masking`        | Data masking          | mask                                   |

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

## Requirements

- Java 21 or higher
- Maven 3.x

## Author

David A. Ventimiglia <davidaventimiglia@neptunestation.com>
