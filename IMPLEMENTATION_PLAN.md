# PJDBC Completion Implementation Plan

## Executive Summary

PJDBC (Proxying JDBC Driver) is approximately 65-70% complete relative to its stated objectives. The core proxying infrastructure for **input transformation** (SQL strings) works well, but **output transformation** (ResultSet data) and **parameter transformation** (PreparedStatement bound values) are missing.

This document outlines a comprehensive plan to complete the project.

---

## Project Objectives (from README)

1. Provide PJDBC, the Proxying JDBC driver
2. Pluggable architecture
3. Allow the introduction of proxies to intercept JDBC calls
4. **Transform both input and output**

---

## Current State Assessment

### What Works

| Feature | Status | Notes |
|---------|--------|-------|
| Core proxy mechanism | ✅ Complete | AbstractProxyDriver → AbstractConnection → AbstractStatement chain |
| Driver chaining | ✅ Complete | Complex chains like `jdbc:log:jdbc:filter:jdbc:cat:jdbc:mock:foo` work |
| Pluggability | ✅ Complete | Easy to add new drivers via AbstractProxyDriver |
| Input transformation (SQL) | ✅ Complete | FilterDriver demonstrates SQL rewriting |
| Broadcasting/multi-target | ✅ Complete | TeeDriver executes against multiple backends |

### What's Missing

| Feature | Status | Notes |
|---------|--------|-------|
| Output transformation | ❌ Stub only | `proxyResultSet()` returns delegate unchanged |
| Parameter transformation | ❌ Missing | PreparedStatement `setXXX()` methods not intercepted |
| RMI support | ❌ Incomplete | rmi/ package exists but RemoteDriver unfinished |
| FilterDriver state | ⚠️ Bug | Static `fltr` field shared across all instances |
| Test coverage | ⚠️ Sparse | Happy paths only; TeeDriverTest has missing annotations |
| Documentation | ⚠️ Minimal | No user/developer guides |

---

## Architectural Decisions

The following decisions have been made to guide implementation:

### 1. Transformation Scope: Connection Level

Transformations will be configurable at the **connection level**, meaning each connection can have different transformations applied. This balances flexibility with simplicity.

### 2. Configuration Mechanism: All Methods Supported

Transformers can be configured via:
- Programmatically (`driver.setTransformer(...)`)
- URL parameters (`jdbc:filter[class=com.MyFilter]:jdbc:...`)
- Properties object at connect time

Priority when multiple are specified: Properties > URL > Programmatic default

### 3. RMI Priority: Deferred

RMI implementation will be deferred to focus on core transformation functionality first. Modern alternatives (HTTP, gRPC) make this lower priority.

### 4. Backward Compatibility: Maintained with Deprecation

Existing `FilterDriver.setFilter(Filter)` code will continue to work but will be marked `@Deprecated` with migration guidance.

---

## Implementation Phases

### Phase 1: Core Transformation Support (CRITICAL)

**Priority:** CRITICAL
**Estimated Effort:** 5-7 days

#### 1.1 Implement ResultSet Output Transformation

**Current State:** `proxyResultSet()` in `AbstractProxyDriver.java` (line 66-67) is a stub:

```java
protected ResultSet proxyResultSet (Statement stmt, ResultSet delegate) throws SQLException {
    return delegate;}
```

**Required Changes:**

1. **Enhance AbstractResultSet** with transformation hooks:
   - Override all `getXXX()` methods to call transformation
   - Add overridable `transformValue(int columnIndex, Object value, int sqlType)` method
   - Maintain reference to parent Statement for context

2. **Update AbstractProxyDriver.proxyResultSet()** to return a proper proxy:
   ```java
   protected ResultSet proxyResultSet (Statement stmt, ResultSet delegate) throws SQLException {
       return new AbstractResultSet(stmt, delegate) {
           @Override
           protected Object transformValue(int columnIndex, Object value, int sqlType) {
               return value; // Subclasses override for transformation
           }
       };
   }
   ```

3. **Wire through all ResultSet return points:**
   - `AbstractStatement.executeQuery()`
   - `AbstractStatement.getResultSet()`
   - `AbstractStatement.getGeneratedKeys()`
   - `AbstractDatabaseMetaData` methods returning ResultSet

**Files to Modify:**
- `src/main/java/org/pjdbc/sql/AbstractProxyDriver.java`
- `src/main/java/org/pjdbc/sql/AbstractStatement.java`
- `src/main/java/org/pjdbc/sql/AbstractResultSet.java`

#### 1.2 Implement PreparedStatement Parameter Transformation

**Current State:** `AbstractPreparedStatement` delegates all `setXXX()` methods directly without transformation hooks.

**Required Changes:**

1. **Add transformation hook to AbstractPreparedStatement:**
   ```java
   protected Object transformParameter(int parameterIndex, Object value, int sqlType) {
       return value; // Subclasses override
   }
   ```

2. **Override setXXX methods** to apply transformation:
   ```java
   public void setString(int parameterIndex, String x) throws SQLException {
       String transformed = (String) transformParameter(parameterIndex, x, Types.VARCHAR);
       super.setString(parameterIndex, transformed);
   }
   ```

3. **Update AbstractProxyDriver.proxyPreparedStatement()** to support transformation.

**Files to Modify:**
- `src/main/java/org/pjdbc/sql/AbstractPreparedStatement.java`
- `src/main/java/org/pjdbc/sql/AbstractProxyDriver.java`

#### 1.3 Fix FilterDriver Static State Bug

**Current State:** In `FilterDriver.java` (line 15):
```java
protected static Filter fltr = new AbstractFilter() {};
```

This causes all FilterDriver instances to share the same Filter.

**Required Changes:**

1. Make filter instance-scoped:
   ```java
   protected Filter fltr = new AbstractFilter() {};
   ```

2. Store filter configuration in Connection wrapper for connection-scoped behavior.

3. Support configuration via Properties:
   ```java
   public Connection connect(String url, Properties info) throws SQLException {
       Filter filter = loadFilterFromProperties(info);
       // Use connection-scoped filter
   }
   ```

**Files to Modify:**
- `src/main/java/org/pjdbc/drivers/FilterDriver.java`

---

### Phase 2: Enhanced Filter Architecture (HIGH)

**Priority:** HIGH
**Estimated Effort:** 2-3 days

#### 2.1 Create Unified JdbcTransformer Interface

Create a comprehensive transformation interface:

```java
package org.pjdbc.sql;

import java.sql.*;

/**
 * Unified interface for JDBC transformation.
 * Handles input (SQL, parameters) and output (ResultSet values) transformation.
 */
public interface JdbcTransformer {

    // Input transformations

    /**
     * Transform SQL before execution.
     * @param sql Original SQL string
     * @return Transformed SQL string
     */
    String transformSql(String sql);

    /**
     * Transform a parameter before binding.
     * @param parameterIndex 1-based parameter index
     * @param value Original parameter value
     * @param sqlType SQL type from java.sql.Types
     * @return Transformed parameter value
     */
    Object transformParameter(int parameterIndex, Object value, int sqlType);

    // Output transformations

    /**
     * Transform a value retrieved from ResultSet.
     * @param columnIndex 1-based column index
     * @param columnName Column name
     * @param value Original value from ResultSet
     * @param sqlType SQL type from java.sql.Types
     * @return Transformed value
     */
    Object transformResultValue(int columnIndex, String columnName, Object value, int sqlType);
}
```

#### 2.2 Create AbstractJdbcTransformer Base Class

```java
package org.pjdbc.sql;

/**
 * Base implementation of JdbcTransformer that performs no transformation.
 * Subclass and override specific methods to add transformation behavior.
 */
public abstract class AbstractJdbcTransformer implements JdbcTransformer {

    @Override
    public String transformSql(String sql) {
        return sql;
    }

    @Override
    public Object transformParameter(int parameterIndex, Object value, int sqlType) {
        return value;
    }

    @Override
    public Object transformResultValue(int columnIndex, String columnName, Object value, int sqlType) {
        return value;
    }
}
```

#### 2.3 Update FilterDriver for Backward Compatibility

```java
public class FilterDriver extends AbstractProxyDriver {

    // Legacy interface - deprecated but still supported
    @Deprecated
    public interface Filter {
        String apply(String sql);
    }

    // Adapter from legacy Filter to JdbcTransformer
    private static class FilterAdapter extends AbstractJdbcTransformer {
        private final Filter filter;

        FilterAdapter(Filter filter) {
            this.filter = filter;
        }

        @Override
        public String transformSql(String sql) {
            return filter.apply(sql);
        }
    }

    // New API
    public void setTransformer(JdbcTransformer transformer) { ... }

    // Legacy API - deprecated
    @Deprecated
    public void setFilter(Filter filter) {
        setTransformer(new FilterAdapter(filter));
    }
}
```

**Files to Create:**
- `src/main/java/org/pjdbc/sql/JdbcTransformer.java`
- `src/main/java/org/pjdbc/sql/AbstractJdbcTransformer.java`

**Files to Modify:**
- `src/main/java/org/pjdbc/drivers/FilterDriver.java`

---

### Phase 3: Test Coverage Fixes (HIGH)

**Priority:** HIGH
**Estimated Effort:** 2-3 days

#### 3.1 Fix TeeDriverTest Missing Annotations

**Current State:** `TeeDriverTest.java` has test methods without `@Test` annotations:
```java
public void acceptsURL () {  // Missing @Test!
```

**Required Changes:** Add `@Test` annotation to all test methods.

**Files to Modify:**
- `src/test/java/TeeDriverTest.java`

#### 3.2 Add UserMapDriver Tests

Create comprehensive tests for UserMapDriver:

```java
public class UserMapDriverTest {
    @Test
    public void testCredentialMapping() { ... }

    @Test
    public void testMissingUserHandling() { ... }

    @Test
    public void testMalformedPropertiesFile() { ... }
}
```

**Files to Create:**
- `src/test/java/UserMapDriverTest.java`

#### 3.3 Add Edge Case Tests

For each driver, add tests for:
- Null URL handling
- Invalid URL format
- SQLException propagation
- Connection close behavior
- Resource cleanup

#### 3.4 Add Transformation Tests

Create tests for the new transformation functionality:

```java
public class TransformationTest {
    @Test
    public void testSqlTransformation() { ... }

    @Test
    public void testParameterTransformation() { ... }

    @Test
    public void testResultSetTransformation() { ... }

    @Test
    public void testCombinedTransformation() { ... }
}
```

**Files to Create:**
- `src/test/java/TransformationTest.java`

---

### Phase 4: URL Parsing Refactoring (MEDIUM)

**Priority:** MEDIUM
**Estimated Effort:** 1-2 days

#### 4.1 Create JdbcUrlParser Utility

```java
package org.pjdbc.sql;

import java.util.*;

/**
 * Parses PJDBC URLs with support for parameters.
 *
 * URL format: jdbc:subprotocol[param1=value1,param2=value2]:subname
 *
 * Examples:
 *   jdbc:filter:jdbc:mock:foo
 *   jdbc:filter[class=com.example.MyFilter]:jdbc:mock:foo
 *   jdbc:pool[min=5,max=20]:jdbc:postgresql://localhost/db
 */
public class JdbcUrlParser {
    private final String protocol;           // always "jdbc"
    private final String subprotocol;        // e.g., "filter", "log"
    private final String subname;            // remaining URL
    private final Map<String, String> parameters;

    public static JdbcUrlParser parse(String url) { ... }

    public String getProtocol() { return protocol; }
    public String getSubprotocol() { return subprotocol; }
    public String getSubname() { return subname; }
    public Map<String, String> getParameters() { return parameters; }
    public String getParameter(String key) { return parameters.get(key); }
    public String getParameter(String key, String defaultValue) { ... }
}
```

#### 4.2 Update AbstractDriver to Use Parser

Replace manual string splitting with `JdbcUrlParser`.

**Files to Create:**
- `src/main/java/org/pjdbc/sql/JdbcUrlParser.java`

**Files to Modify:**
- `src/main/java/org/pjdbc/sql/AbstractDriver.java`
- `src/main/java/org/pjdbc/sql/AbstractProxyDriver.java`

---

### Phase 5: RMI Completion (DEFERRED)

**Priority:** LOW (Deferred)
**Estimated Effort:** 4-6 days (when undertaken)

RMI implementation is deferred to focus on core transformation functionality. When undertaken, the following would be required:

1. Implement `RemoteDriver` extending `AbstractProxyDriver`
2. Create InvocationHandler implementations for remote proxies
3. Handle serialization concerns (streams, blobs, large data)
4. Implement proper URL parsing for RMI format

---

### Phase 6: PoolDriver Improvements (OPTIONAL)

**Priority:** LOW
**Estimated Effort:** 2-3 days

#### 6.1 Make PoolDriver Configurable

```java
/**
 * Pool configuration interface.
 */
public interface PoolConfiguration {
    int getMinConnections();
    int getMaxConnections();
    long getConnectionTimeoutMs();
    long getIdleTimeoutMs();
    long getMaxLifetimeMs();
    boolean isValidateOnBorrow();
    String getValidationQuery();
}
```

#### 6.2 Support URL Parameters

```
jdbc:pool[min=5,max=20,timeout=30000]:jdbc:postgresql://localhost/db
```

---

### Phase 7: Documentation (OPTIONAL)

**Priority:** LOW
**Estimated Effort:** 3-5 days

Create the following documentation:

1. **User's Guide** (`docs/users-guide.md`)
   - Installation and setup
   - URL format for each driver
   - Configuration options
   - Usage examples
   - Troubleshooting

2. **Driver Writer's Guide** (`docs/driver-writers-guide.md`)
   - Extending AbstractProxyDriver
   - Implementing transformation hooks
   - Best practices
   - Example custom driver

3. **Developer's Guide** (`docs/developers-guide.md`)
   - Architecture overview
   - Building from source
   - Running tests
   - Contributing guidelines

---

## Implementation Order

The following order is recommended to maximize value delivery:

### Step 1: Bug Fixes (Day 1)
1. Fix FilterDriver static state bug
2. Fix TeeDriverTest missing `@Test` annotations
3. Run existing tests to establish baseline

### Step 2: Output Transformation (Days 2-4)
1. Add transformation hooks to AbstractResultSet
2. Update proxyResultSet() in AbstractProxyDriver
3. Wire ResultSet wrapping through all return points
4. Add tests for output transformation

### Step 3: Parameter Transformation (Days 5-6)
1. Add transformation hooks to AbstractPreparedStatement
2. Update proxyPreparedStatement() in AbstractProxyDriver
3. Add tests for parameter transformation

### Step 4: JdbcTransformer Interface (Days 7-8)
1. Create JdbcTransformer interface
2. Create AbstractJdbcTransformer base class
3. Update FilterDriver with backward compatibility
4. Add integration tests

### Step 5: Test Coverage (Days 9-10)
1. Add UserMapDriver tests
2. Add edge case tests for all drivers
3. Add combined transformation tests
4. Ensure all tests pass

### Step 6: URL Parsing (Days 11-12)
1. Create JdbcUrlParser utility
2. Update AbstractDriver to use parser
3. Add URL parameter support to drivers
4. Add parser tests

### Future (As Needed)
- PoolDriver improvements
- RMI completion
- Documentation

---

## Critical Files Reference

| File | Purpose | Changes Needed |
|------|---------|----------------|
| `sql/AbstractProxyDriver.java` | Core proxy creation | Implement `proxyResultSet()`, add transformation hooks |
| `sql/AbstractResultSet.java` | ResultSet wrapper | Add `getXXX()` transformation hooks |
| `sql/AbstractPreparedStatement.java` | PreparedStatement wrapper | Add `setXXX()` transformation hooks |
| `sql/AbstractStatement.java` | Statement wrapper | Ensure ResultSet wrapping in all methods |
| `drivers/FilterDriver.java` | SQL filter driver | Fix static bug, add JdbcTransformer support |
| `test/java/TeeDriverTest.java` | TeeDriver tests | Add missing `@Test` annotations |

---

## Success Criteria

The project will be considered complete when:

1. ✅ All stated objectives from README are met
2. ✅ Input transformation works (SQL strings AND parameters)
3. ✅ Output transformation works (ResultSet values)
4. ✅ Transformation is configurable at connection level
5. ✅ Configuration via programmatic, URL, and Properties all work
6. ✅ Backward compatibility maintained for existing Filter API
7. ✅ All tests pass
8. ✅ Test coverage includes transformation scenarios

---

## Appendix: Current Codebase Structure

```
/home/user/PJDBC/
├── pom.xml                                    # Maven build configuration
├── README.md                                  # Project overview
├── TODO                                       # Original task list
├── IMPLEMENTATION_PLAN.md                     # This document
├── src/
│   ├── main/
│   │   ├── java/org/pjdbc/
│   │   │   ├── drivers/                       # Concrete driver implementations
│   │   │   │   ├── CatDriver.java            # Pass-through proxy
│   │   │   │   ├── FilterDriver.java         # SQL transformation
│   │   │   │   ├── LogDriver.java            # SQL logging
│   │   │   │   ├── MockDriver.java           # Testing mock
│   │   │   │   ├── PoolDriver.java           # Connection pooling
│   │   │   │   ├── SerialDriver.java         # Serialization (incomplete)
│   │   │   │   ├── SinkDriver.java           # Black hole driver
│   │   │   │   ├── TeeDriver.java            # Multi-target execution
│   │   │   │   └── UserMapDriver.java        # Credential mapping
│   │   │   ├── sql/                           # Abstract JDBC wrappers
│   │   │   │   ├── AbstractDriver.java       # Base driver class
│   │   │   │   ├── AbstractProxyDriver.java  # Proxy driver base
│   │   │   │   ├── AbstractWrapper.java      # JDBC Wrapper base
│   │   │   │   ├── AbstractConnection.java   # Connection wrapper
│   │   │   │   ├── AbstractStatement.java    # Statement wrapper
│   │   │   │   ├── AbstractPreparedStatement.java
│   │   │   │   ├── AbstractCallableStatement.java
│   │   │   │   ├── AbstractResultSet.java    # ResultSet wrapper
│   │   │   │   └── ... (metadata wrappers)
│   │   │   └── rmi/                           # RMI support (incomplete)
│   │   └── resources/
│   │       └── META-INF/services/
│   │           └── java.sql.Driver            # ServiceLoader config
│   └── test/
│       └── java/                              # Test classes
│           ├── CatDriverTest.java
│           ├── DriverCompositionTest.java
│           ├── FilterDriverTest.java
│           ├── LogDriverTest.java
│           ├── MockDriverTest.java
│           ├── PoolDriverTest.java
│           ├── SinkDriverTest.java
│           └── TeeDriverTest.java
```
