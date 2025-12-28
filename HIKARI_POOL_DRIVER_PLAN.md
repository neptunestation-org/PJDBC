# HikariPoolDriver Implementation Plan

## Executive Summary

This plan outlines the integration of **HikariCP**, the industry-standard Java connection pool library, into the PJDBC framework as a new `HikariPoolDriver`. This will complement the existing rudimentary `PoolDriver` with an enterprise-grade solution.

## Research Findings

### Why HikariCP?

Based on research of Java connection pool libraries:

| Library | Performance | Status | Recommendation |
|---------|-------------|--------|----------------|
| **HikariCP** | Fastest | Active, Spring Boot default | **Best choice** |
| Druid | Excellent | Active (Alibaba) | Good for monitoring-heavy use |
| Apache DBCP2 | Moderate | Active | Legacy compatibility |
| C3P0 | Slower | Maintenance mode | Not recommended for new projects |
| Tomcat JDBC | Good | Active | Tomcat-specific deployments |

**HikariCP advantages:**
- Bytecode-level optimizations for minimal latency
- Excellent defaults (minimal configuration needed)
- Connection leak detection built-in
- Health check support
- Metrics integration (Micrometer, Dropwizard)
- Active development and large community
- Default in Spring Boot since 2.0

### Current PoolDriver Limitations

The existing `PoolDriver` (`src/main/java/org/pjdbc/drivers/PoolDriver.java`):
- Uses simple `LinkedBlockingQueue` for pooling
- No connection validation/health checks
- No leak detection
- No connection lifecycle management (max lifetime, idle timeout)
- No metrics or monitoring
- Basic min/max/timeout only

## Implementation Plan

### Phase 1: Project Setup

#### 1.1 Add HikariCP Dependency

Add to `pom.xml`:

```xml
<dependency>
  <groupId>com.zaxxer</groupId>
  <artifactId>HikariCP</artifactId>
  <version>4.0.3</version> <!-- Java 8 compatible -->
</dependency>
```

Note: Using version 4.0.3 for Java 8 compatibility (project targets Java 1.8). Version 5.x+ requires Java 11+.

### Phase 2: Core Implementation

#### 2.1 Create HikariPoolDriver Class

**File:** `src/main/java/org/pjdbc/drivers/HikariPoolDriver.java`

**Design decisions:**
- Extend `AbstractProxyDriver` to maintain consistency with framework
- Use subprotocol `hikari` (URL: `jdbc:hikari[options]:jdbc:target:...`)
- Map URL parameters to HikariConfig properties
- Manage HikariDataSource instances per unique target URL

**Key components:**

```java
public class HikariPoolDriver extends AbstractProxyDriver {
    // Pool registry: target URL -> HikariDataSource
    private static final ConcurrentHashMap<String, HikariDataSource> pools =
        new ConcurrentHashMap<>();

    // Subprotocol for URL matching
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "hikari".equals(subprotocol);
    }

    // Override connect() to use HikariDataSource
    public Connection connect(String url, Properties info) throws SQLException {
        // Parse URL, get/create pool, return proxied connection
    }
}
```

#### 2.2 Configuration Mapping

Map URL parameters to HikariCP configuration:

| URL Parameter | HikariCP Property | Default | Description |
|---------------|-------------------|---------|-------------|
| `min` | `minimumIdle` | 10 | Minimum idle connections |
| `max` | `maximumPoolSize` | 10 | Maximum pool size |
| `timeout` | `connectionTimeout` | 30000 | Connection wait timeout (ms) |
| `idleTimeout` | `idleTimeout` | 600000 | Idle connection timeout (ms) |
| `maxLifetime` | `maxLifetime` | 1800000 | Max connection lifetime (ms) |
| `leakDetection` | `leakDetectionThreshold` | 0 | Leak detection threshold (ms) |
| `poolName` | `poolName` | auto | Pool identifier for logging |
| `autoCommit` | `autoCommit` | true | Auto-commit behavior |
| `validation` | `connectionTestQuery` | null | Legacy validation query |

**Example URL:**
```
jdbc:hikari[max=20,min=5,timeout=5000,leakDetection=30000]:jdbc:postgresql://localhost/mydb
```

#### 2.3 Pool Lifecycle Management

```java
// Create pool for a target URL
private HikariDataSource createPool(String targetUrl, Properties info,
                                     Map<String, String> params) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(targetUrl);

    // Apply user/password from Properties
    if (info != null) {
        if (info.containsKey("user"))
            config.setUsername(info.getProperty("user"));
        if (info.containsKey("password"))
            config.setPassword(info.getProperty("password"));
    }

    // Apply URL parameters
    applyParameters(config, params);

    return new HikariDataSource(config);
}

// Shutdown hook for graceful pool cleanup
static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        pools.values().forEach(HikariDataSource::close);
    }));
}
```

#### 2.4 Connection Proxying Integration

The HikariPoolDriver must integrate with PJDBC's proxy infrastructure:

```java
@Override
public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) return null;

    String targetUrl = subname(url);
    Map<String, String> params = parseUrl(url).getParameters();

    // Get or create pool
    HikariDataSource pool = pools.computeIfAbsent(targetUrl,
        key -> createPool(key, info, params));

    // Get connection from pool and wrap with proxy
    Connection rawConn = pool.getConnection();
    return proxyConnection(rawConn, url, info, this);
}
```

### Phase 3: Testing

#### 3.1 Unit Tests

**File:** `src/test/java/HikariPoolDriverTest.java`

Test cases:
1. `acceptsURL()` - URL pattern matching
2. `connect()` - Basic connection acquisition
3. `configurationParsing()` - URL parameter parsing
4. `poolReuse()` - Same pool for same target URL
5. `connectionReturn()` - Connections returned to pool on close
6. `poolSizeEnforcement()` - max/min settings honored
7. `timeoutBehavior()` - connection timeout works
8. `driverChaining()` - works with other PJDBC drivers

#### 3.2 Integration Tests (Manual)

- Test with real database (PostgreSQL, MySQL)
- Load testing for pool behavior under stress
- Leak detection verification

### Phase 4: Registration and Documentation

#### 4.1 Service Provider Registration

Add to `src/main/resources/META-INF/services/java.sql.Driver`:
```
org.pjdbc.drivers.HikariPoolDriver
```

#### 4.2 Documentation Updates

Update `docs/USER_GUIDE.md`:
- Add HikariPoolDriver section
- Configuration reference
- Usage examples
- Comparison with basic PoolDriver

Update `README.md`:
- Add HikariCP to feature list
- Note external dependency

## File Changes Summary

| File | Action | Description |
|------|--------|-------------|
| `pom.xml` | Modify | Add HikariCP dependency |
| `src/main/java/org/pjdbc/drivers/HikariPoolDriver.java` | Create | New driver implementation |
| `src/test/java/HikariPoolDriverTest.java` | Create | Unit tests |
| `src/main/resources/META-INF/services/java.sql.Driver` | Modify | Register new driver |
| `docs/USER_GUIDE.md` | Modify | Document new driver |
| `README.md` | Modify | Update feature list |

## Design Considerations

### Why a Separate Driver vs. Enhancing PoolDriver?

1. **Dependency isolation**: Users who don't need enterprise pooling don't need HikariCP dependency
2. **Backward compatibility**: Existing PoolDriver users unaffected
3. **Clear choice**: Users can explicitly choose between lightweight (pool) and enterprise (hikari)
4. **Testing simplicity**: MockDriver-based tests don't require HikariCP

### Alternative Considered: Optional Dependency

Could make HikariCP optional and have PoolDriver use it when available. Rejected because:
- Complicates testing
- Runtime surprises if dependency missing
- Less explicit user choice

### Driver Chaining Compatibility

HikariPoolDriver must work in chains:
```
jdbc:hikari[max=10]:jdbc:log:jdbc:filter:jdbc:postgresql://localhost/db
```

The `subname()` extraction and `proxyConnection()` pattern ensure this works correctly.

## Implementation Order

1. Add HikariCP dependency to pom.xml
2. Create HikariPoolDriver.java with basic connect()
3. Add URL parameter parsing and configuration
4. Implement pool lifecycle management
5. Write unit tests
6. Register in service provider file
7. Update documentation
8. Test driver chaining scenarios

## Success Criteria

- [ ] HikariPoolDriver accepts URLs with `jdbc:hikari:` prefix
- [ ] Configuration parameters correctly mapped to HikariConfig
- [ ] Connections properly pooled and reused
- [ ] Pool statistics accessible (via HikariDataSource API)
- [ ] Works in driver chains with other PJDBC drivers
- [ ] All unit tests pass
- [ ] No regressions in existing tests

## References

- [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby)
- [Baeldung HikariCP Guide](https://www.baeldung.com/hikaricp)
