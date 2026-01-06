# Framework Integration Guide

PJDBC integrates transparently with any JDBC-based framework. Since PJDBC operates at the JDBC URL level, integration typically requires only changing your connection URL.

## Table of Contents

- [Spring Boot](#spring-boot)
- [Spring Framework (non-Boot)](#spring-framework-non-boot)
- [Hibernate / JPA](#hibernate--jpa)
- [HikariCP](#hikaricp)
- [MyBatis](#mybatis)
- [jOOQ](#jooq)
- [Quarkus](#quarkus)
- [Micronaut](#micronaut)
- [Plain JDBC](#plain-jdbc)
- [Troubleshooting](#troubleshooting)

---

## Spring Boot

Spring Boot auto-configures DataSource from `application.properties` or `application.yml`.

### Basic Configuration

```properties
# application.properties

# Before PJDBC
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb

# With PJDBC retry driver
spring.datasource.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb

# With multiple PJDBC drivers (retry + timeout)
spring.datasource.url=jdbc:retry:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost:5432/mydb

spring.datasource.username=myuser
spring.datasource.password=mypassword
```

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
```

### With Driver Parameters

```properties
# Retry with custom settings
spring.datasource.url=jdbc:retry[maxRetries=5,initialDelay=200,maxDelay=10000]:jdbc:postgresql://localhost:5432/mydb

# Read-only for reporting
spring.datasource.url=jdbc:readonly:jdbc:postgresql://localhost:5432/mydb

# Data masking for dev/test
spring.datasource.url=jdbc:mask[columns=ssn;credit_card]:jdbc:postgresql://localhost:5432/mydb
```

### Multiple DataSources

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.readonly")
    public DataSource readonlyDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

```properties
# Primary with retry
spring.datasource.primary.url=jdbc:retry:jdbc:postgresql://primary:5432/mydb
spring.datasource.primary.username=app_user

# Read replica with readonly enforcement
spring.datasource.readonly.url=jdbc:readonly:jdbc:postgresql://replica:5432/mydb
spring.datasource.readonly.username=readonly_user
```

### Profile-Based Configuration

```properties
# application.properties (base)
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb

# application-dev.properties
spring.datasource.url=jdbc:mask[columns=ssn;email]:jdbc:postgresql://localhost:5432/mydb

# application-prod.properties
spring.datasource.url=jdbc:retry[maxRetries=3]:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://prod:5432/mydb
```

---

## Spring Framework (non-Boot)

### XML Configuration

```xml
<bean id="dataSource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
    <property name="url" value="jdbc:retry:jdbc:postgresql://localhost:5432/mydb"/>
    <property name="username" value="myuser"/>
    <property name="password" value="mypassword"/>
</bean>
```

### Java Configuration

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:retry:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost:5432/mydb");
        ds.setUsername("myuser");
        ds.setPassword("mypassword");
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

### With HikariCP (Recommended)

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("myuser");
        config.setPassword("mypassword");
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }
}
```

---

## Hibernate / JPA

### persistence.xml

```xml
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence" version="2.2">
    <persistence-unit name="myPU">
        <properties>
            <property name="javax.persistence.jdbc.url"
                      value="jdbc:retry:jdbc:postgresql://localhost:5432/mydb"/>
            <property name="javax.persistence.jdbc.user" value="myuser"/>
            <property name="javax.persistence.jdbc.password" value="mypassword"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        </properties>
    </persistence-unit>
</persistence>
```

### Spring Boot JPA

```properties
# application.properties
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.datasource.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypassword
```

### Hibernate Configuration (hibernate.cfg.xml)

```xml
<hibernate-configuration>
    <session-factory>
        <property name="connection.url">jdbc:retry:jdbc:postgresql://localhost:5432/mydb</property>
        <property name="connection.username">myuser</property>
        <property name="connection.password">mypassword</property>
        <property name="dialect">org.hibernate.dialect.PostgreSQLDialect</property>
    </session-factory>
</hibernate-configuration>
```

### Programmatic Configuration

```java
Configuration configuration = new Configuration();
configuration.setProperty("hibernate.connection.url",
    "jdbc:retry:jdbc:timeout[queryTimeout=60]:jdbc:postgresql://localhost:5432/mydb");
configuration.setProperty("hibernate.connection.username", "myuser");
configuration.setProperty("hibernate.connection.password", "mypassword");

SessionFactory sessionFactory = configuration.buildSessionFactory();
```

---

## HikariCP

HikariCP is the recommended connection pool for PJDBC. It works transparently with PJDBC URLs.

### Programmatic Configuration

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:retry[maxRetries=3]:jdbc:postgresql://localhost:5432/mydb");
config.setUsername("myuser");
config.setPassword("mypassword");

// Pool settings
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setIdleTimeout(300000);
config.setConnectionTimeout(30000);

// Validation query (optional, most drivers support isValid())
config.setConnectionTestQuery("SELECT 1");

HikariDataSource dataSource = new HikariDataSource(config);
```

### Properties File

```properties
# hikari.properties
jdbcUrl=jdbc:retry:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost:5432/mydb
username=myuser
password=mypassword
maximumPoolSize=10
minimumIdle=2
idleTimeout=300000
connectionTimeout=30000
```

```java
HikariConfig config = new HikariConfig("hikari.properties");
HikariDataSource dataSource = new HikariDataSource(config);
```

### Spring Boot with HikariCP

Spring Boot 2.x+ uses HikariCP by default:

```properties
# application.properties
spring.datasource.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypassword
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
```

### Important Notes

**Connection Validation**: HikariCP's connection validation works normally with PJDBC. The validation query runs through the PJDBC driver chain.

**Retry + Pool Interaction**: When using RetryDriver with HikariCP:
- Connection-level retries happen at the PJDBC layer
- Pool-level connection acquisition has its own timeout (`connectionTimeout`)
- Consider your total timeout budget: pool timeout + PJDBC retry delays

```properties
# Example: 30s pool timeout + up to 3 retries with max 5s delay each
spring.datasource.hikari.connection-timeout=30000
spring.datasource.url=jdbc:retry[maxRetries=3,maxDelay=5000]:jdbc:postgresql://localhost/mydb
```

---

## MyBatis

### mybatis-config.xml

```xml
<configuration>
    <environments default="development">
        <environment id="development">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="org.pjdbc.drivers.RetryDriver"/>
                <property name="url" value="jdbc:retry:jdbc:postgresql://localhost:5432/mydb"/>
                <property name="username" value="myuser"/>
                <property name="password" value="mypassword"/>
            </dataSource>
        </environment>
    </environments>
</configuration>
```

### Spring Boot + MyBatis

```properties
# application.properties
mybatis.configuration.map-underscore-to-camel-case=true
spring.datasource.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypassword
```

### With External DataSource

```java
@Configuration
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("myuser");
        config.setPassword("mypassword");
        return new HikariDataSource(config);
    }
}
```

---

## jOOQ

### Programmatic Configuration

```java
// Create connection with PJDBC
Connection connection = DriverManager.getConnection(
    "jdbc:retry:jdbc:postgresql://localhost:5432/mydb",
    "myuser",
    "mypassword"
);

// Create jOOQ DSLContext
DSLContext create = DSL.using(connection, SQLDialect.POSTGRES);

// Use jOOQ normally
Result<Record> result = create.select().from("users").fetch();
```

### With DataSource

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:retry:jdbc:postgresql://localhost:5432/mydb");
config.setUsername("myuser");
config.setPassword("mypassword");
DataSource dataSource = new HikariDataSource(config);

DSLContext create = DSL.using(dataSource, SQLDialect.POSTGRES);
```

### Spring Boot + jOOQ

```properties
# application.properties
spring.datasource.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypassword
spring.jooq.sql-dialect=POSTGRES
```

---

## Quarkus

### application.properties

```properties
# Quarkus datasource configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:retry:jdbc:postgresql://localhost:5432/mydb
quarkus.datasource.username=myuser
quarkus.datasource.password=mypassword

# Optional: pool settings
quarkus.datasource.jdbc.max-size=10
quarkus.datasource.jdbc.min-size=2
```

### Multiple DataSources

```properties
# Default datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:retry:jdbc:postgresql://primary:5432/mydb

# Named readonly datasource
quarkus.datasource.readonly.db-kind=postgresql
quarkus.datasource.readonly.jdbc.url=jdbc:readonly:jdbc:postgresql://replica:5432/mydb
```

---

## Micronaut

### application.yml

```yaml
datasources:
  default:
    url: jdbc:retry:jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
    dialect: POSTGRES
```

### Multiple DataSources

```yaml
datasources:
  default:
    url: jdbc:retry:jdbc:postgresql://primary:5432/mydb
    username: app_user
  readonly:
    url: jdbc:readonly:jdbc:postgresql://replica:5432/mydb
    username: readonly_user
```

---

## Plain JDBC

### Basic Usage

```java
// Load PJDBC drivers (automatic via ServiceLoader in most cases)
Class.forName("org.pjdbc.drivers.RetryDriver");

// Connect with PJDBC URL
Connection conn = DriverManager.getConnection(
    "jdbc:retry[maxRetries=3]:jdbc:postgresql://localhost:5432/mydb",
    "myuser",
    "mypassword"
);

// Use connection normally
try (Statement stmt = conn.createStatement()) {
    ResultSet rs = stmt.executeQuery("SELECT * FROM users");
    while (rs.next()) {
        System.out.println(rs.getString("name"));
    }
}
```

### With Properties

```java
Properties props = new Properties();
props.setProperty("user", "myuser");
props.setProperty("password", "mypassword");

Connection conn = DriverManager.getConnection(
    "jdbc:retry:jdbc:timeout[queryTimeout=30]:jdbc:postgresql://localhost:5432/mydb",
    props
);
```

---

## Troubleshooting

### Driver Not Found

If you get "No suitable driver found", ensure PJDBC is on your classpath:

```xml
<dependency>
    <groupId>org.pjdbc</groupId>
    <artifactId>PJDBC</artifactId>
    <version>2.0.0</version>
</dependency>
```

For some environments, you may need to explicitly load the driver:

```java
Class.forName("org.pjdbc.drivers.RetryDriver");
```

### URL Parsing Issues

Use the CLI to validate your URL:

```bash
java -jar PJDBC.jar validate "jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/mydb"
```

### HikariCP Connection Test Failures

If HikariCP fails to validate connections, ensure your PJDBC chain allows the validation query:

```properties
# ReadonlyDriver allows SELECT queries for validation
spring.datasource.url=jdbc:readonly:jdbc:postgresql://localhost/mydb
```

### Timeout Stacking

When combining TimeoutDriver with pool timeouts, be aware of cumulative timeouts:

```
Total max wait = Pool connectionTimeout + PJDBC queryTimeout
```

Configure accordingly:

```properties
# Pool waits up to 30s for connection
spring.datasource.hikari.connection-timeout=30000

# Each query has 60s timeout
spring.datasource.url=jdbc:timeout[queryTimeout=60]:jdbc:postgresql://localhost/mydb
```

### Retry + Transaction Interaction

RetryDriver retries individual statements, not transactions. For transaction-level retry:

```java
// Application-level transaction retry
int maxAttempts = 3;
for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
        performTransaction();
        break;
    } catch (SQLException e) {
        if (attempt == maxAttempts || !isRetryable(e)) throw e;
        Thread.sleep(100 * attempt);
    }
}
```

### Logging Driver Activity

For debugging, add CatDriver to log the SQL passing through:

```java
// CatDriver doesn't add logging, but you can extend it
// Or use p6spy alongside PJDBC (see INTEROP.md)
```

### CircuitBreaker State Monitoring

Access circuit breaker state for health checks:

```java
@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/health/db")
    public Map<String, Object> dbHealth() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            CircuitBreakerDriver.CircuitBreaker cb =
                CircuitBreakerDriver.getCircuitBreaker(conn);
            if (cb != null) {
                return Map.of(
                    "state", cb.getState().name(),
                    "failures", cb.getFailureCount(),
                    "threshold", cb.getFailureThreshold()
                );
            }
        }
        return Map.of("status", "unknown");
    }
}
```
