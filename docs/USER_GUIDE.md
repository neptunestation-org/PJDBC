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

### CachingDriver (Query Result Caching)

Caches SELECT query results in memory to reduce database load.

**URL Format:** `jdbc:cache[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `ttl` | Time-to-live in seconds for cache entries | 60 |
| `maxSize` | Maximum number of cached queries (LRU eviction) | 1000 |
| `invalidateOnWrite` | Clear cache on INSERT/UPDATE/DELETE | true |
| `enabled` | Enable caching | true |

**Features:**
- Caches SELECT query results in memory
- LRU eviction when maxSize is exceeded
- TTL-based expiration
- Automatic invalidation on write operations
- Cache statistics (hits, misses, evictions, hit ratio)
- Thread-safe implementation
- Parameter-aware caching for PreparedStatements

**Examples:**
```java
// Basic caching with defaults (60s TTL, 1000 max entries)
Connection c = DriverManager.getConnection(
    "jdbc:cache:jdbc:postgresql://localhost/db");

// Custom TTL and max size
Connection c = DriverManager.getConnection(
    "jdbc:cache[ttl=300,maxSize=5000]:jdbc:postgresql://localhost/db");

// Disable invalidation on writes (for read-only workloads)
Connection c = DriverManager.getConnection(
    "jdbc:cache[invalidateOnWrite=false]:jdbc:postgresql://localhost/db");

// Access cache statistics
QueryCache cache = CachingDriver.getCache(conn);
System.out.println("Hit ratio: " + cache.getHitRatio());
System.out.println("Hits: " + cache.getHits());
System.out.println("Misses: " + cache.getMisses());
```

### RedisCachingDriver (Distributed Redis Caching)

Caches SELECT query results in Redis for distributed caching across multiple application instances.

**URL Format:** `jdbc:rediscache[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `host` | Redis server hostname | localhost |
| `port` | Redis server port | 6379 |
| `password` | Redis password | (none) |
| `database` | Redis database number | 0 |
| `keyPrefix` | Prefix for cache keys (namespace isolation) | pjdbc: |
| `ttl` | Time-to-live in seconds for cache entries | 60 |
| `maxPoolSize` | Maximum connections in Redis pool | 8 |
| `invalidateOnWrite` | Clear cache on INSERT/UPDATE/DELETE | true |
| `enabled` | Enable caching | true |

**Features:**
- Distributed caching across multiple application instances
- TTL support via Redis SETEX
- Connection pooling with JedisPool
- Key prefix for namespace isolation
- Cache statistics (hits, misses, hit ratio)
- Thread-safe implementation
- Automatic invalidation on write operations

**Examples:**
```java
// Basic Redis caching (localhost:6379)
Connection c = DriverManager.getConnection(
    "jdbc:rediscache:jdbc:postgresql://localhost/db");

// Remote Redis with custom TTL
Connection c = DriverManager.getConnection(
    "jdbc:rediscache[host=redis.example.com,ttl=300]:jdbc:postgresql://localhost/db");

// With authentication
Connection c = DriverManager.getConnection(
    "jdbc:rediscache[host=redis.example.com,password=secret]:jdbc:postgresql://localhost/db");

// Custom key prefix for namespace isolation
Connection c = DriverManager.getConnection(
    "jdbc:rediscache[keyPrefix=myapp:]:jdbc:postgresql://localhost/db");

// Use different Redis database
Connection c = DriverManager.getConnection(
    "jdbc:rediscache[database=2]:jdbc:postgresql://localhost/db");

// Access cache statistics
RedisQueryCache cache = RedisCachingDriver.getCache(conn);
System.out.println("Hit ratio: " + cache.getHitRatio());
System.out.println("Hits: " + cache.getHits());
System.out.println("Misses: " + cache.getMisses());
```

**Note:** Requires the `jedis` dependency (included as optional in the PJDBC pom.xml).

### MemcachedCachingDriver (Distributed Memcached Caching)

Caches SELECT query results in Memcached for distributed caching across multiple application instances.

**URL Format:** `jdbc:memcache[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `servers` | Semicolon-separated list of host:port servers | localhost:11211 |
| `keyPrefix` | Prefix for cache keys (namespace isolation) | pjdbc: |
| `ttl` | Time-to-live in seconds for cache entries | 60 |
| `invalidateOnWrite` | Clear cache on INSERT/UPDATE/DELETE | true |
| `enabled` | Enable caching | true |

**Features:**
- Distributed caching across multiple application instances
- TTL support via Memcached expiration
- Support for multiple Memcached servers (consistent hashing)
- Key prefix for namespace isolation
- Cache statistics (hits, misses, hit ratio)
- Thread-safe implementation
- Automatic invalidation on write operations

**Examples:**
```java
// Basic Memcached caching (localhost:11211)
Connection c = DriverManager.getConnection(
    "jdbc:memcache:jdbc:postgresql://localhost/db");

// Multiple Memcached servers for high availability
Connection c = DriverManager.getConnection(
    "jdbc:memcache[servers=cache1:11211;cache2:11211;cache3:11211]:jdbc:postgresql://localhost/db");

// Custom TTL
Connection c = DriverManager.getConnection(
    "jdbc:memcache[ttl=300]:jdbc:postgresql://localhost/db");

// Custom key prefix for namespace isolation
Connection c = DriverManager.getConnection(
    "jdbc:memcache[keyPrefix=myapp:]:jdbc:postgresql://localhost/db");

// Access cache statistics
MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
System.out.println("Hit ratio: " + cache.getHitRatio());
System.out.println("Hits: " + cache.getHits());
System.out.println("Misses: " + cache.getMisses());
```

**Note:** Requires the `spymemcached` dependency (included as optional in the PJDBC pom.xml).

### HazelcastCachingDriver (Distributed Hazelcast Caching)

Caches SELECT query results in Hazelcast for distributed caching with automatic cluster discovery and replication.

**URL Format:** `jdbc:hazelcast[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `mode` | "embedded" for local instance or "client" for connecting to cluster | embedded |
| `clusterName` | Hazelcast cluster name | pjdbc-cache |
| `members` | Semicolon-separated list of host:port addresses | 127.0.0.1:5701 |
| `mapName` | IMap name for caching | pjdbc_query_cache |
| `ttl` | Time-to-live in seconds for cache entries | 60 |
| `maxIdle` | Maximum idle time in seconds (0 to disable) | 0 |
| `invalidateOnWrite` | Clear cache on INSERT/UPDATE/DELETE | true |
| `enabled` | Enable caching | true |

**Features:**
- Distributed caching with automatic cluster discovery and replication
- Support for embedded mode (simple setup) and client mode (connect to external cluster)
- TTL and max idle time support via Hazelcast IMap
- Shared Hazelcast instances across connections with same configuration
- Cache statistics (hits, misses, hit ratio)
- Thread-safe implementation
- Automatic invalidation on write operations

**Examples:**
```java
// Basic Hazelcast caching (embedded mode)
Connection c = DriverManager.getConnection(
    "jdbc:hazelcast:jdbc:postgresql://localhost/db");

// Client mode connecting to existing Hazelcast cluster
Connection c = DriverManager.getConnection(
    "jdbc:hazelcast[mode=client,members=hz1:5701;hz2:5701]:jdbc:postgresql://localhost/db");

// Custom cluster name
Connection c = DriverManager.getConnection(
    "jdbc:hazelcast[clusterName=my-app-cache]:jdbc:postgresql://localhost/db");

// Custom map name for isolation
Connection c = DriverManager.getConnection(
    "jdbc:hazelcast[mapName=user_queries]:jdbc:postgresql://localhost/db");

// TTL and max idle time
Connection c = DriverManager.getConnection(
    "jdbc:hazelcast[ttl=300,maxIdle=60]:jdbc:postgresql://localhost/db");

// Access cache statistics
HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
System.out.println("Hit ratio: " + cache.getHitRatio());
System.out.println("Hits: " + cache.getHits());
System.out.println("Misses: " + cache.getMisses());

// Shutdown all Hazelcast instances on application exit
HazelcastCachingDriver.shutdownAll();
```

**Note:** Requires the `hazelcast` dependency (included as optional in the PJDBC pom.xml).

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

### MetricsDriver (Performance Monitoring)

Collects performance metrics for JDBC operations including query counts, timing, and error rates.

**URL Format:** `jdbc:metrics[param=value,...]:target-url`

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `enabled` | Enable metrics collection | true |
| `slowThreshold` | Threshold in ms for slow query detection | 1000 |
| `trackByType` | Track metrics by operation type (SELECT, INSERT, etc.) | true |

**Metrics Available:**
- Total queries, updates, executes, and operations
- Total errors and error rate
- Slow query count and rate
- Timing statistics (min, max, avg, total)
- Rows affected
- Active and total connections (global)
- Per-operation-type metrics (SELECT, INSERT, UPDATE, DELETE, OTHER)

**Examples:**
```java
// Basic metrics collection
Connection c = DriverManager.getConnection(
    "jdbc:metrics:jdbc:postgresql://localhost/db");

// Custom slow query threshold (500ms)
Connection c = DriverManager.getConnection(
    "jdbc:metrics[slowThreshold=500]:jdbc:postgresql://localhost/db");

// Disable metrics temporarily
Connection c = DriverManager.getConnection(
    "jdbc:metrics[enabled=false]:jdbc:postgresql://localhost/db");

// Access connection-specific metrics
MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);
System.out.println("Total queries: " + metrics.getTotalQueries());
System.out.println("Avg time: " + metrics.getAvgTimeMs() + "ms");
System.out.println("Error rate: " + metrics.getErrorRate());

// Access per-type metrics
TypeMetrics selectMetrics = metrics.getTypeMetrics(MetricsDriver.OperationType.SELECT);
System.out.println("SELECT count: " + selectMetrics.getCount());

// Access global metrics (across all connections)
MetricsDriver.Metrics global = MetricsDriver.getGlobalMetrics();
System.out.println("Active connections: " + global.getActiveConnections());
System.out.println("Total connections: " + global.getTotalConnections());

// Reset metrics
metrics.reset();
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
