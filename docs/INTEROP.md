# Tool Interoperability Guide

PJDBC can coexist with other JDBC tools. This guide covers how to combine PJDBC with SQL logging, connection pools, and observability tools.

## Table of Contents

- [General Principles](#general-principles)
- [p6spy](#p6spy)
- [datasource-proxy](#datasource-proxy)
- [log4jdbc](#log4jdbc)
- [Connection Pools](#connection-pools)
- [OpenTelemetry](#opentelemetry)
- [Micrometer](#micrometer)
- [Flyway / Liquibase](#flyway--liquibase)
- [Driver Chain Order](#driver-chain-order)
- [Troubleshooting](#troubleshooting)

---

## General Principles

### URL-Based vs DataSource-Based Tools

PJDBC operates at the **JDBC URL level** by chaining driver prefixes:

```
jdbc:retry:jdbc:postgresql://localhost/mydb
     ↑          ↑
     PJDBC      Real driver
```

Other tools operate at different levels:

| Tool | Integration Level | Wraps |
|------|------------------|-------|
| **PJDBC** | JDBC URL | Driver/Connection |
| **p6spy** | JDBC URL | Driver/Connection |
| **datasource-proxy** | DataSource | DataSource |
| **HikariCP** | DataSource | Connection factory |
| **OpenTelemetry** | DataSource | DataSource |
| **Micrometer** | DataSource | DataSource |

### Chain Order Matters

When combining tools, order determines what each tool sees:

```
Application
    ↓
Logging (sees final SQL)
    ↓
PJDBC (transforms SQL)
    ↓
Connection Pool
    ↓
Database
```

**Rule of thumb**: Place logging/observability tools *outside* PJDBC to capture the SQL that actually executes.

---

## p6spy

[p6spy](https://github.com/p6spy/p6spy) is a popular SQL logging tool. It works at the JDBC URL level like PJDBC.

### p6spy Outside PJDBC (Recommended)

Log the SQL after PJDBC transformations:

```properties
# spy.properties
driverlist=org.pjdbc.drivers.RetryDriver
realdriver=org.pjdbc.drivers.RetryDriver
```

```properties
# application.properties
spring.datasource.url=jdbc:p6spy:jdbc:retry:jdbc:postgresql://localhost/mydb
spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
```

**Chain**: Application → p6spy → PJDBC → PostgreSQL

This logs the SQL that PJDBC passes to the database.

### p6spy Inside PJDBC

Log the original SQL before PJDBC transformations:

```properties
# spy.properties
driverlist=org.postgresql.Driver
realdriver=org.postgresql.Driver
```

```properties
# application.properties
spring.datasource.url=jdbc:retry:jdbc:p6spy:jdbc:postgresql://localhost/mydb
```

**Chain**: Application → PJDBC → p6spy → PostgreSQL

This logs the original SQL before any PJDBC modifications.

### p6spy Configuration

```properties
# spy.properties

# Use PJDBC as the real driver
driverlist=org.pjdbc.drivers.RetryDriver,org.pjdbc.drivers.TimeoutDriver

# Logging format
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat
appender=com.p6spy.engine.spy.appender.Slf4JLogger

# Optional: exclude certain SQL patterns
filter=true
exclude=SELECT 1
```

### Maven Dependencies

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
<dependency>
    <groupId>org.pjdbc</groupId>
    <artifactId>PJDBC</artifactId>
    <version>2.0.0</version>
</dependency>
```

---

## datasource-proxy

[datasource-proxy](https://github.com/jdbc-observations/datasource-proxy) wraps DataSource objects, not JDBC URLs.

### Integration Pattern

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        // 1. Create HikariCP with PJDBC URL
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
        config.setUsername("user");
        config.setPassword("pass");
        DataSource hikariDs = new HikariDataSource(config);

        // 2. Wrap with datasource-proxy for logging
        return ProxyDataSourceBuilder.create(hikariDs)
            .name("MyDS")
            .logQueryBySlf4j(SLF4JLogLevel.INFO)
            .multiline()
            .build();
    }
}
```

**Chain**: Application → datasource-proxy → HikariCP → PJDBC → PostgreSQL

### With Query Metrics

```java
DataSource dataSource = ProxyDataSourceBuilder.create(hikariDs)
    .name("MyDS")
    .logQueryBySlf4j(SLF4JLogLevel.DEBUG)
    .countQuery()
    .build();

// Access metrics
QueryCountHolder.getGrandTotal();
```

### Spring Boot Auto-Configuration

```java
@Configuration
public class DataSourceProxyConfig {

    @Bean
    public BeanPostProcessor dataSourceProxy() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof DataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create((DataSource) bean)
                        .logQueryBySlf4j(SLF4JLogLevel.INFO)
                        .build();
                }
                return bean;
            }
        };
    }
}
```

---

## log4jdbc

[log4jdbc](https://github.com/arthurblake/log4jdbc) is another URL-based SQL logger.

### log4jdbc Outside PJDBC

```properties
# application.properties
spring.datasource.url=jdbc:log4jdbc:jdbc:retry:jdbc:postgresql://localhost/mydb
spring.datasource.driver-class-name=net.sf.log4jdbc.sql.jdbcapi.DriverSpy
```

### log4jdbc Inside PJDBC

```properties
spring.datasource.url=jdbc:retry:jdbc:log4jdbc:jdbc:postgresql://localhost/mydb
```

### Logging Configuration

```xml
<!-- logback.xml -->
<logger name="jdbc.sqlonly" level="DEBUG"/>
<logger name="jdbc.sqltiming" level="INFO"/>
<logger name="jdbc.resultsettable" level="DEBUG"/>
```

---

## Connection Pools

PJDBC works transparently with connection pools. The pool manages connections; PJDBC transforms SQL.

### HikariCP

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
config.setMaximumPoolSize(10);
DataSource ds = new HikariDataSource(config);
```

### Apache DBCP2

```java
BasicDataSource ds = new BasicDataSource();
ds.setUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
ds.setMaxTotal(10);
```

### c3p0

```java
ComboPooledDataSource ds = new ComboPooledDataSource();
ds.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
ds.setMaxPoolSize(10);
```

### Pool + PJDBC Timeout Interaction

When using TimeoutDriver with a connection pool:

```properties
# Pool connection timeout: how long to wait for a connection from pool
spring.datasource.hikari.connection-timeout=30000

# PJDBC query timeout: how long each query can run
spring.datasource.url=jdbc:timeout[queryTimeout=60]:jdbc:postgresql://localhost/mydb
```

These are independent:
- Pool timeout: waiting for available connection
- Query timeout: executing SQL on acquired connection

---

## OpenTelemetry

OpenTelemetry JDBC instrumentation wraps DataSource objects.

### Auto-Instrumentation

With the OpenTelemetry Java agent, PJDBC connections are traced automatically:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=myapp \
     -jar myapp.jar
```

The agent instruments at the DataSource level, capturing PJDBC-transformed SQL.

### Manual Instrumentation

```java
@Configuration
public class OtelConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
        HikariDataSource hikariDs = new HikariDataSource(config);

        // Wrap with OpenTelemetry
        return JdbcTelemetry.create(GlobalOpenTelemetry.get())
            .wrap(hikariDs);
    }
}
```

### What Gets Traced

OpenTelemetry captures:
- SQL statement (after PJDBC transformation)
- Execution time (including PJDBC overhead)
- Database connection attributes

PJDBC-specific behavior (retries, circuit breaker state) is not automatically traced. For that, extend PJDBC drivers or use JMX.

---

## Micrometer

Micrometer provides DataSource metrics.

### Spring Boot Integration

```java
@Configuration
public class MetricsConfig {

    @Bean
    public DataSource dataSource(MeterRegistry registry) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost/mydb");
        config.setPoolName("mypool");
        HikariDataSource ds = new HikariDataSource(config);

        // HikariCP exports metrics automatically when poolName is set
        return ds;
    }
}
```

### Available Metrics

With HikariCP + Micrometer:
- `hikaricp.connections.active`
- `hikaricp.connections.idle`
- `hikaricp.connections.pending`
- `hikaricp.connections.timeout`

PJDBC-specific metrics (retry counts, circuit breaker state) require custom instrumentation or JMX.

### Circuit Breaker JMX to Micrometer

```java
@Configuration
public class PjdbcMetrics {

    @Bean
    public MeterBinder circuitBreakerMetrics() {
        return registry -> {
            // Expose circuit breaker state as gauge
            Gauge.builder("pjdbc.circuitbreaker.state", () -> {
                CircuitBreakerDriver.CircuitBreaker cb =
                    CircuitBreakerDriver.getCircuitBreaker("default");
                if (cb == null) return -1;
                switch (cb.getState()) {
                    case CLOSED: return 0;
                    case HALF_OPEN: return 1;
                    case OPEN: return 2;
                    default: return -1;
                }
            }).register(registry);
        };
    }
}
```

---

## Flyway / Liquibase

Database migration tools work normally with PJDBC URLs.

### Flyway

```properties
# application.properties
spring.flyway.url=jdbc:retry:jdbc:postgresql://localhost/mydb
spring.flyway.user=migration_user
spring.flyway.password=secret
```

Or use the application DataSource:

```properties
spring.flyway.enabled=true
# Flyway uses spring.datasource.* by default
```

### Liquibase

```properties
spring.liquibase.url=jdbc:retry:jdbc:postgresql://localhost/mydb
spring.liquibase.user=migration_user
spring.liquibase.password=secret
```

### Recommendation

For migrations, consider using a simpler PJDBC chain or direct connection:

```properties
# Application: full PJDBC chain
spring.datasource.url=jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/mydb

# Migrations: minimal chain (migrations are one-time, don't need retry)
spring.flyway.url=jdbc:postgresql://localhost/mydb
```

---

## Driver Chain Order

### Recommended Order

From outermost (closest to application) to innermost (closest to database):

```
1. Logging/Observability (p6spy, log4jdbc)
2. Resilience (retry, circuitbreaker)
3. Timeout
4. Access Control (readonly, schema, mask)
5. Transformation (filter)
6. Terminal/Database driver
```

Example:

```
jdbc:p6spy:jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:postgresql://localhost/mydb
    ↑           ↑          ↑            ↑              ↑
    Log     Retry on   Enforce     Block          PostgreSQL
    SQL     failure    timeout     writes
```

### Anti-Patterns

**Don't**: Put retry inside timeout
```
jdbc:timeout:jdbc:retry:jdbc:postgresql://...
```
This times out the entire retry sequence, not individual attempts.

**Do**: Put timeout inside retry
```
jdbc:retry:jdbc:timeout:jdbc:postgresql://...
```
Each retry attempt gets its own timeout.

**Don't**: Put logging inside transformation
```
jdbc:filter:jdbc:p6spy:jdbc:postgresql://...
```
This logs original SQL, not transformed SQL.

**Do**: Put logging outside transformation
```
jdbc:p6spy:jdbc:filter:jdbc:postgresql://...
```
This logs what actually executes.

---

## Troubleshooting

### Driver Class Not Found

When combining URL-based tools (p6spy, log4jdbc), ensure driver classes are loaded:

```java
// Explicit loading if needed
Class.forName("com.p6spy.engine.spy.P6SpyDriver");
Class.forName("org.pjdbc.drivers.RetryDriver");
```

### Double Logging

If SQL appears twice in logs, you may have multiple logging tools active:

```properties
# Disable one
logging.level.jdbc.sqlonly=OFF

# Or remove duplicate dependency
```

### Connection Pool Exhaustion

If connections aren't returned to pool, check for:
- Unclosed connections in application code
- PJDBC driver holding connections (shouldn't happen with correct implementation)
- Logging tools not closing properly

Enable pool leak detection:

```properties
spring.datasource.hikari.leak-detection-threshold=30000
```

### Slow Startup

Multiple URL-based drivers can slow connection initialization. Each driver in the chain processes the URL:

```
jdbc:p6spy:jdbc:retry:jdbc:timeout:jdbc:postgresql://...
```

Consider:
- Reducing chain length for development
- Using datasource-proxy (wraps DataSource, not URL) for logging

### ClassCastException with Proxies

Some tools return proxy objects that may not cast to expected types:

```java
// May fail if connection is wrapped
PostgreSQLConnection pgConn = (PostgreSQLConnection) conn;

// Use unwrap instead
PostgreSQLConnection pgConn = conn.unwrap(PostgreSQLConnection.class);
```

### JMX Conflicts

If multiple tools register JMX beans, ensure unique names:

```java
// PJDBC circuit breakers use: org.pjdbc:type=CircuitBreaker,name=<name>
// HikariCP uses: com.zaxxer.hikari:type=Pool,name=<poolName>
```

Use distinct names to avoid conflicts.
