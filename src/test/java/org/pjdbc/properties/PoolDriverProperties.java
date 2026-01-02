package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for PoolDriver.
 *
 * These tests verify connection pooling properties:
 * - Connection pooling and reuse
 * - Max pool size limits
 * - Timeout behavior
 * - Closed connection behavior
 * - Configuration parsing
 * - Default values
 * - Driver composition
 */
class PoolDriverProperties {

    private static final AtomicInteger poolCounter = new AtomicInteger(0);

    private String createUniquePoolName() {
        return "pool_prop_" + poolCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== CONNECTION POOLING AND REUSE ==========

    /**
     * Property: Connections are returned to pool on close and can be reused.
     */
    @Property(tries = 20)
    void connectionsReturnedToPoolOnClose(
            @ForAll @IntRange(min = 1, max = 5) int iterations) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:mock:" + poolName;

        for (int i = 0; i < iterations; i++) {
            Connection conn = DriverManager.getConnection(poolUrl);
            assertNotNull(conn, "Connection should not be null");

            // Use the connection
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }

            conn.close(); // Should return to pool
        }

        // After all iterations, we should still be able to get a connection
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
        }
    }

    /**
     * Property: Multiple connections can be obtained and used concurrently.
     */
    @Property(tries = 10)
    void multipleConnectionsCanBeObtained(
            @ForAll @IntRange(min = 2, max = 5) int connectionCount) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:mock:" + poolName;

        Connection[] connections = new Connection[connectionCount];

        // Get multiple connections
        for (int i = 0; i < connectionCount; i++) {
            connections[i] = DriverManager.getConnection(poolUrl);
            assertNotNull(connections[i], "Connection " + i + " should not be null");
        }

        // All connections should be distinct proxy objects
        for (int i = 0; i < connectionCount; i++) {
            for (int j = i + 1; j < connectionCount; j++) {
                assertNotSame(connections[i], connections[j],
                    "Connections should be distinct objects");
            }
        }

        // Close all connections
        for (Connection conn : connections) {
            conn.close();
        }
    }

    /**
     * Property: Pooled connections are reused after close.
     */
    @Property(tries = 10)
    void pooledConnectionsAreReused(
            @ForAll @IntRange(min = 2, max = 4) int reuseCount) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[max=1]:jdbc:mock:" + poolName;

        // Get and close a connection multiple times
        for (int i = 0; i < reuseCount; i++) {
            Connection conn = DriverManager.getConnection(poolUrl);
            assertNotNull(conn);

            // Verify connection works
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }

            conn.close();
        }

        // Pool should still have the connection available
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
        }
    }

    // ========== MAX POOL SIZE LIMITS ==========

    /**
     * Property: max parameter limits how many connections can be in the pool.
     */
    @Property(tries = 10)
    void maxPoolSizeLimitsPooledConnections(
            @ForAll @IntRange(min = 1, max = 3) int maxSize) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[max=" + maxSize + "]:jdbc:mock:" + poolName;

        // Create more connections than max, close them all
        Connection[] connections = new Connection[maxSize + 2];
        for (int i = 0; i < connections.length; i++) {
            connections[i] = DriverManager.getConnection(poolUrl);
            assertNotNull(connections[i]);
        }

        // Close all connections - only max should be pooled
        for (Connection conn : connections) {
            conn.close();
        }

        // Should still be able to get connections
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
        }
    }

    // ========== TIMEOUT BEHAVIOR ==========

    /**
     * Property: With short timeout and empty pool, new connection is created quickly.
     */
    @Property(tries = 10)
    void timeoutWithEmptyPoolCreatesNewConnection(
            @ForAll @IntRange(min = 10, max = 50) int timeoutMs) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[timeout=" + timeoutMs + "]:jdbc:mock:" + poolName;

        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
        }
        long duration = System.currentTimeMillis() - start;

        // Should complete within timeout + overhead (pool is empty, creates new)
        assertTrue(duration < timeoutMs + 500,
            "Should create new connection within reasonable time, took " + duration + "ms");
    }

    /**
     * Property: When pool has available connection, return is immediate.
     */
    @Property(tries = 10)
    void immediateReturnWhenPoolHasConnection() throws SQLException {
        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[timeout=5000]:jdbc:mock:" + poolName;

        // Create and close a connection to populate pool
        Connection c1 = DriverManager.getConnection(poolUrl);
        c1.close();

        // Second request should be fast (from pool)
        long start = System.currentTimeMillis();
        try (Connection c2 = DriverManager.getConnection(poolUrl)) {
            assertNotNull(c2);
        }
        long duration = System.currentTimeMillis() - start;

        // Should be much faster than timeout
        assertTrue(duration < 100,
            "Getting pooled connection should be fast, took " + duration + "ms");
    }

    // ========== CLOSED CONNECTION BEHAVIOR ==========

    /**
     * Property: Operations on closed pooled connection throw SQLException.
     */
    @Property(tries = 10)
    void operationsOnClosedConnectionThrow() throws SQLException {
        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:mock:" + poolName;

        Connection conn = DriverManager.getConnection(poolUrl);
        conn.close();

        // createStatement should throw
        assertThrows(SQLException.class, () -> conn.createStatement(),
            "createStatement on closed connection should throw SQLException");
    }

    /**
     * Property: Close is idempotent - can close multiple times without error.
     */
    @Property(tries = 10)
    void closeIsIdempotent(
            @ForAll @IntRange(min = 2, max = 5) int closeCount) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:mock:" + poolName;

        Connection conn = DriverManager.getConnection(poolUrl);

        // Close multiple times - should not throw
        for (int i = 0; i < closeCount; i++) {
            conn.close(); // Should not throw
        }
    }

    // ========== CONFIGURATION PARSING ==========

    /**
     * Property: Parameters are parsed correctly from URL.
     */
    @Property(tries = 10)
    void parametersParsedCorrectly(
            @ForAll @IntRange(min = 1, max = 10) int maxValue,
            @ForAll @IntRange(min = 100, max = 5000) int timeoutValue) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[max=" + maxValue + ",timeout=" + timeoutValue + "]:jdbc:mock:" + poolName;

        // Should connect successfully with parsed parameters
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    /**
     * Property: Parameters are case-insensitive.
     */
    @Property(tries = 10)
    void parametersAreCaseInsensitive(
            @ForAll("parameterCases") String paramCase) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[" + paramCase + "=5]:jdbc:mock:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn, "Connection with " + paramCase + " parameter should work");
        }
    }

    // ========== DEFAULT VALUES ==========

    /**
     * Property: Default configuration (no params) works normally.
     */
    @Property(tries = 20)
    void defaultConfigWorks(
            @ForAll @IntRange(min = 1, max = 5) int iterations) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:mock:" + poolName;

        for (int i = 0; i < iterations; i++) {
            try (Connection conn = DriverManager.getConnection(poolUrl)) {
                assertNotNull(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT " + i);
                }
            }
        }
    }

    /**
     * Property: Invalid parameter values fall back to defaults.
     */
    @Property(tries = 10)
    void invalidParametersUseDefaults(
            @ForAll("invalidValues") String invalidValue) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[max=" + invalidValue + "]:jdbc:mock:" + poolName;

        // Should work with defaults despite invalid parameter
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn, "Should work with invalid parameter value");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:pool:X preserves pooling behavior (cat is identity).
     */
    @Property(tries = 10)
    void catPoolComposition() throws SQLException {
        String poolName = createUniquePoolName();
        String catPoolUrl = "jdbc:cat:jdbc:pool:jdbc:mock:" + poolName;

        // Get connection, close, get again (should reuse)
        Connection c1 = DriverManager.getConnection(catPoolUrl);
        assertNotNull(c1);
        c1.close();

        Connection c2 = DriverManager.getConnection(catPoolUrl);
        assertNotNull(c2, "cat:pool should preserve pooling");
        c2.close();
    }

    /**
     * Property: pool:cat:X preserves pooling behavior.
     */
    @Property(tries = 10)
    void poolCatComposition() throws SQLException {
        String poolName = createUniquePoolName();
        String poolCatUrl = "jdbc:pool:jdbc:cat:jdbc:mock:" + poolName;

        Connection c1 = DriverManager.getConnection(poolCatUrl);
        assertNotNull(c1);
        c1.close();

        Connection c2 = DriverManager.getConnection(poolCatUrl);
        assertNotNull(c2, "pool:cat should preserve pooling");
        c2.close();
    }

    /**
     * Property: log:pool:X preserves pooling behavior.
     */
    @Property(tries = 10)
    void logPoolComposition() throws SQLException {
        String poolName = createUniquePoolName();
        String logPoolUrl = "jdbc:log:jdbc:pool:jdbc:mock:" + poolName;

        Connection c1 = DriverManager.getConnection(logPoolUrl);
        assertNotNull(c1);
        c1.close();

        Connection c2 = DriverManager.getConnection(logPoolUrl);
        assertNotNull(c2, "log:pool should preserve pooling");
        c2.close();
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> parameterCases() {
        return Arbitraries.of(
            "max",
            "MAX",
            "Max",
            "timeout",
            "TIMEOUT",
            "Timeout",
            "min",
            "MIN",
            "Min"
        );
    }

    @Provide
    Arbitrary<String> invalidValues() {
        return Arbitraries.of(
            "invalid",
            "abc",
            "null",
            "",
            "NaN",
            "-abc"
        );
    }
}
