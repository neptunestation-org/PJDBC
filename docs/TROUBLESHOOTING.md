# PJDBC Troubleshooting Guide

Common issues, known limitations, and their solutions.

## Quick Diagnostics

### Enable Debug Logging

```bash
# Via system property
java -Dorg.pjdbc.level=FINE -jar myapp.jar

# Or programmatically
java.util.logging.Logger.getLogger("org.pjdbc").setLevel(java.util.logging.Level.FINE);
```

### Validate Your URL

```bash
bin/pjdbc validate "jdbc:retry[maxRetries=3]:jdbc:postgresql://localhost/db"
```

### Visualize Driver Chain

```bash
bin/pjdbc chain "jdbc:retry:jdbc:timeout:jdbc:filter:jdbc:postgresql://localhost/db"
```

---

## Common Issues

### Driver Not Found

**Symptom:** `No suitable driver found for jdbc:retry:...`

**Cause:** PJDBC drivers not loaded.

**Solution:** Ensure drivers are loaded before use:
```java
// Option 1: Explicit class loading
Class.forName("org.pjdbc.drivers.RetryDriver");

// Option 2: ServiceLoader (automatic if pjdbc.jar in classpath)
// Just ensure META-INF/services/java.sql.Driver is present
```

---

### Invalid URL Parameter

**Symptom:** `SQLException: Invalid parameter 'maxRetry' for driver 'retry'`

**Cause:** Typo in parameter name.

**Solution:** Check correct parameter names:
```bash
bin/pjdbc show retry
```

Common typos:
| Wrong | Correct |
|-------|---------|
| `maxRetry` | `maxRetries` |
| `queryTimeOut` | `queryTimeout` |
| `readonly` | Use `jdbc:readonly:` driver instead |

---

### Connection Fails After Network Blip

**Symptom:** Operations fail after brief network interruption, even with RetryDriver.

**Cause:** RetryDriver retries the operation but the underlying connection is dead.

**Solution:** RetryDriver now recreates connections on connection errors (SQLState 08xxx). Ensure you're using PJDBC 2.0+.

**Limitation:** For PreparedStatement, parameter bindings are lost on reconnection. Re-bind parameters after catching the retry warning in logs.

---

### FederatingDriver Returns Unexpected Results

**Symptom:** Query returns fewer/more rows than expected from federated sources.

**Causes:**
1. One source failed silently
2. Merge strategy doesn't match your intent

**Solutions:**

1. Enable logging to see per-source results:
   ```bash
   java -Dorg.pjdbc.level=FINE ...
   ```

2. Choose appropriate merge strategy:
   ```
   jdbc:federate[mergeStrategy=concat]:...      # All rows from all sources
   jdbc:federate[mergeStrategy=first_non_empty]:... # First source with data
   ```

**Known Limitation:** `getRow()` returns 0 on merged ResultSets. Track row count in application code if needed.

---

### Transactions Don't Work with FederatingDriver

**Symptom:** `commit()` or `rollback()` doesn't affect all databases.

**Cause:** FederatingDriver cannot coordinate distributed transactions.

**Solution:** This is a fundamental limitation. Options:
1. Use `strictTransactions=true` to get explicit errors
2. Design for eventual consistency
3. Use a real distributed transaction coordinator (XA)

```java
// Fail fast on transaction attempts
jdbc:federate[strictTransactions=true]:jdbc:db1;jdbc:db2
```

---

### DataMaskingDriver Doesn't Mask Numeric Columns

**Symptom:** Sensitive numeric data (SSN as INT) not masked.

**Cause:** DataMaskingDriver only masks String column types.

**Workaround:** Cast to VARCHAR in your query:
```sql
SELECT CAST(ssn AS VARCHAR) as ssn FROM users
```

---

### TimeoutDriver Doesn't Cancel Long Queries

**Symptom:** Query exceeds timeout but keeps running on database.

**Cause:** Not all JDBC drivers support `Statement.cancel()`.

**Solution:** Check your database driver documentation. PostgreSQL and MySQL support cancellation; some others don't.

---

### RetryDriver Retries Non-Idempotent Operations

**Symptom:** INSERT executed multiple times after transient failure.

**Cause:** RetryDriver cannot know if your operation is idempotent.

**Solution:** Only use RetryDriver for:
- SELECT queries
- Idempotent writes (INSERT with ON CONFLICT, UPSERT)
- Operations with application-level idempotency keys

**Wrong:**
```
jdbc:retry:jdbc:postgresql://...
INSERT INTO orders (id, amount) VALUES (DEFAULT, 100)  -- May insert twice!
```

**Right:**
```
jdbc:retry:jdbc:postgresql://...
INSERT INTO orders (id, amount) VALUES (?, ?) ON CONFLICT DO NOTHING
```

---

### ReadonlyDriver Blocks Writes in Stored Procedures

**Symptom:** Stored procedure that does internal writes fails.

**Cause:** ReadonlyDriver can't see inside stored procedures.

**Solution:** ReadonlyDriver validates SQL text, not execution. For stored procedures with side effects, don't use ReadonlyDriver.

---

### SchemaValidationDriver Rejects Valid Queries

**Symptom:** `SchemaViolationException` on legitimate query.

**Cause:** Table/column not in whitelist, or parser can't extract table names.

**Solutions:**
1. Add missing table/column to whitelist
2. Use `mode=blacklist` instead to block specific tables
3. Complex queries (CTEs, subqueries) may not parse correctly - simplify or disable validation for that connection

---

### FilterDriver Transformer Not Applied

**Symptom:** SQL passes through unchanged.

**Cause:** Transformer not registered or wrong class name.

**Solution:** Ensure transformer is set before getting connection:
```java
FilterDriver.setTransformer(myTransformer);  // Must be called first!
Connection conn = DriverManager.getConnection("jdbc:filter:...");
```

---

## Performance Issues

### High Overhead with Deep Driver Chains

**Context:** PJDBC adds microseconds per driver layer. With 4+ drivers, this can reach 50-100μs overhead.

**Reality Check:** For queries taking 1-100ms, this is <0.1% overhead - negligible.

**If you still need to optimize:**
1. Reduce chain depth - combine functionality
2. Move expensive drivers (filter, schema) closer to the database
3. If microseconds matter, don't use proxy layers

---

### Memory Growth with Long-Lived Connections

**Symptom:** Heap grows over time with PJDBC connections.

**Diagnosis:**
```java
// Check for connection leaks
bin/pjdbc test "your-connection-string"
```

**Solutions:**
1. Ensure connections are closed (try-with-resources)
2. Use connection pooling (HikariCP, not PJDBC - we removed PoolDriver intentionally)
3. Check for ResultSet leaks

---

## Getting Help

1. **Validate configuration:** `bin/pjdbc validate "your-url"`
2. **Enable logging:** `-Dorg.pjdbc.level=FINE`
3. **Check driver docs:** `bin/pjdbc show <driver-prefix>`
4. **File issue:** https://github.com/neptunestation-org/PJDBC/issues
