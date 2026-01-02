# PJDBC Driver Writer's Guide

This guide explains how to create custom PJDBC proxy drivers.

## Architecture Overview

PJDBC uses a layered architecture:

```
AbstractDriver
    └── AbstractProxyDriver
            └── Your Custom Driver
```

- `AbstractDriver`: Base JDBC driver implementation with URL parsing
- `AbstractProxyDriver`: Adds proxying infrastructure (wraps connections, statements, etc.)

## Creating a Simple Driver

### Step 1: Extend AbstractProxyDriver

```java
package com.example;

import java.sql.*;
import org.pjdbc.sql.*;

public class MyDriver extends AbstractProxyDriver {
    // Auto-register with DriverManager
    static {
        try {
            DriverManager.registerDriver(new MyDriver());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Define your subprotocol (e.g., "jdbc:mydriver:...")
    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "mydriver".equals(subprotocol);
    }
}
```

This minimal driver:
- Registers itself with `DriverManager`
- Accepts URLs like `jdbc:mydriver:jdbc:postgresql://...`
- Passes all calls through unchanged

### Step 2: Override Proxy Methods

Override these methods to intercept and transform calls:

```java
// Intercept statement creation
@Override
protected Statement proxyStatement(Statement delegate, Connection conn)
    throws SQLException {
    return new AbstractStatement(delegate, conn) {
        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            // Transform SQL before execution
            String transformed = sql.toUpperCase();
            return super.executeQuery(transformed);
        }
    };
}

// Intercept result sets
@Override
protected ResultSet proxyResultSet(Statement stmt, ResultSet delegate)
    throws SQLException {
    return new AbstractResultSet(stmt, delegate) {
        @Override
        public String getString(int columnIndex) throws SQLException {
            // Transform output
            return super.getString(columnIndex).trim();
        }
    };
}
```

## Available Proxy Points

| Method                   | Purpose                        |
|--------------------------|--------------------------------|
| `proxyConnection`        | Wrap Connection objects        |
| `proxyStatement`         | Wrap Statement objects         |
| `proxyPreparedStatement` | Wrap PreparedStatement objects |
| `proxyCallableStatement` | Wrap CallableStatement objects |
| `proxyResultSet`         | Wrap ResultSet objects         |

## URL Parameters

Use `JdbcUrlParser` for URL parameters:

```java
@Override
public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) return null;

    // Parse URL parameters like jdbc:mydriver[param=value]:...
    JdbcUrlParser parser = parseUrl(url);
    String myParam = parser.getParameter("param", "default");

    // Get underlying URL
    String targetUrl = subname(url);

    return proxyConnection(
        DriverManager.getConnection(targetUrl, info),
        targetUrl, info, this);
}
```

## Using JdbcTransformer

For SQL/parameter/result transformation, use `JdbcTransformer`:

```java
public class TransformingDriver extends AbstractProxyDriver {
    protected JdbcTransformer transformer = new AbstractJdbcTransformer() {};

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn)
        throws SQLException {
        final JdbcTransformer xformer = transformer;
        return new AbstractStatement(delegate, conn) {
            @Override
            public ResultSet executeQuery(String sql) throws SQLException {
                return super.executeQuery(xformer.transformSql(sql));
            }
        };
    }

    public void setTransformer(JdbcTransformer t) {
        this.transformer = t;
    }
}
```

## Abstract Classes Reference

### AbstractStatement

Override SQL execution methods:
- `executeQuery(String sql)`
- `executeUpdate(String sql)`
- `execute(String sql)`
- `addBatch(String sql)`

### AbstractPreparedStatement

Override parameter setters for input transformation:
- `setString(int, String)`
- `setInt(int, int)`
- `setObject(int, Object)`
- etc.

Use `transformParameter(int index, Object value, int sqlType)` hook.

### AbstractResultSet

Override getters for output transformation:
- `getString(int)`
- `getInt(int)`
- `getObject(int)`
- etc.

Use `transformValue(int columnIndex, Object value, int sqlType)` hook.

## Complete Example: Logging Driver

```java
package com.example;

import java.sql.*;
import java.util.logging.*;
import org.pjdbc.sql.*;

public class SimpleLogDriver extends AbstractProxyDriver {
    static {
        try {
            DriverManager.registerDriver(new SimpleLogDriver());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Logger LOG = Logger.getLogger(SimpleLogDriver.class.getName());

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "simplelog".equals(subprotocol);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn)
        throws SQLException {
        return new AbstractStatement(delegate, conn) {
            @Override
            public ResultSet executeQuery(String sql) throws SQLException {
                LOG.info("SQL: " + sql);
                return super.executeQuery(sql);
            }

            @Override
            public int executeUpdate(String sql) throws SQLException {
                LOG.info("SQL: " + sql);
                return super.executeUpdate(sql);
            }
        };
    }
}
```

Usage:
```java
Connection c = DriverManager.getConnection("jdbc:simplelog:jdbc:postgresql://localhost/db");
```

## Testing Your Driver

Use `MockDriver` for unit tests:

```java
@Test
public void testMyDriver() throws SQLException {
    Connection c = DriverManager.getConnection("jdbc:mydriver:jdbc:mock:test");
    Statement s = c.createStatement();
    s.executeQuery("SELECT * FROM users");

    // Verify with MockDriver's log
    String log = MockDriver.getLog("jdbc:mock:test");
    assertTrue(log.contains("SELECT"));
}
```
