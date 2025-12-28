# PJDBC Developer's Guide

This guide is for contributors to the PJDBC project itself.

## Project Structure

```
PJDBC/
├── src/
│   ├── main/java/org/pjdbc/
│   │   ├── sql/           # Core abstract classes
│   │   └── drivers/       # Built-in driver implementations
│   └── test/java/         # JUnit tests
├── docs/                  # Documentation
├── pom.xml               # Maven build
└── .beads/               # Issue tracking
```

## Core Classes

### org.pjdbc.sql (Abstract Layer)

| Class | Purpose |
|-------|---------|
| `AbstractDriver` | Base JDBC Driver with URL parsing |
| `AbstractProxyDriver` | Proxying infrastructure |
| `AbstractConnection` | Connection wrapper with delegation |
| `AbstractStatement` | Statement wrapper |
| `AbstractPreparedStatement` | PreparedStatement wrapper with parameter hooks |
| `AbstractCallableStatement` | CallableStatement wrapper |
| `AbstractResultSet` | ResultSet wrapper with value hooks |
| `JdbcTransformer` | Interface for SQL/parameter/result transformation |
| `AbstractJdbcTransformer` | Default no-op transformer |
| `JdbcUrlParser` | URL parsing with parameter support |

### org.pjdbc.drivers (Implementations)

| Driver | Subprotocol | Purpose |
|--------|-------------|---------|
| `CatDriver` | `cat` | Passthrough |
| `PoolDriver` | `pool` | Connection pooling |
| `FilterDriver` | `filter` | SQL transformation |
| `LogDriver` | `log` | SQL logging |
| `TeeDriver` | `tee` | Dual-write |
| `UserMapDriver` | `mapuser` | Credential mapping |
| `MockDriver` | `mock` | Testing |

## Building

```bash
mvn clean compile
mvn test
mvn package
```

## Testing

### Using MockDriver

`MockDriver` captures all SQL for verification:

```java
Connection c = DriverManager.getConnection("jdbc:mock:testdb");
Statement s = c.createStatement();
s.executeQuery("SELECT * FROM users");

String log = MockDriver.getLog("jdbc:mock:testdb");
assertEquals("executeQuery[SELECT * FROM users]", log);
```

### Test Patterns

1. **Direct connection**: Test driver.connect() directly
2. **Indirect connection**: Test via DriverManager
3. **URL acceptance**: Verify acceptsURL() behavior
4. **Edge cases**: Null URLs, malformed URLs, null Properties

## Adding a New Driver

1. Create class in `org.pjdbc.drivers`
2. Extend `AbstractProxyDriver`
3. Implement `acceptsSubProtocol(String)`
4. Add static registration block
5. Override proxy methods as needed
6. Add tests in `src/test/java`

## Transformation Hooks

### Input Transformation

In `AbstractPreparedStatement`:
```java
protected Object transformParameter(int parameterIndex, Object value, int sqlType)
    throws SQLException {
    return value; // Override to transform
}
```

### Output Transformation

In `AbstractResultSet`:
```java
protected Object transformValue(int columnIndex, Object value, int sqlType)
    throws SQLException {
    return value; // Override to transform
}
```

## URL Parsing

PJDBC URLs follow this format:
```
jdbc:subprotocol[param1=val1,param2=val2]:subname
```

Use `JdbcUrlParser` for parsing:
```java
JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:pool[max=10]:jdbc:postgresql://...");
parser.getSubprotocol();  // "pool"
parser.getSubname();      // "jdbc:postgresql://..."
parser.getParameter("max"); // "10"
```

## Issue Tracking

This project uses [Beads](https://beads.dev) for issue tracking:

```bash
bd ready          # Show available work
bd show PJDBC-xxx # View issue details
bd close PJDBC-xxx # Close completed issue
```

## Code Style

- Tabs for indentation
- Compact formatting (braces on same line)
- Minimal comments (code should be self-documenting)
- No unnecessary abstractions

## Commit Messages

Format:
```
Brief summary (imperative mood)

- Detail 1
- Detail 2
```

## Testing Checklist

Before submitting:
- [ ] All existing tests pass
- [ ] New functionality has tests
- [ ] Edge cases covered (null, empty, invalid input)
- [ ] Driver registration works
- [ ] URL parsing works for new parameters
