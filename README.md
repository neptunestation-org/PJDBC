# PJDBC - Proxying JDBC Driver

PJDBC is a pluggable JDBC driver framework that enables intercepting, transforming, and extending database operations through composable proxy drivers.

## Features

- **Chainable Drivers**: Stack multiple proxy drivers to build complex pipelines
- **SQL Transformation**: Modify SQL statements before execution
- **Connection Pooling**: Built-in connection pool driver
- **Query Logging**: Log all SQL statements via Java logging
- **User Mapping**: Map application users to database credentials
- **Extensible**: Create custom drivers by extending base classes

## Installation

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>org.pjdbc</groupId>
  <artifactId>PJDBC</artifactId>
  <version>1.0-SNAPSHOT</version>
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

## Chaining Drivers

Drivers can be composed by nesting URLs:

```java
// Pool -> Log -> Actual Database
Connection conn = DriverManager.getConnection(
    "jdbc:pool:jdbc:log:jdbc:postgresql://localhost/mydb"
);
```

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

## Requirements

- Java 8 or higher
- Maven 3.x

## Author

David A. Ventimiglia <davidaventimiglia@neptunestation.com>
