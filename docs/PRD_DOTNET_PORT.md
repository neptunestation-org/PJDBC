# PRD: Pjdbc.Net - PJDBC Port to .NET

## Overview

**Project Name:** Pjdbc.Net
**Version:** 1.0.0
**Status:** Draft
**Author:** David A. Ventimiglia
**Date:** 2026-01-04

### Executive Summary

Pjdbc.Net is a .NET port of PJDBC concepts, providing composable middleware for ADO.NET database connections. It enables SQL transformation, access control, resilience patterns, and testing utilities through a fluent builder API that integrates with .NET's dependency injection and Entity Framework Core.

### Problem Statement

.NET applications using ADO.NET or Entity Framework Core lack a standardized way to:
- Intercept and transform SQL queries at the connection level
- Enforce access control policies transparently
- Add resilience patterns (retry, circuit breaker) without Polly boilerplate everywhere
- Mask sensitive data for non-production environments
- Mock database connections for unit testing without complex setup

Existing solutions require either:
- Scattered `try/catch` blocks with retry logic throughout the codebase
- Custom `DbConnection` wrappers per use case
- Infrastructure-level proxies (added complexity)
- EF Core interceptors (EF-specific, not portable to Dapper/raw ADO.NET)

### Solution

Pjdbc.Net provides a middleware layer that wraps any `DbConnection`, enabling cross-cutting concerns through composable middleware configured via a fluent builder API or dependency injection.

---

## Goals and Non-Goals

### Goals

1. **Zero application code changes**: Middleware applies transparently via connection configuration
2. **Composable middleware**: Stack multiple behaviors (readonly + retry + masking)
3. **Provider-agnostic**: Works with Npgsql, SqlClient, MySqlConnector, etc.
4. **First-class DI support**: Integrates with `IServiceCollection` and `IConfiguration`
5. **EF Core compatible**: Works as underlying connection for Entity Framework Core
6. **Async-first**: Full support for `async/await` and `CancellationToken`
7. **Testing-first**: Built-in mock connection for unit testing

### Non-Goals

1. **Connection pooling**: Use provider-native pooling or external solutions
2. **Caching**: Use `IMemoryCache`, `IDistributedCache`, or application-level caching
3. **Logging/metrics/tracing**: Use `ILogger`, OpenTelemetry, or Application Insights
4. **Load balancing**: Use Azure SQL failover, ProxySQL, or DNS-based solutions
5. **Query building**: Pjdbc.Net operates at the connection level, not query construction

---

## Architecture

### System Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Application Code                      │
│         using (var conn = PjdbcConnection(...))          │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  PjdbcConnection                         │
│           (DbConnection with middleware)                 │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              Middleware Pipeline                         │
│     Readonly → Retry → CircuitBreaker → Mask            │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│        Inner DbConnection (Npgsql, SqlClient, etc.)      │
└─────────────────────────────────────────────────────────┘
```

### Project Structure

```
Pjdbc.Net/
├── Pjdbc.Net.sln
├── src/
│   ├── Pjdbc/
│   │   ├── Pjdbc.csproj
│   │   ├── PjdbcConnection.cs
│   │   ├── PjdbcCommand.cs
│   │   ├── PjdbcDataReader.cs
│   │   ├── PjdbcTransaction.cs
│   │   ├── PjdbcConnectionBuilder.cs
│   │   ├── PjdbcConnectionStringBuilder.cs
│   │   ├── Middleware/
│   │   │   ├── IDbMiddleware.cs
│   │   │   ├── IQueryMiddleware.cs
│   │   │   ├── IResultMiddleware.cs
│   │   │   ├── MiddlewarePipeline.cs
│   │   │   ├── ReadonlyMiddleware.cs
│   │   │   ├── RetryMiddleware.cs
│   │   │   ├── CircuitBreakerMiddleware.cs
│   │   │   ├── TimeoutMiddleware.cs
│   │   │   ├── MaskingMiddleware.cs
│   │   │   ├── SchemaValidationMiddleware.cs
│   │   │   ├── ChaosMiddleware.cs
│   │   │   └── FilterMiddleware.cs
│   │   ├── Configuration/
│   │   │   ├── PjdbcOptions.cs
│   │   │   ├── ReadonlyOptions.cs
│   │   │   ├── RetryOptions.cs
│   │   │   ├── CircuitBreakerOptions.cs
│   │   │   └── MaskingOptions.cs
│   │   └── Exceptions/
│   │       ├── PjdbcException.cs
│   │       ├── ReadonlyViolationException.cs
│   │       ├── SchemaViolationException.cs
│   │       └── CircuitBreakerOpenException.cs
│   ├── Pjdbc.Mock/
│   │   ├── Pjdbc.Mock.csproj
│   │   ├── MockConnection.cs
│   │   ├── MockCommand.cs
│   │   ├── MockDataReader.cs
│   │   ├── MockExpectation.cs
│   │   └── MockDatabase.cs
│   └── Pjdbc.DependencyInjection/
│       ├── Pjdbc.DependencyInjection.csproj
│       └── ServiceCollectionExtensions.cs
└── tests/
    ├── Pjdbc.Tests/
    ├── Pjdbc.Mock.Tests/
    └── Pjdbc.IntegrationTests/
```

### NuGet Packages

| Package | Description | Dependencies |
|---------|-------------|--------------|
| `Pjdbc` | Core middleware framework | None (netstandard2.1) |
| `Pjdbc.Mock` | Mock connection for testing | `Pjdbc` |
| `Pjdbc.DependencyInjection` | DI extensions | `Pjdbc`, `Microsoft.Extensions.DependencyInjection.Abstractions` |

---

## Core Interfaces

### Middleware Interfaces

```csharp
namespace Pjdbc;

/// <summary>
/// Base interface for all database middleware.
/// </summary>
public interface IDbMiddleware
{
    /// <summary>
    /// Execution order in the pipeline. Lower values execute first.
    /// Recommended ranges:
    ///   0-99:   Validation (readonly, schema)
    ///   100-199: Resilience (retry, circuit breaker, timeout)
    ///   200-299: Transformation (filter, mask)
    /// </summary>
    int Order { get; }

    /// <summary>
    /// Wraps a command with middleware behavior.
    /// </summary>
    DbCommand WrapCommand(DbCommand command, PjdbcConnection connection);

    /// <summary>
    /// Wraps a data reader with middleware behavior.
    /// </summary>
    DbDataReader WrapReader(DbDataReader reader, PjdbcCommand command);
}

/// <summary>
/// Middleware that intercepts query execution.
/// </summary>
public interface IQueryMiddleware : IDbMiddleware
{
    /// <summary>
    /// Called before command execution. Can modify or reject the query.
    /// </summary>
    ValueTask<QueryContext> BeforeExecuteAsync(
        QueryContext context,
        CancellationToken cancellationToken = default);

    /// <summary>
    /// Called after command execution. Can handle errors or perform cleanup.
    /// </summary>
    ValueTask AfterExecuteAsync(
        QueryContext context,
        object? result,
        Exception? exception,
        CancellationToken cancellationToken = default);
}

/// <summary>
/// Middleware that transforms result data.
/// </summary>
public interface IResultMiddleware : IDbMiddleware
{
    /// <summary>
    /// Transform a value read from results.
    /// </summary>
    object? TransformValue(string columnName, int columnOrdinal, Type columnType, object? value);
}

/// <summary>
/// Context passed through the middleware pipeline.
/// </summary>
public record QueryContext
{
    public required string CommandText { get; init; }
    public required CommandType CommandType { get; init; }
    public required DbParameterCollection Parameters { get; init; }
    public required CommandBehavior Behavior { get; init; }
    public IDictionary<string, object?> Properties { get; init; } = new Dictionary<string, object?>();

    /// <summary>
    /// Creates a new context with modified command text.
    /// </summary>
    public QueryContext WithCommandText(string commandText) => this with { CommandText = commandText };
}
```

---

## API Design

### Primary API: Fluent Builder

```csharp
namespace Pjdbc;

/// <summary>
/// Fluent builder for creating PjdbcConnection instances.
/// </summary>
public class PjdbcConnectionBuilder
{
    private readonly string _connectionString;
    private DbProviderFactory? _providerFactory;
    private readonly List<IDbMiddleware> _middleware = new();

    public PjdbcConnectionBuilder(string connectionString);

    /// <summary>
    /// Specifies the database provider to use.
    /// </summary>
    public PjdbcConnectionBuilder UseProvider<TConnection>()
        where TConnection : DbConnection, new();

    /// <summary>
    /// Specifies the database provider factory.
    /// </summary>
    public PjdbcConnectionBuilder UseProviderFactory(DbProviderFactory factory);

    /// <summary>
    /// Adds readonly enforcement middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseReadonly(Action<ReadonlyOptions>? configure = null);

    /// <summary>
    /// Adds retry middleware with exponential backoff.
    /// </summary>
    public PjdbcConnectionBuilder UseRetry(Action<RetryOptions>? configure = null);

    /// <summary>
    /// Adds circuit breaker middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseCircuitBreaker(Action<CircuitBreakerOptions>? configure = null);

    /// <summary>
    /// Adds query timeout middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseTimeout(TimeSpan timeout);

    /// <summary>
    /// Adds data masking middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseMasking(Action<MaskingOptions> configure);

    /// <summary>
    /// Adds schema validation middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseSchemaValidation(Action<SchemaValidationOptions> configure);

    /// <summary>
    /// Adds chaos/fault injection middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseChaos(Action<ChaosOptions> configure);

    /// <summary>
    /// Adds SQL transformation middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseFilter(ISqlTransformer transformer);

    /// <summary>
    /// Adds custom middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseMiddleware(IDbMiddleware middleware);

    /// <summary>
    /// Adds custom middleware.
    /// </summary>
    public PjdbcConnectionBuilder UseMiddleware<TMiddleware>()
        where TMiddleware : IDbMiddleware, new();

    /// <summary>
    /// Builds the configured PjdbcConnection.
    /// </summary>
    public PjdbcConnection Build();
}
```

### Usage Examples

```csharp
using Pjdbc;
using Npgsql;

// Example 1: Simple readonly connection
await using var conn = new PjdbcConnectionBuilder("Host=localhost;Database=mydb")
    .UseProvider<NpgsqlConnection>()
    .UseReadonly()
    .Build();

await conn.OpenAsync();

await using var cmd = conn.CreateCommand();
cmd.CommandText = "SELECT * FROM users";
await using var reader = await cmd.ExecuteReaderAsync();

// Example 2: Production-ready configuration
await using var conn = new PjdbcConnectionBuilder("Host=localhost;Database=mydb")
    .UseProvider<NpgsqlConnection>()
    .UseReadonly()
    .UseRetry(opts =>
    {
        opts.MaxAttempts = 3;
        opts.InitialDelay = TimeSpan.FromMilliseconds(100);
        opts.MaxDelay = TimeSpan.FromSeconds(5);
        opts.BackoffMultiplier = 2.0;
    })
    .UseCircuitBreaker(opts =>
    {
        opts.Name = "primary-db";
        opts.FailureThreshold = 5;
        opts.SuccessThreshold = 1;
        opts.ResetTimeout = TimeSpan.FromSeconds(30);
    })
    .UseTimeout(TimeSpan.FromSeconds(30))
    .Build();

// Example 3: Data masking for non-production
await using var conn = new PjdbcConnectionBuilder("Host=localhost;Database=mydb")
    .UseProvider<NpgsqlConnection>()
    .UseMasking(opts =>
    {
        opts.AddColumn("ssn", MaskStrategy.Partial);
        opts.AddColumn("credit_card", MaskStrategy.Partial);
        opts.AddColumn("email", MaskStrategy.Email);
        opts.AddColumn("password", MaskStrategy.Redact);
        opts.ShowLast = 4;
    })
    .Build();

// Example 4: Schema validation
await using var conn = new PjdbcConnectionBuilder("Host=localhost;Database=mydb")
    .UseProvider<NpgsqlConnection>()
    .UseSchemaValidation(opts =>
    {
        opts.Mode = SchemaValidationMode.Whitelist;
        opts.AllowedTables.AddRange(new[] { "users", "orders", "products" });
        opts.BlockedColumns.AddRange(new[] { "internal_notes", "admin_flags" });
    })
    .Build();

// Example 5: Chaos testing
await using var conn = new PjdbcConnectionBuilder("Host=localhost;Database=mydb")
    .UseProvider<NpgsqlConnection>()
    .UseChaos(opts =>
    {
        opts.FailureRate = 0.1;  // 10% failure rate
        opts.Latency = TimeSpan.FromMilliseconds(100);
        opts.LatencyVariance = TimeSpan.FromMilliseconds(50);
    })
    .Build();
```

### Dependency Injection Integration

```csharp
// In Startup.cs or Program.cs
using Pjdbc.DependencyInjection;

// Basic registration
services.AddPjdbc<NpgsqlConnection>(
    Configuration.GetConnectionString("DefaultConnection")!,
    builder => builder
        .UseReadonly()
        .UseRetry()
);

// Named connections
services.AddPjdbc<NpgsqlConnection>(
    "ReadonlyConnection",
    Configuration.GetConnectionString("Readonly")!,
    builder => builder.UseReadonly()
);

services.AddPjdbc<NpgsqlConnection>(
    "MaskedConnection",
    Configuration.GetConnectionString("Default")!,
    builder => builder.UseMasking(opts =>
    {
        opts.AddColumn("ssn", MaskStrategy.Partial);
    })
);

// Usage in services
public class UserRepository
{
    private readonly PjdbcConnection _connection;

    public UserRepository(PjdbcConnection connection)
    {
        _connection = connection;
    }

    // Or with named connections
    public UserRepository([FromKeyedServices("ReadonlyConnection")] PjdbcConnection connection)
    {
        _connection = connection;
    }
}
```

### Entity Framework Core Integration

```csharp
// In DbContext configuration
services.AddDbContext<MyDbContext>((sp, options) =>
{
    var pjdbcConn = new PjdbcConnectionBuilder(
            Configuration.GetConnectionString("DefaultConnection")!)
        .UseProvider<NpgsqlConnection>()
        .UseReadonly()
        .UseRetry()
        .Build();

    options.UseNpgsql(pjdbcConn);
});

// Or with factory pattern
services.AddDbContextFactory<MyDbContext>((sp, options) =>
{
    var connectionFactory = sp.GetRequiredService<IPjdbcConnectionFactory>();
    options.UseNpgsql(connectionFactory.Create());
});
```

### Configuration from appsettings.json

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=mydb"
  },
  "Pjdbc": {
    "DefaultConnection": {
      "Readonly": {
        "Enabled": true,
        "AllowDdl": false
      },
      "Retry": {
        "Enabled": true,
        "MaxAttempts": 3,
        "InitialDelay": "00:00:00.100",
        "MaxDelay": "00:00:05"
      },
      "CircuitBreaker": {
        "Enabled": true,
        "Name": "primary-db",
        "FailureThreshold": 5,
        "ResetTimeout": "00:00:30"
      },
      "Masking": {
        "Columns": {
          "ssn": "Partial",
          "credit_card": "Partial",
          "email": "Email"
        }
      }
    }
  }
}
```

```csharp
// Configuration-driven setup
services.AddPjdbc<NpgsqlConnection>("DefaultConnection", Configuration);
```

---

## Middleware Specifications

### ReadonlyMiddleware

Blocks write operations (INSERT, UPDATE, DELETE, DROP, etc.).

```csharp
public class ReadonlyOptions
{
    /// <summary>Allow CREATE, ALTER, DROP statements.</summary>
    public bool AllowDdl { get; set; } = false;

    /// <summary>Allow INSERT, UPDATE, DELETE statements.</summary>
    public bool AllowDml { get; set; } = false;

    /// <summary>Custom error message for violations.</summary>
    public string? CustomMessage { get; set; }
}
```

**Behavior:**
- Parses SQL to detect write operations
- Throws `ReadonlyViolationException` for blocked queries
- Order: 10

### RetryMiddleware

Retries failed queries with exponential backoff.

```csharp
public class RetryOptions
{
    /// <summary>Maximum retry attempts. Default: 3</summary>
    public int MaxAttempts { get; set; } = 3;

    /// <summary>Initial delay before first retry. Default: 100ms</summary>
    public TimeSpan InitialDelay { get; set; } = TimeSpan.FromMilliseconds(100);

    /// <summary>Maximum delay cap. Default: 5s</summary>
    public TimeSpan MaxDelay { get; set; } = TimeSpan.FromSeconds(5);

    /// <summary>Backoff multiplier. Default: 2.0</summary>
    public double BackoffMultiplier { get; set; } = 2.0;

    /// <summary>Add random jitter to delays. Default: true</summary>
    public bool Jitter { get; set; } = true;

    /// <summary>Custom predicate to determine retryable exceptions.</summary>
    public Func<Exception, bool>? ShouldRetry { get; set; }
}
```

**Default retryable exceptions:**
- `SocketException`
- `IOException` (network-related)
- `TimeoutException`
- PostgreSQL: 40001, 40P01 (deadlock), 57P01 (admin shutdown)
- SQL Server: 1205 (deadlock), -2 (timeout)

**Behavior:**
- Only retries on transient errors
- Respects `CancellationToken`
- Order: 100

### CircuitBreakerMiddleware

Implements the circuit breaker pattern.

```csharp
public class CircuitBreakerOptions
{
    /// <summary>Name for monitoring/identification.</summary>
    public string Name { get; set; } = "default";

    /// <summary>Consecutive failures before opening. Default: 5</summary>
    public int FailureThreshold { get; set; } = 5;

    /// <summary>Consecutive successes to close from half-open. Default: 1</summary>
    public int SuccessThreshold { get; set; } = 1;

    /// <summary>Time before transitioning from open to half-open. Default: 30s</summary>
    public TimeSpan ResetTimeout { get; set; } = TimeSpan.FromSeconds(30);

    /// <summary>Custom predicate to determine circuit-breaking exceptions.</summary>
    public Func<Exception, bool>? ShouldBreak { get; set; }
}

public enum CircuitState
{
    Closed,
    Open,
    HalfOpen
}
```

**Behavior:**
- CLOSED: Normal operation
- OPEN: Throws `CircuitBreakerOpenException` immediately
- HALF_OPEN: Allows test requests
- Order: 110

### TimeoutMiddleware

Enforces query timeout limits.

```csharp
public class TimeoutOptions
{
    /// <summary>Query timeout. Default: 30s</summary>
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(30);

    /// <summary>Attempt to cancel command on timeout. Default: true</summary>
    public bool CancelOnTimeout { get; set; } = true;
}
```

**Behavior:**
- Sets `DbCommand.CommandTimeout`
- Optionally wraps execution with `CancellationTokenSource`
- Order: 120

### MaskingMiddleware

Masks sensitive data in query results.

```csharp
public enum MaskStrategy
{
    /// <summary>Replace entire value: "********"</summary>
    Full,

    /// <summary>Show first/last N chars: "****1234"</summary>
    Partial,

    /// <summary>Preserve first char and domain: "j***@example.com"</summary>
    Email,

    /// <summary>Replace with "[REDACTED]"</summary>
    Redact,

    /// <summary>Replace with hash prefix: "a1b2c3d4..."</summary>
    Hash
}

public class MaskingOptions
{
    /// <summary>Column masking configuration.</summary>
    public Dictionary<string, MaskStrategy> Columns { get; } =
        new(StringComparer.OrdinalIgnoreCase);

    /// <summary>Mask character. Default: '*'</summary>
    public char MaskChar { get; set; } = '*';

    /// <summary>Characters to show at start for Partial. Default: 0</summary>
    public int ShowFirst { get; set; } = 0;

    /// <summary>Characters to show at end for Partial. Default: 4</summary>
    public int ShowLast { get; set; } = 4;

    public void AddColumn(string columnName, MaskStrategy strategy)
    {
        Columns[columnName] = strategy;
    }
}
```

**Behavior:**
- Wraps `DbDataReader` to transform values on read
- Column matching is case-insensitive
- Order: 200

### SchemaValidationMiddleware

Validates queries against allowed/blocked tables and columns.

```csharp
public enum SchemaValidationMode
{
    /// <summary>Only allow explicitly listed tables.</summary>
    Whitelist,

    /// <summary>Block explicitly listed tables, allow others.</summary>
    Blacklist
}

public class SchemaValidationOptions
{
    public SchemaValidationMode Mode { get; set; } = SchemaValidationMode.Whitelist;
    public List<string> AllowedTables { get; } = new();
    public List<string> BlockedTables { get; } = new();
    public List<string> AllowedColumns { get; } = new();
    public List<string> BlockedColumns { get; } = new();
    public bool CaseSensitive { get; set; } = false;
    public string? CustomMessage { get; set; }
}
```

**Behavior:**
- Parses SQL to extract table/column references
- Throws `SchemaViolationException` for violations
- Order: 20

### ChaosMiddleware

Injects failures for resilience testing.

```csharp
public class ChaosOptions
{
    /// <summary>Probability (0.0-1.0) of failure per query.</summary>
    public double FailureRate { get; set; } = 0.0;

    /// <summary>Fixed delay before each query.</summary>
    public TimeSpan Latency { get; set; } = TimeSpan.Zero;

    /// <summary>Random additional delay up to this value.</summary>
    public TimeSpan LatencyVariance { get; set; } = TimeSpan.Zero;

    /// <summary>Probability of closing connection unexpectedly.</summary>
    public double ConnectionDropRate { get; set; } = 0.0;

    /// <summary>Delay per row iteration in results.</summary>
    public TimeSpan ResultSetLatency { get; set; } = TimeSpan.Zero;

    /// <summary>Custom exception message.</summary>
    public string ExceptionMessage { get; set; } = "Pjdbc: Chaos injected failure";
}
```

**Behavior:**
- Probabilistic failure injection
- Configurable latency injection
- Order: 500

### FilterMiddleware

Transforms SQL queries before execution.

```csharp
public interface ISqlTransformer
{
    string Transform(string sql);
}

// Built-in transformers
public static class SqlTransformers
{
    public static ISqlTransformer ReplaceTable(string oldTable, string newTable);
    public static ISqlTransformer AddSchema(string schema);
    public static ISqlTransformer Composite(params ISqlTransformer[] transformers);
}
```

**Behavior:**
- Calls transformer before query execution
- Order: 50

---

## Mock Package

### MockConnection

```csharp
namespace Pjdbc.Mock;

/// <summary>
/// In-memory mock database connection for unit testing.
/// </summary>
public class MockConnection : DbConnection
{
    private readonly MockDatabase _database;
    private readonly List<MockExpectation> _expectations = new();
    private readonly List<ExecutedCommand> _executedCommands = new();

    public MockConnection(MockDatabase? database = null);

    /// <summary>
    /// Sets up an expected query.
    /// </summary>
    public MockExpectation Expect(string commandText);

    /// <summary>
    /// Sets up an expected query with regex pattern matching.
    /// </summary>
    public MockExpectation ExpectPattern(string pattern);

    /// <summary>
    /// Enables strict mode where unexpected queries throw.
    /// </summary>
    public MockConnection Strict();

    /// <summary>
    /// Verifies all expectations were met.
    /// </summary>
    public void VerifyAll();

    /// <summary>
    /// Gets all executed commands for inspection.
    /// </summary>
    public IReadOnlyList<ExecutedCommand> ExecutedCommands => _executedCommands;

    /// <summary>
    /// Resets expectations and executed commands.
    /// </summary>
    public void Reset();
}

public record ExecutedCommand(string CommandText, IReadOnlyList<object?> Parameters);
```

### MockExpectation

```csharp
namespace Pjdbc.Mock;

public class MockExpectation
{
    /// <summary>
    /// Specifies expected parameters.
    /// </summary>
    public MockExpectation WithParameters(params object?[] parameters);

    /// <summary>
    /// Specifies the result to return.
    /// </summary>
    public MockExpectation Returns(string[] columns, params object?[][] rows);

    /// <summary>
    /// Specifies the result using a builder.
    /// </summary>
    public MockExpectation Returns(Action<MockResultBuilder> configure);

    /// <summary>
    /// Specifies an exception to throw.
    /// </summary>
    public MockExpectation Throws<TException>() where TException : Exception, new();

    /// <summary>
    /// Specifies an exception to throw.
    /// </summary>
    public MockExpectation Throws(Exception exception);

    /// <summary>
    /// Specifies the result for ExecuteNonQuery.
    /// </summary>
    public MockExpectation ReturnsRowsAffected(int rowsAffected);

    /// <summary>
    /// Specifies the result for ExecuteScalar.
    /// </summary>
    public MockExpectation ReturnsScalar(object? value);

    /// <summary>
    /// Specifies this expectation can be matched multiple times.
    /// </summary>
    public MockExpectation Times(int count);

    /// <summary>
    /// Specifies this expectation can be matched any number of times.
    /// </summary>
    public MockExpectation AnyTimes();
}

public class MockResultBuilder
{
    public MockResultBuilder WithColumns(params string[] columns);
    public MockResultBuilder AddRow(params object?[] values);
}
```

### Usage Example

```csharp
using Pjdbc.Mock;
using Xunit;

public class UserRepositoryTests
{
    [Fact]
    public async Task FindByIdAsync_ReturnsUser_WhenExists()
    {
        // Arrange
        var mock = new MockConnection();
        mock.Expect("SELECT id, name, email FROM users WHERE id = @id")
            .WithParameters(1)
            .Returns(
                new[] { "id", "name", "email" },
                new object?[] { 1, "Alice", "alice@example.com" });

        var repo = new UserRepository(mock);

        // Act
        var user = await repo.FindByIdAsync(1);

        // Assert
        Assert.NotNull(user);
        Assert.Equal("Alice", user.Name);
        Assert.Equal("alice@example.com", user.Email);
        mock.VerifyAll();
    }

    [Fact]
    public async Task FindByIdAsync_ReturnsNull_WhenNotExists()
    {
        // Arrange
        var mock = new MockConnection();
        mock.Expect("SELECT id, name, email FROM users WHERE id = @id")
            .WithParameters(999)
            .Returns(new[] { "id", "name", "email" }); // Empty result

        var repo = new UserRepository(mock);

        // Act
        var user = await repo.FindByIdAsync(999);

        // Assert
        Assert.Null(user);
        mock.VerifyAll();
    }

    [Fact]
    public async Task CreateAsync_ThrowsOnDuplicate()
    {
        // Arrange
        var mock = new MockConnection();
        mock.Expect("INSERT INTO users (name, email) VALUES (@name, @email)")
            .WithParameters("Alice", "alice@example.com")
            .Throws(new PostgresException("duplicate key value", "23505"));

        var repo = new UserRepository(mock);

        // Act & Assert
        await Assert.ThrowsAsync<DuplicateUserException>(
            () => repo.CreateAsync(new User { Name = "Alice", Email = "alice@example.com" }));
    }

    [Fact]
    public async Task BulkInsert_ExecutesMultipleTimes()
    {
        // Arrange
        var mock = new MockConnection();
        mock.Expect("INSERT INTO users (name) VALUES (@name)")
            .Times(3)
            .ReturnsRowsAffected(1);

        var repo = new UserRepository(mock);

        // Act
        await repo.BulkInsertAsync(new[] { "Alice", "Bob", "Charlie" });

        // Assert
        Assert.Equal(3, mock.ExecutedCommands.Count);
        mock.VerifyAll();
    }
}
```

---

## Exception Types

```csharp
namespace Pjdbc;

/// <summary>
/// Base exception for all Pjdbc errors.
/// </summary>
public class PjdbcException : DbException
{
    public PjdbcException(string message) : base(message) { }
    public PjdbcException(string message, Exception inner) : base(message, inner) { }
}

/// <summary>
/// Thrown when a write operation is blocked by ReadonlyMiddleware.
/// </summary>
public class ReadonlyViolationException : PjdbcException
{
    public string CommandText { get; }
    public string Operation { get; } // INSERT, UPDATE, DELETE, etc.

    public ReadonlyViolationException(string commandText, string operation)
        : base($"Write operation '{operation}' blocked: {Truncate(commandText, 100)}")
    {
        CommandText = commandText;
        Operation = operation;
    }
}

/// <summary>
/// Thrown when a query violates schema validation rules.
/// </summary>
public class SchemaViolationException : PjdbcException
{
    public string CommandText { get; }
    public IReadOnlyList<string> ViolatingTables { get; }
    public IReadOnlyList<string> ViolatingColumns { get; }

    public SchemaViolationException(
        string commandText,
        IEnumerable<string> tables,
        IEnumerable<string> columns)
        : base($"Schema violation: {FormatViolations(tables, columns)}")
    {
        CommandText = commandText;
        ViolatingTables = tables.ToList();
        ViolatingColumns = columns.ToList();
    }
}

/// <summary>
/// Thrown when circuit breaker is open.
/// </summary>
public class CircuitBreakerOpenException : PjdbcException
{
    public string CircuitName { get; }
    public TimeSpan RemainingTimeout { get; }

    public CircuitBreakerOpenException(string name, TimeSpan remaining)
        : base($"Circuit breaker '{name}' is open. Retry after {remaining.TotalSeconds:F1}s")
    {
        CircuitName = name;
        RemainingTimeout = remaining;
    }
}
```

---

## DI Extensions

```csharp
namespace Pjdbc.DependencyInjection;

public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Adds a PjdbcConnection to the service collection.
    /// </summary>
    public static IServiceCollection AddPjdbc<TConnection>(
        this IServiceCollection services,
        string connectionString,
        Action<PjdbcConnectionBuilder>? configure = null)
        where TConnection : DbConnection, new();

    /// <summary>
    /// Adds a named PjdbcConnection to the service collection.
    /// </summary>
    public static IServiceCollection AddPjdbc<TConnection>(
        this IServiceCollection services,
        string name,
        string connectionString,
        Action<PjdbcConnectionBuilder>? configure = null)
        where TConnection : DbConnection, new();

    /// <summary>
    /// Adds a PjdbcConnection configured from IConfiguration.
    /// </summary>
    public static IServiceCollection AddPjdbc<TConnection>(
        this IServiceCollection services,
        string connectionStringName,
        IConfiguration configuration)
        where TConnection : DbConnection, new();

    /// <summary>
    /// Adds a connection factory for creating PjdbcConnections.
    /// </summary>
    public static IServiceCollection AddPjdbcFactory<TConnection>(
        this IServiceCollection services,
        string connectionString,
        Action<PjdbcConnectionBuilder>? configure = null)
        where TConnection : DbConnection, new();
}

/// <summary>
/// Factory for creating PjdbcConnection instances.
/// </summary>
public interface IPjdbcConnectionFactory
{
    PjdbcConnection Create();
}
```

---

## Testing Strategy

### Unit Tests

- Each middleware tested in isolation
- Mock underlying `DbConnection`
- Test edge cases (empty results, errors, cancellation)

### Integration Tests

- Test against real databases via Docker/Testcontainers
- Verify middleware composition order
- Test with Npgsql, SqlClient, MySqlConnector

### Benchmarks

```csharp
[MemoryDiagnoser]
public class MiddlewareBenchmarks
{
    [Benchmark(Baseline = true)]
    public async Task NoMiddleware() { }

    [Benchmark]
    public async Task ReadonlyMiddleware() { }

    [Benchmark]
    public async Task RetryMiddleware() { }

    [Benchmark]
    public async Task FullStack() { }
}
```

---

## Compatibility Matrix

| Provider | Package | Tested |
|----------|---------|--------|
| PostgreSQL | `Npgsql` | Yes |
| SQL Server | `Microsoft.Data.SqlClient` | Yes |
| MySQL | `MySqlConnector` | Yes |
| SQLite | `Microsoft.Data.Sqlite` | Yes |
| Oracle | `Oracle.ManagedDataAccess.Core` | Planned |

| Framework | Version | Tested |
|-----------|---------|--------|
| .NET | 8.0 | Yes |
| .NET | 9.0 | Yes |
| .NET Standard | 2.1 | Yes |

---

## Release Plan

### v0.1.0 - Foundation
- Core middleware interface
- `PjdbcConnection`, `PjdbcCommand`, `PjdbcDataReader`
- Readonly middleware
- Mock package
- Basic documentation

### v0.2.0 - Resilience
- Retry middleware
- Circuit breaker middleware
- Timeout middleware

### v0.3.0 - Security
- Masking middleware
- Schema validation middleware

### v0.4.0 - Integration
- DI package
- Configuration binding
- EF Core integration examples
- Filter middleware

### v0.5.0 - Testing
- Chaos middleware
- Enhanced mock features
- Sink connection

### v1.0.0 - Stable Release
- API stability guarantee
- Comprehensive documentation
- Performance benchmarks
- All providers tested

---

## Open Questions

1. **Polly integration**: Should retry/circuit breaker delegate to Polly, or remain standalone?
2. **EF Core interceptors**: Should we provide EF Core interceptor adapters for middleware?
3. **Health checks**: Should we provide `IHealthCheck` implementations for circuit breaker state?
4. **Metrics**: Should we expose middleware metrics via `System.Diagnostics.Metrics`?
5. **Source generators**: Could we use source generators for optimized SQL parsing?

---

## References

- [ADO.NET documentation](https://learn.microsoft.com/en-us/dotnet/framework/data/adonet/)
- [DbConnection class](https://learn.microsoft.com/en-us/dotnet/api/system.data.common.dbconnection)
- [EF Core interceptors](https://learn.microsoft.com/en-us/ef/core/logging-events-diagnostics/interceptors)
- [PJDBC Java implementation](https://github.com/neptunestation-org/PJDBC)
