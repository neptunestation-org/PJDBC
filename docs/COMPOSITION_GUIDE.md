# PJDBC Driver Composition Guide

How to combine PJDBC drivers effectively.

## How Composition Works

PJDBC drivers wrap each other like layers of an onion. The **outermost** driver processes requests first, then passes to the next layer.

```
Application
    ↓
jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:postgresql://...
    │          │            │              │
    │          │            │              └── Database (innermost)
    │          │            └── ReadonlyDriver (3rd)
    │          └── TimeoutDriver (2nd)
    └── RetryDriver (outermost, 1st to see request)
```

**Execution flow:**
1. App calls `executeQuery()`
2. RetryDriver receives call, may retry on failure
3. TimeoutDriver sets query timeout
4. ReadonlyDriver validates SQL is read-only
5. PostgreSQL executes query

**Return flow:** Results bubble back up through each layer.

---

## Recommended Layer Order

From outermost (first) to innermost (closest to database):

| Layer | Drivers | Why This Position |
|-------|---------|-------------------|
| 1. Resilience | `retry`, `circuitbreaker` | Wrap everything so retries include all logic |
| 2. Timeout | `timeout` | Timeout should apply to entire operation |
| 3. Observability | `filter` (for logging) | See the SQL before/after transformation |
| 4. Transformation | `filter` (for rewriting) | Transform SQL before validation |
| 5. Validation | `schema`, `readonly` | Validate transformed SQL |
| 6. Security | `mask`, `mapuser` | Apply security closest to data |
| 7. Terminal | `federate`, `tee`, `mock` | Must be innermost |

### Example Compositions

**Production read replica with retry:**
```
jdbc:retry[maxRetries=3]:jdbc:timeout[queryTimeout=30]:jdbc:readonly:jdbc:postgresql://replica/db
```

**Development with masking:**
```
jdbc:mask[columns=ssn,email]:jdbc:postgresql://dev/db
```

**Resilient federated query:**
```
jdbc:retry:jdbc:timeout[queryTimeout=60]:jdbc:federate:jdbc:db1;jdbc:db2
```

**SQL transformation with validation:**
```
jdbc:filter:jdbc:schema[tables=allowed_table]:jdbc:postgresql://db
```

---

## Rules and Constraints

### Terminal Drivers Must Be Last

These drivers don't delegate to another JDBC driver:
- `federate` - connects to multiple databases
- `mock` - in-memory mock
- `tee` - connects to multiple databases
- `sink` - discards everything

**Wrong:**
```
jdbc:mock:jdbc:retry:...  ✗ mock can't wrap anything
```

**Right:**
```
jdbc:retry:jdbc:mock  ✓ retry wraps mock
```

### Retry Should Be Outermost

If retry is inside other drivers, those drivers' work is wasted on retry:

**Wrong:**
```
jdbc:timeout:jdbc:retry:...  ✗ timeout runs once, then retry restarts
```

**Right:**
```
jdbc:retry:jdbc:timeout:...  ✓ each retry attempt gets fresh timeout
```

### Timeout Before Operations That Can Hang

Place timeout before any driver that might block:

```
jdbc:timeout[queryTimeout=30]:jdbc:federate:jdbc:slow_db1;jdbc:slow_db2
```

### Validation After Transformation

If using FilterDriver to rewrite SQL, validate the *transformed* SQL:

**Wrong:**
```
jdbc:schema:jdbc:filter:...  ✗ validates original SQL, not transformed
```

**Right:**
```
jdbc:filter:jdbc:schema:...  ✓ validates SQL after transformation
```

### Security Closest to Data

Masking and credential mapping should be close to the database:

```
jdbc:retry:jdbc:timeout:jdbc:mask[columns=ssn]:jdbc:postgresql://...
                              ↑
                        Close to DB, after all other processing
```

---

## Anti-Patterns

### Too Many Layers

**Problem:** 6+ driver layers add measurable overhead on fast queries.

```
jdbc:retry:jdbc:circuitbreaker:jdbc:timeout:jdbc:filter:jdbc:schema:jdbc:mask:jdbc:readonly:jdbc:postgresql://...
```

**Solution:** Combine functionality or accept some limitations:
```
jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:postgresql://...
```

### Retry with Non-Idempotent Operations

**Problem:** RetryDriver will re-execute failed writes.

```
jdbc:retry:jdbc:postgresql://...
-- Then: INSERT INTO orders VALUES (...)  ✗ May insert twice!
```

**Solution:** Only use retry for idempotent operations, or handle at application level.

### Readonly Inside Retry

**Problem:** If readonly rejects a write, retry will keep trying.

```
jdbc:retry:jdbc:readonly:...
-- Then: INSERT INTO ...  ✗ Retries the same rejected query!
```

**Solution:** Readonly rejection is not a transient error. Place readonly outside retry, or don't combine them:
```
jdbc:readonly:jdbc:retry:...  ✓ Rejection happens before retry logic
```

### Federate with Transactions

**Problem:** FederatingDriver can't coordinate distributed transactions.

```
jdbc:federate:jdbc:db1;jdbc:db2
conn.setAutoCommit(false);
// Changes to db1 and db2 are NOT atomic!
```

**Solution:** Use `strictTransactions=true` to fail fast, or design for eventual consistency.

### Masking with Retry

**Problem:** If masking fails (column not found), retry won't help.

```
jdbc:retry:jdbc:mask:...  ✗ Masking errors are not transient
```

**Solution:** Masking errors indicate configuration problems, not transient failures. Don't combine, or ensure masking config is correct.

---

## Debugging Composition

### Visualize the Chain

```bash
bin/pjdbc chain "jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/db"
```

Output:
```
Chain (3 layers):
→ RetryDriver
  └─→ TimeoutDriver
    └─→ postgresql (terminal)
```

### Enable Per-Driver Logging

```bash
java -Dorg.pjdbc.level=FINE ...
```

Each driver logs its operations:
```
FINE: RetryDriver: Attempt 1/4
FINE: TimeoutDriver: Setting query timeout to 30s
FINE: RetryDriver: Operation succeeded on attempt 1
```

### Validate Before Deploying

```bash
bin/pjdbc validate "your-full-jdbc-url"
```

Catches:
- Invalid parameter names
- Wrong parameter types
- Missing required parameters

---

## Quick Reference

| If you need... | Use this composition |
|----------------|---------------------|
| Resilient reads | `jdbc:retry:jdbc:timeout:jdbc:readonly:...` |
| Masked dev data | `jdbc:mask[columns=...]:...` |
| SQL rewriting | `jdbc:filter:...` |
| Multi-DB queries | `jdbc:retry:jdbc:timeout:jdbc:federate:...` |
| Write replication | `jdbc:tee:url1;url2` |
| Testing | `jdbc:mock` or `jdbc:sink` |
| Chaos testing | `jdbc:chaos[failureRate=0.1]:...` |

---

## See Also

- [Troubleshooting Guide](TROUBLESHOOTING.md) - When things go wrong
- [Driver Writers Guide](DRIVER_WRITERS_GUIDE.md) - Creating custom drivers
- `bin/pjdbc show <driver>` - Driver-specific parameters
