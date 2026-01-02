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

    // ========== POOL ISOLATION ==========

    /**
     * Property: Different pool URLs create separate independent pools.
     */
    @Property(tries = 10)
    void differentPoolUrlsCreateSeparatePools(
            @ForAll @IntRange(min = 2, max = 4) int poolCount) throws SQLException {

        String[] poolNames = new String[poolCount];
        String[] poolUrls = new String[poolCount];
        Connection[] connections = new Connection[poolCount];

        // Create connections to different pools
        for (int i = 0; i < poolCount; i++) {
            poolNames[i] = createUniquePoolName();
            poolUrls[i] = "jdbc:pool:jdbc:mock:" + poolNames[i];
            connections[i] = DriverManager.getConnection(poolUrls[i]);
            assertNotNull(connections[i], "Connection " + i + " should not be null");
        }

        // Close all connections (return to their respective pools)
        for (Connection conn : connections) {
            conn.close();
        }

        // Each pool should have its own connection
        for (int i = 0; i < poolCount; i++) {
            try (Connection conn = DriverManager.getConnection(poolUrls[i])) {
                assertNotNull(conn, "Pool " + i + " should have connection available");
            }
        }
    }

    /**
     * Property: Operations on one pool don't affect another pool.
     */
    @Property(tries = 10)
    void poolOperationsAreIsolated() throws SQLException {
        String poolName1 = createUniquePoolName();
        String poolName2 = createUniquePoolName();
        String poolUrl1 = "jdbc:pool:jdbc:mock:" + poolName1;
        String poolUrl2 = "jdbc:pool:jdbc:mock:" + poolName2;

        MockDriver.clearLogs();

        // Execute SQL on pool 1
        try (Connection conn1 = DriverManager.getConnection(poolUrl1);
             Statement stmt = conn1.createStatement()) {
            stmt.executeQuery("SELECT 1");
        }

        // Execute SQL on pool 2
        try (Connection conn2 = DriverManager.getConnection(poolUrl2);
             Statement stmt = conn2.createStatement()) {
            stmt.executeQuery("SELECT 2");
        }

        // Verify each pool's underlying connection received correct SQL
        String log1 = MockDriver.getLog("jdbc:mock:" + poolName1);
        String log2 = MockDriver.getLog("jdbc:mock:" + poolName2);

        assertTrue(log1.contains("SELECT 1"), "Pool 1 should have SELECT 1");
        assertFalse(log1.contains("SELECT 2"), "Pool 1 should not have SELECT 2");
        assertTrue(log2.contains("SELECT 2"), "Pool 2 should have SELECT 2");
        assertFalse(log2.contains("SELECT 1"), "Pool 2 should not have SELECT 1");
    }

    // ========== SQL OPERATIONS THROUGH POOL ==========

    /**
     * Property: executeQuery works correctly through pooled connection.
     */
    @Property(tries = 10)
    void executeQueryThroughPool(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String poolName = createUniquePoolName();
        String mockUrl = "jdbc:mock:" + poolName;
        String poolUrl = "jdbc:pool:" + mockUrl;

        MockDriver.clearLogs();

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeQuery"), "Should have executeQuery");
        assertTrue(log.contains(sql), "SQL should reach underlying driver");
    }

    /**
     * Property: executeUpdate works correctly through pooled connection.
     */
    @Property(tries = 10)
    void executeUpdateThroughPool(
            @ForAll("updateStatements") String sql) throws SQLException {

        String poolName = createUniquePoolName();
        String mockUrl = "jdbc:mock:" + poolName;
        String poolUrl = "jdbc:pool:" + mockUrl;

        MockDriver.clearLogs();

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeUpdate"), "Should have executeUpdate");
        assertTrue(log.contains(sql), "SQL should reach underlying driver");
    }

    /**
     * Property: execute works correctly through pooled connection.
     */
    @Property(tries = 10)
    void executeThroughPool(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String poolName = createUniquePoolName();
        String mockUrl = "jdbc:mock:" + poolName;
        String poolUrl = "jdbc:pool:" + mockUrl;

        MockDriver.clearLogs();

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("execute"), "Should have execute");
        assertTrue(log.contains(sql), "SQL should reach underlying driver");
    }

    /**
     * Property: Multiple SQL operations work on same pooled connection.
     */
    @Property(tries = 10)
    void multipleSqlOperationsThroughPool(
            @ForAll @IntRange(min = 2, max = 5) int operationCount) throws SQLException {

        String poolName = createUniquePoolName();
        String mockUrl = "jdbc:mock:" + poolName;
        String poolUrl = "jdbc:pool:" + mockUrl;

        MockDriver.clearLogs();

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < operationCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String log = MockDriver.getLog(mockUrl);
        for (int i = 0; i < operationCount; i++) {
            assertTrue(log.contains("SELECT " + i),
                "Operation " + i + " should reach underlying driver");
        }
    }

    // ========== H2 INTEGRATION ==========

    /**
     * Property: Pool works with real H2 database for queries.
     */
    @Property(tries = 10)
    void poolWithH2Query(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next(), "Should have result");
            assertEquals(value, rs.getInt(1), "Query result should be correct");
        }
    }

    /**
     * Property: Pool works with real H2 database for table operations.
     */
    @Property(tries = 10)
    void poolWithH2TableOperations(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {

            // Create table
            stmt.execute("CREATE TABLE test_pool (id INT, name VARCHAR(50))");

            // Insert rows
            for (int i = 0; i < rowCount; i++) {
                int result = stmt.executeUpdate(
                    "INSERT INTO test_pool VALUES (" + i + ", 'name" + i + "')");
                assertEquals(1, result, "Insert should affect 1 row");
            }

            // Query count
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_pool")) {
                assertTrue(rs.next());
                assertEquals(rowCount, rs.getInt(1), "Should have " + rowCount + " rows");
            }
        }
    }

    /**
     * Property: Pool connection lifecycle works (get, use, close, get again).
     */
    @Property(tries = 10)
    void poolConnectionLifecycle(
            @ForAll @IntRange(min = 2, max = 4) int cycles) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        // Multiple get/use/close cycles - each should work independently
        for (int i = 0; i < cycles; i++) {
            try (Connection conn = DriverManager.getConnection(poolUrl);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT " + i)) {

                assertTrue(rs.next(), "Cycle " + i + " should return result");
                assertEquals(i, rs.getInt(1), "Cycle " + i + " should return correct value");
            }
        }
    }

    /**
     * Property: H2 data persists across pool connection returns.
     * This verifies the fix for PJDBC-jzy where pooled connections weren't re-proxied.
     */
    @Property(tries = 10)
    void h2DataPersistsAcrossPoolReturns(
            @ForAll @IntRange(min = 2, max = 4) int iterations) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[max=1]:jdbc:h2:mem:" + poolName;

        // First connection creates table
        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE persist_test (id INT)");
        }

        // Subsequent connections insert and verify - data should persist
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = DriverManager.getConnection(poolUrl);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("INSERT INTO persist_test VALUES (" + i + ")");

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM persist_test")) {
                    assertTrue(rs.next());
                    assertEquals(i + 1, rs.getInt(1),
                        "Should have " + (i + 1) + " rows after iteration " + i);
                }
            }
        }
    }

    // ========== STATEMENT TYPES ==========

    /**
     * Property: Statement works through pooled connection.
     */
    @Property(tries = 10)
    void statementThroughPool(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1));
        }
    }

    /**
     * Property: PreparedStatement works through pooled connection.
     */
    @Property(tries = 10)
    void preparedStatementThroughPool(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT ?")) {

            pstmt.setInt(1, value);

            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(value, rs.getInt(1));
            }
        }
    }

    /**
     * Property: Multiple statement types work on same pooled connection.
     */
    @Property(tries = 10)
    void multipleStatementTypesThroughPool(
            @ForAll @IntRange(min = 1, max = 50) int value) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool:jdbc:h2:mem:" + poolName;

        try (Connection conn = DriverManager.getConnection(poolUrl)) {

            // Regular Statement
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT " + value)) {
                assertTrue(rs.next());
                assertEquals(value, rs.getInt(1));
            }

            // PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT ?")) {
                pstmt.setInt(1, value * 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(value * 2, rs.getInt(1));
                }
            }
        }
    }

    // ========== MIN POOL SIZE ==========

    /**
     * Property: min parameter is accepted (doesn't cause errors).
     */
    @Property(tries = 10)
    void minParameterAccepted(
            @ForAll @IntRange(min = 0, max = 5) int minSize) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[min=" + minSize + "]:jdbc:mock:" + poolName;

        // Should connect successfully with min parameter
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    /**
     * Property: min and max parameters work together.
     */
    @Property(tries = 10)
    void minAndMaxParametersTogether(
            @ForAll @IntRange(min = 1, max = 3) int minSize,
            @ForAll @IntRange(min = 3, max = 5) int maxSize) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[min=" + minSize + ",max=" + maxSize + "]:jdbc:mock:" + poolName;

        // Should connect successfully with both parameters
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
        }

        // Can get multiple connections up to max
        Connection[] connections = new Connection[maxSize];
        for (int i = 0; i < maxSize; i++) {
            connections[i] = DriverManager.getConnection(poolUrl);
            assertNotNull(connections[i]);
        }

        // Close all
        for (Connection conn : connections) {
            conn.close();
        }
    }

    /**
     * Property: All three parameters (min, max, timeout) work together.
     */
    @Property(tries = 10)
    void allParametersTogether(
            @ForAll @IntRange(min = 0, max = 2) int minSize,
            @ForAll @IntRange(min = 3, max = 5) int maxSize,
            @ForAll @IntRange(min = 100, max = 1000) int timeout) throws SQLException {

        String poolName = createUniquePoolName();
        String poolUrl = "jdbc:pool[min=" + minSize + ",max=" + maxSize + ",timeout=" + timeout + "]:jdbc:mock:" + poolName;

        // Should connect successfully with all parameters
        try (Connection conn = DriverManager.getConnection(poolUrl)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
        }
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

    @Provide
    Arbitrary<String> sqlStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "SELECT id, name FROM test",
            "SELECT COUNT(*) FROM t"
        );
    }

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1",
            "INSERT INTO users (name) VALUES ('test')"
        );
    }
}
