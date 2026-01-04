# PRD: pgdbc-go - PJDBC Port to Go

## Overview

**Project Name:** pgdbc-go
**Version:** 1.0.0
**Status:** Draft
**Author:** David A. Ventimiglia
**Date:** 2026-01-04

### Executive Summary

pgdbc-go is a Go port of PJDBC concepts, providing composable middleware for Go's `database/sql` interface. It enables SQL transformation, access control, resilience patterns, and testing utilities without requiring application code changes.

### Problem Statement

Go applications using `database/sql` lack a standardized way to:
- Intercept and transform SQL queries
- Enforce access control at the driver level
- Add resilience patterns (retry, circuit breaker) transparently
- Mock database connections for testing

Existing solutions require either:
- Application-level code changes scattered throughout the codebase
- Database proxy servers (added infrastructure complexity)
- Driver-specific implementations (not portable)

### Solution

pgdbc-go provides a middleware layer that wraps any `database/sql`-compatible driver, enabling cross-cutting concerns through composable middleware configured via functional options or DSN parameters.

---

## Goals and Non-Goals

### Goals

1. **Zero application code changes**: Middleware applies transparently via connection configuration
2. **Composable middleware**: Stack multiple behaviors (readonly + retry + masking)
3. **Driver-agnostic**: Works with any `database/sql` compatible driver (pgx, mysql, sqlite3, etc.)
4. **Zero runtime dependencies**: Core package has no external dependencies
5. **Go-idiomatic API**: Functional options, context-aware, explicit error handling
6. **Testing-first**: Built-in mock driver for unit testing

### Non-Goals

1. **Connection pooling**: Use `database/sql`'s built-in pooler or external solutions
2. **Caching**: Use application-level caching (go-cache, groupcache, Redis)
3. **Logging/metrics/tracing**: Use OpenTelemetry, Prometheus client, or similar
4. **Load balancing**: Use ProxySQL, PgBouncer, or DNS-based solutions
5. **ORM features**: pgdbc-go operates at the driver level, not query building

---

## Architecture

### System Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Application Code                      │
│              sql.Open("pgdbc", dsn)                      │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   database/sql                           │
│            (connection pooling, etc.)                    │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  pgdbc DriverChain                       │
│     Readonly → Retry → CircuitBreaker → Delegate        │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              Underlying Driver (pgx, mysql, etc.)        │
└─────────────────────────────────────────────────────────┘
```

### Package Structure

```
pgdbc-go/
├── go.mod
├── go.sum
├── README.md
├── LICENSE
├── pgdbc.go              # Main entry point, Open(), Register()
├── driver.go             # driver.Driver implementation
├── conn.go               # driver.Conn wrapper
├── stmt.go               # driver.Stmt wrapper
├── rows.go               # driver.Rows wrapper
├── dsn.go                # DSN parsing utilities
├── errors.go             # Error types
├── middleware/
│   ├── middleware.go     # Middleware interface definitions
│   ├── readonly.go       # Read-only enforcement
│   ├── retry.go          # Retry with exponential backoff
│   ├── circuitbreaker.go # Circuit breaker pattern
│   ├── timeout.go        # Query timeout enforcement
│   ├── mask.go           # Data masking in results
│   ├── schema.go         # Schema validation (whitelist/blacklist)
│   ├── chaos.go          # Fault injection for testing
│   ├── filter.go         # SQL transformation
│   └── tee.go            # Write replication
├── mock/
│   ├── mock.go           # Mock driver implementation
│   ├── expectation.go    # Query expectations
│   └── rows.go           # Mock rows implementation
└── sink/
    └── sink.go           # Discard driver for benchmarking
```

---

## Core Interfaces

### Middleware Interface

```go
package pgdbc

import (
    "context"
    "database/sql/driver"
)

// Middleware wraps database operations with additional behavior.
// Implementations should be safe for concurrent use.
type Middleware interface {
    // Order returns the execution order. Lower values execute first.
    // Recommended ranges:
    //   0-99:   Validation (readonly, schema)
    //   100-199: Resilience (retry, circuit breaker, timeout)
    //   200-299: Transformation (filter, mask)
    //   900+:    Observability (would be external)
    Order() int

    // WrapConn wraps a connection with middleware behavior.
    WrapConn(conn driver.Conn) driver.Conn
}

// QueryMiddleware intercepts query execution specifically.
type QueryMiddleware interface {
    Middleware

    // BeforeQuery is called before query execution.
    // Returns modified query and args, or error to abort.
    BeforeQuery(ctx context.Context, query string, args []driver.NamedValue) (string, []driver.NamedValue, error)

    // AfterQuery is called after query execution.
    // Can handle errors or perform cleanup.
    AfterQuery(ctx context.Context, query string, result driver.Result, err error) error
}

// ResultMiddleware intercepts result set iteration.
type ResultMiddleware interface {
    Middleware

    // WrapRows wraps a Rows iterator with transformation behavior.
    WrapRows(rows driver.Rows) driver.Rows
}

// ExecMiddleware intercepts statement execution (INSERT/UPDATE/DELETE).
type ExecMiddleware interface {
    Middleware

    // BeforeExec is called before statement execution.
    BeforeExec(ctx context.Context, query string, args []driver.NamedValue) (string, []driver.NamedValue, error)

    // AfterExec is called after statement execution.
    AfterExec(ctx context.Context, query string, result driver.Result, err error) error
}
```

### Option Type

```go
package pgdbc

// Option configures a pgdbc connection.
type Option func(*config)

// config holds internal configuration.
type config struct {
    driverName     string
    dsn            string
    middleware     []Middleware
    innerConnector driver.Connector
}
```

---

## API Design

### Primary API: Functional Options

```go
package pgdbc

import (
    "database/sql"
)

// Open creates a new database connection with the specified middleware.
// The driverName should be a registered database/sql driver (e.g., "pgx", "mysql").
func Open(driverName, dsn string, opts ...Option) (*sql.DB, error)

// OpenDB creates a *sql.DB from a driver.Connector with middleware.
func OpenDB(connector driver.Connector, opts ...Option) *sql.DB

// MustOpen is like Open but panics on error. Useful for package-level vars.
func MustOpen(driverName, dsn string, opts ...Option) *sql.DB
```

### Usage Examples

```go
package main

import (
    "context"
    "database/sql"
    "log"
    "time"

    "github.com/neptunestation-org/pgdbc-go"
    "github.com/neptunestation-org/pgdbc-go/middleware"
    _ "github.com/jackc/pgx/v5/stdlib"
)

func main() {
    // Example 1: Simple readonly connection
    db, err := pgdbc.Open("pgx", "postgres://localhost/mydb",
        middleware.Readonly(),
    )
    if err != nil {
        log.Fatal(err)
    }
    defer db.Close()

    // Example 2: Production-ready configuration
    db, err := pgdbc.Open("pgx", "postgres://localhost/mydb",
        middleware.Readonly(),
        middleware.Retry(middleware.RetryConfig{
            MaxAttempts:       3,
            InitialDelay:      100 * time.Millisecond,
            MaxDelay:          5 * time.Second,
            BackoffMultiplier: 2.0,
            Jitter:            true,
        }),
        middleware.CircuitBreaker(middleware.CircuitBreakerConfig{
            Name:             "primary-db",
            FailureThreshold: 5,
            SuccessThreshold: 1,
            ResetTimeout:     30 * time.Second,
        }),
        middleware.Timeout(30 * time.Second),
    )

    // Example 3: Data masking for non-production
    db, err := pgdbc.Open("pgx", "postgres://localhost/mydb",
        middleware.Mask(middleware.MaskConfig{
            Columns: map[string]middleware.MaskStrategy{
                "ssn":         middleware.MaskPartial,
                "credit_card": middleware.MaskPartial,
                "email":       middleware.MaskEmail,
                "password":    middleware.MaskRedact,
            },
            ShowLast: 4,
        }),
    )

    // Example 4: Schema validation
    db, err := pgdbc.Open("pgx", "postgres://localhost/mydb",
        middleware.SchemaValidation(middleware.SchemaConfig{
            Mode:          middleware.SchemaWhitelist,
            AllowedTables: []string{"users", "orders", "products"},
            BlockedColumns: []string{"internal_notes", "admin_flags"},
        }),
    )

    // Example 5: Chaos testing
    db, err := pgdbc.Open("pgx", "postgres://localhost/mydb",
        middleware.Chaos(middleware.ChaosConfig{
            FailureRate:    0.1,  // 10% of queries fail
            Latency:        100 * time.Millisecond,
            LatencyVariance: 50 * time.Millisecond,
        }),
    )

    // Use db as normal *sql.DB
    ctx := context.Background()
    rows, err := db.QueryContext(ctx, "SELECT id, name FROM users")
    // ...
}
```

### Secondary API: Driver Registration

For configuration-driven setups where connection strings come from config files:

```go
package main

import (
    "database/sql"

    "github.com/neptunestation-org/pgdbc-go"
    "github.com/neptunestation-org/pgdbc-go/middleware"
)

func init() {
    // Register a named driver with preset middleware
    pgdbc.Register("myapp-readonly", "pgx",
        middleware.Readonly(),
        middleware.Retry(middleware.RetryConfig{MaxAttempts: 3}),
    )

    pgdbc.Register("myapp-masked", "pgx",
        middleware.Mask(middleware.MaskConfig{
            Columns: map[string]middleware.MaskStrategy{
                "ssn": middleware.MaskPartial,
            },
        }),
    )
}

func main() {
    // Use registered driver name with standard sql.Open
    db, err := sql.Open("myapp-readonly", "postgres://localhost/mydb")
    // ...
}
```

### DSN-Based Configuration (Optional)

For simpler cases, middleware can be configured via DSN query parameters:

```go
// Query parameter format
dsn := "postgres://localhost/mydb?pgdbc.readonly=true&pgdbc.retry.maxAttempts=3"

db, err := pgdbc.OpenDSN("pgx", dsn)
```

Supported DSN parameters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `pgdbc.readonly` | bool | Enable readonly mode |
| `pgdbc.retry.maxAttempts` | int | Max retry attempts |
| `pgdbc.retry.initialDelay` | duration | Initial retry delay |
| `pgdbc.timeout` | duration | Query timeout |
| `pgdbc.circuitbreaker.threshold` | int | Failure threshold |

---

## Middleware Specifications

### Readonly Middleware

Blocks write operations (INSERT, UPDATE, DELETE, DROP, etc.).

```go
type ReadonlyConfig struct {
    AllowDDL bool   // Allow CREATE, ALTER, DROP (default: false)
    AllowDML bool   // Allow INSERT, UPDATE, DELETE (default: false)
    Message  string // Custom error message
}

func Readonly(opts ...ReadonlyOption) Middleware
```

**Behavior:**
- Parses SQL to detect write operations
- Returns `ErrReadonlyViolation` for blocked queries
- Order: 10 (validates early)

### Retry Middleware

Retries failed queries with exponential backoff.

```go
type RetryConfig struct {
    MaxAttempts       int           // Default: 3
    InitialDelay      time.Duration // Default: 100ms
    MaxDelay          time.Duration // Default: 5s
    BackoffMultiplier float64       // Default: 2.0
    Jitter            bool          // Default: true
    RetryableErrors   func(error) bool // Custom predicate
}

func Retry(cfg RetryConfig) Middleware
```

**Default retryable errors:**
- Connection refused/reset
- Deadlock detected (PostgreSQL 40001, 40P01)
- Serialization failure
- Admin shutdown (57P01)
- Timeout errors

**Behavior:**
- Only retries on transient errors
- Respects context cancellation
- Order: 100

### Circuit Breaker Middleware

Implements the circuit breaker pattern for fault tolerance.

```go
type CircuitBreakerConfig struct {
    Name             string        // For monitoring/metrics
    FailureThreshold int           // Failures before open (default: 5)
    SuccessThreshold int           // Successes to close (default: 1)
    ResetTimeout     time.Duration // Time before half-open (default: 30s)
}

type CircuitState int

const (
    CircuitClosed CircuitState = iota
    CircuitOpen
    CircuitHalfOpen
)

func CircuitBreaker(cfg CircuitBreakerConfig) Middleware

// GetCircuitBreaker retrieves circuit breaker state for monitoring
func GetCircuitBreaker(db *sql.DB) (*CircuitBreakerState, error)
```

**Behavior:**
- CLOSED: Normal operation
- OPEN: Fail fast without hitting database
- HALF_OPEN: Test with limited requests
- Order: 110

### Timeout Middleware

Enforces query timeout limits.

```go
func Timeout(d time.Duration) Middleware
```

**Behavior:**
- Wraps context with timeout
- Cancels query if timeout exceeded
- Order: 120

### Mask Middleware

Masks sensitive data in query results.

```go
type MaskStrategy int

const (
    MaskFull    MaskStrategy = iota // "********"
    MaskPartial                      // "****1234"
    MaskEmail                        // "j***@example.com"
    MaskRedact                       // "[REDACTED]"
    MaskHash                         // "a1b2c3d4..."
)

type MaskConfig struct {
    Columns  map[string]MaskStrategy // column name -> strategy
    ShowFirst int                    // chars to show at start (default: 0)
    ShowLast  int                    // chars to show at end (default: 4)
    MaskChar  rune                   // mask character (default: '*')
}

func Mask(cfg MaskConfig) Middleware
```

**Behavior:**
- Wraps driver.Rows to transform values on read
- Column matching is case-insensitive
- Order: 200

### Schema Validation Middleware

Validates queries against allowed/blocked tables and columns.

```go
type SchemaMode int

const (
    SchemaWhitelist SchemaMode = iota
    SchemaBlacklist
)

type SchemaConfig struct {
    Mode           SchemaMode
    AllowedTables  []string // whitelist mode
    BlockedTables  []string // blacklist mode
    AllowedColumns []string
    BlockedColumns []string
    CaseSensitive  bool
}

func SchemaValidation(cfg SchemaConfig) Middleware
```

**Behavior:**
- Parses SQL to extract table/column references
- Returns `ErrSchemaViolation` for violations
- Order: 20

### Chaos Middleware

Injects failures for resilience testing.

```go
type ChaosConfig struct {
    FailureRate        float64       // 0.0-1.0 probability of failure
    Latency            time.Duration // fixed delay per query
    LatencyVariance    time.Duration // random additional delay
    ConnectionDropRate float64       // probability of connection close
    ResultSetLatency   time.Duration // delay per row iteration
    ExceptionMessage   string        // custom error message
}

func Chaos(cfg ChaosConfig) Middleware
```

**Behavior:**
- Probabilistic failure injection
- Configurable latency injection
- Order: 500

### Filter Middleware

Transforms SQL queries before execution.

```go
type SQLTransformer interface {
    Transform(sql string) (string, error)
}

type FilterConfig struct {
    Transformer SQLTransformer
}

func Filter(cfg FilterConfig) Middleware

// Helper transformers
func ReplaceTable(old, new string) SQLTransformer
func AddSchema(schema string) SQLTransformer
```

**Behavior:**
- Calls transformer before query execution
- Order: 50

---

## Mock Package

### Mock Driver

```go
package mock

import "database/sql/driver"

// DB is an in-memory mock database for testing.
type DB struct {
    expectations []*Expectation
    strict       bool // fail on unexpected queries
}

// New creates a new mock database.
func New() *DB

// Expect sets up an expected query.
func (db *DB) Expect(query string) *Expectation

// ExpectRegex sets up an expected query with regex matching.
func (db *DB) ExpectRegex(pattern string) *Expectation

// Strict enables strict mode where unexpected queries fail.
func (db *DB) Strict() *DB

// AssertExpectationsMet verifies all expectations were executed.
func (db *DB) AssertExpectationsMet(t testing.TB)

// Reset clears all expectations and recorded queries.
func (db *DB) Reset()
```

### Expectation Builder

```go
package mock

type Expectation struct {
    query      string
    args       []driver.Value
    columns    []string
    rows       [][]driver.Value
    err        error
    rowsAffected int64
    lastInsertId int64
}

func (e *Expectation) WithArgs(args ...any) *Expectation
func (e *Expectation) WillReturn(columns []string, rows ...[]any) *Expectation
func (e *Expectation) WillReturnRows(rows *Rows) *Expectation
func (e *Expectation) WillReturnError(err error) *Expectation
func (e *Expectation) WillReturnResult(rowsAffected, lastInsertId int64) *Expectation
```

### Usage Example

```go
package myapp_test

import (
    "testing"

    "github.com/neptunestation-org/pgdbc-go"
    "github.com/neptunestation-org/pgdbc-go/mock"
)

func TestUserRepository_FindByID(t *testing.T) {
    mockDB := mock.New()
    mockDB.Expect("SELECT id, name, email FROM users WHERE id = $1").
        WithArgs(1).
        WillReturn(
            []string{"id", "name", "email"},
            []any{1, "Alice", "alice@example.com"},
        )

    db, err := pgdbc.OpenMock(mockDB)
    if err != nil {
        t.Fatal(err)
    }

    repo := NewUserRepository(db)
    user, err := repo.FindByID(context.Background(), 1)

    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if user.Name != "Alice" {
        t.Errorf("expected Alice, got %s", user.Name)
    }

    mockDB.AssertExpectationsMet(t)
}

func TestUserRepository_FindByID_NotFound(t *testing.T) {
    mockDB := mock.New()
    mockDB.Expect("SELECT id, name, email FROM users WHERE id = $1").
        WithArgs(999).
        WillReturn([]string{"id", "name", "email"}) // empty result

    db, _ := pgdbc.OpenMock(mockDB)
    repo := NewUserRepository(db)

    _, err := repo.FindByID(context.Background(), 999)

    if !errors.Is(err, ErrUserNotFound) {
        t.Errorf("expected ErrUserNotFound, got %v", err)
    }
}
```

---

## Error Types

```go
package pgdbc

import "errors"

var (
    // ErrReadonlyViolation is returned when a write query is blocked.
    ErrReadonlyViolation = errors.New("pgdbc: readonly violation")

    // ErrSchemaViolation is returned when a query accesses blocked tables/columns.
    ErrSchemaViolation = errors.New("pgdbc: schema violation")

    // ErrCircuitOpen is returned when circuit breaker is open.
    ErrCircuitOpen = errors.New("pgdbc: circuit breaker open")

    // ErrRateLimitExceeded is returned when rate limit is exceeded.
    ErrRateLimitExceeded = errors.New("pgdbc: rate limit exceeded")

    // ErrChaosInjected is returned by chaos middleware.
    ErrChaosInjected = errors.New("pgdbc: chaos injected failure")
)

// ReadonlyViolationError provides details about the blocked query.
type ReadonlyViolationError struct {
    Query     string
    Operation string // INSERT, UPDATE, DELETE, etc.
}

func (e *ReadonlyViolationError) Error() string
func (e *ReadonlyViolationError) Unwrap() error // returns ErrReadonlyViolation
```

---

## Testing Strategy

### Unit Tests

- Each middleware tested in isolation
- Mock underlying driver.Conn
- Test edge cases (empty results, errors, cancellation)

### Integration Tests

- Test against real databases via Docker/testcontainers
- Verify middleware composition order
- Test with pgx, mysql, sqlite3 drivers

### Benchmarks

```go
func BenchmarkNoMiddleware(b *testing.B)
func BenchmarkReadonlyMiddleware(b *testing.B)
func BenchmarkRetryMiddleware(b *testing.B)
func BenchmarkFullStack(b *testing.B)
```

---

## Compatibility Matrix

| Driver | Package | Tested |
|--------|---------|--------|
| PostgreSQL | `github.com/jackc/pgx/v5/stdlib` | Yes |
| PostgreSQL | `github.com/lib/pq` | Yes |
| MySQL | `github.com/go-sql-driver/mysql` | Yes |
| SQLite | `github.com/mattn/go-sqlite3` | Yes |
| SQL Server | `github.com/denisenkom/go-mssqldb` | Planned |

---

## Release Plan

### v0.1.0 - Foundation
- Core middleware interface
- Readonly middleware
- Mock driver
- Basic documentation

### v0.2.0 - Resilience
- Retry middleware
- Circuit breaker middleware
- Timeout middleware

### v0.3.0 - Security
- Mask middleware
- Schema validation middleware

### v0.4.0 - Testing & Transformation
- Chaos middleware
- Filter middleware
- Sink driver

### v1.0.0 - Stable Release
- API stability guarantee
- Comprehensive documentation
- Performance benchmarks
- All drivers tested

---

## Open Questions

1. **Middleware ordering**: Should order be explicit (user-defined) or implicit (by type)?
2. **DSN format**: How much configuration should be DSN-parseable vs code-only?
3. **Context propagation**: How to pass middleware-specific context (e.g., "this query is idempotent")?
4. **Metrics hooks**: Should we provide hooks for external metrics systems without depending on them?

---

## References

- [database/sql documentation](https://pkg.go.dev/database/sql)
- [database/sql/driver documentation](https://pkg.go.dev/database/sql/driver)
- [PJDBC Java implementation](https://github.com/neptunestation-org/PJDBC)
