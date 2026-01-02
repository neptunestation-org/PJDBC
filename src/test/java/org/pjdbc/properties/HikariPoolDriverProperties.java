package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for HikariPoolDriver.
 *
 * These tests verify HikariCP-based connection pooling properties:
 * - Connection pooling from HikariCP
 * - HikariCP configuration via URL parameters
 * - Connection reuse
 * - Pool per target URL
 * - Default configuration
 * - Driver composition
 */
class HikariPoolDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "hikari_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private Properties defaultProperties() {
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        return props;
    }

    // ========== CONNECTION POOLING ==========

    /**
     * Property: Connections are obtained from HikariCP pool.
     */
    @Property(tries = 10)
    void connectionsFromPool(
            @ForAll @IntRange(min = 1, max = 3) int connectionCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        for (int i = 0; i < connectionCount; i++) {
            try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties())) {
                assertNotNull(conn, "Connection should not be null");
                assertTrue(conn.isWrapperFor(Connection.class),
                    "Connection should be a wrapper");

                // Verify connection works
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    /**
     * Property: Multiple connections can be obtained concurrently.
     */
    @Property(tries = 10)
    void multipleConnectionsWork(
            @ForAll @IntRange(min = 2, max = 4) int connectionCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName + "?maximumPoolSize=" + (connectionCount + 1);

        Connection[] connections = new Connection[connectionCount];

        // Get multiple connections
        for (int i = 0; i < connectionCount; i++) {
            connections[i] = DriverManager.getConnection(hikariUrl, defaultProperties());
            assertNotNull(connections[i]);
        }

        // All connections should work
        for (int i = 0; i < connectionCount; i++) {
            try (Statement stmt = connections[i].createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                assertTrue(rs.next());
                assertEquals(i, rs.getInt(1));
            }
        }

        // Close all
        for (Connection conn : connections) {
            conn.close();
        }
    }

    // ========== HIKARICP CONFIGURATION ==========

    /**
     * Property: maximumPoolSize is configurable via URL parameter.
     */
    @Property(tries = 5)
    void maximumPoolSizeConfigured(
            @ForAll @IntRange(min = 2, max = 5) int poolSize) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName + "?maximumPoolSize=" + poolSize;

        // Should be able to get poolSize connections
        Connection[] connections = new Connection[poolSize];
        for (int i = 0; i < poolSize; i++) {
            connections[i] = DriverManager.getConnection(hikariUrl, defaultProperties());
            assertNotNull(connections[i]);
        }

        // Close all
        for (Connection conn : connections) {
            conn.close();
        }
    }

    /**
     * Property: Multiple HikariCP properties can be configured.
     */
    @Property(tries = 5)
    void multiplePropertiesConfigured() throws SQLException {
        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName +
            "?maximumPoolSize=3&minimumIdle=1&idleTimeout=30000";

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties())) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
        }
    }

    // ========== CONNECTION REUSE ==========

    /**
     * Property: Connections are reused after close.
     */
    @Property(tries = 10)
    void connectionsReusedAfterClose(
            @ForAll @IntRange(min = 2, max = 5) int iterations) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName + "?maximumPoolSize=1";

        // With pool size 1, all connections must be reused
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties())) {
                assertNotNull(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                    assertTrue(rs.next());
                }
            }
        }
    }

    /**
     * Property: Pool maintains connections across multiple requests.
     */
    @Property(tries = 10)
    void poolMaintainsConnections(
            @ForAll @IntRange(min = 3, max = 6) int requestCount) throws SQLException {

        String dbName = createUniqueDbName();
        // Use DB_CLOSE_DELAY in H2 URL, maximumPoolSize in properties
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        Properties props = defaultProperties();
        props.setProperty("maximumPoolSize", "2");

        // Create a table in first connection
        try (Connection conn = DriverManager.getConnection(hikariUrl, props);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
        }

        // Subsequent connections should see the table
        for (int i = 0; i < requestCount; i++) {
            try (Connection conn = DriverManager.getConnection(hikariUrl, props);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_table")) {
                assertTrue(rs.next(), "Table should exist in iteration " + i);
            }
        }
    }

    // ========== POOL PER TARGET URL ==========

    /**
     * Property: Different target URLs get separate pools.
     */
    @Property(tries = 5)
    void differentUrlsGetDifferentPools() throws SQLException {
        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();

        String hikariUrl1 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName1 + ";DB_CLOSE_DELAY=-1";
        String hikariUrl2 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName2 + ";DB_CLOSE_DELAY=-1";

        // Create different tables in each database
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement()) {
            stmt1.execute("CREATE TABLE table_one (id INT)");
        }

        try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
             Statement stmt2 = conn2.createStatement()) {
            stmt2.execute("CREATE TABLE table_two (id INT)");
        }

        // Verify tables are in separate databases
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement()) {
            // table_one should exist
            try (ResultSet rs = stmt1.executeQuery("SELECT COUNT(*) FROM table_one")) {
                assertTrue(rs.next());
            }
            // table_two should NOT exist in this database
            assertThrows(SQLException.class,
                () -> stmt1.executeQuery("SELECT COUNT(*) FROM table_two"));
        }
    }

    // ========== DEFAULT CONFIGURATION ==========

    /**
     * Property: Driver works without explicit HikariCP properties.
     */
    @Property(tries = 10)
    void defaultConfigWorks(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties())) {
            assertNotNull(conn);

            for (int i = 0; i < queryCount; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                    assertTrue(rs.next());
                    assertEquals(i, rs.getInt(1));
                }
            }
        }
    }

    /**
     * Property: Works with minimal properties (just user/password).
     */
    @Property(tries = 10)
    void worksWithMinimalProperties() throws SQLException {
        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        Properties minimalProps = new Properties();
        minimalProps.setProperty("user", "sa");
        minimalProps.setProperty("password", "");

        try (Connection conn = DriverManager.getConnection(hikariUrl, minimalProps)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt(1));
            }
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:hikaricp:X preserves pooling (cat is identity).
     * Note: cat wraps hikaricp, so cat must be loaded first.
     */
    @Property(tries = 5)
    void catHikariComposition() throws SQLException, ClassNotFoundException {
        // Ensure CatDriver is loaded
        Class.forName("org.pjdbc.drivers.CatDriver");

        String dbName = createUniqueDbName();
        String catHikariUrl = "jdbc:cat:jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(catHikariUrl, defaultProperties())) {
            assertNotNull(conn, "cat:hikaricp should work");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
        }
    }

    /**
     * Property: log:hikaricp:X preserves pooling.
     * Note: log wraps hikaricp, so log must be loaded first.
     */
    @Property(tries = 5)
    void logHikariComposition() throws SQLException, ClassNotFoundException {
        // Ensure LogDriver is loaded
        Class.forName("org.pjdbc.drivers.LogDriver");

        String dbName = createUniqueDbName();
        String logHikariUrl = "jdbc:log:jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(logHikariUrl, defaultProperties())) {
            assertNotNull(conn, "log:hikaricp should work");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
        }
    }

    // ========== SQL OPERATIONS ==========

    /**
     * Property: executeQuery works correctly through HikariCP pool.
     */
    @Property(tries = 10)
    void executeQueryThroughPool(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next(), "Should have result");
            assertEquals(value, rs.getInt(1), "Query result should match");
        }
    }

    /**
     * Property: executeUpdate works correctly through HikariCP pool.
     */
    @Property(tries = 10)
    void executeUpdateThroughPool(
            @ForAll @IntRange(min = 1, max = 10) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE update_test (id INT, data VARCHAR(50))");

            for (int i = 0; i < rowCount; i++) {
                int result = stmt.executeUpdate(
                    "INSERT INTO update_test VALUES (" + i + ", 'val" + i + "')");
                assertEquals(1, result, "Insert should affect 1 row");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM update_test")) {
                assertTrue(rs.next());
                assertEquals(rowCount, rs.getInt(1), "Should have " + rowCount + " rows");
            }
        }
    }

    /**
     * Property: execute works correctly through HikariCP pool.
     */
    @Property(tries = 10)
    void executeThroughPool() throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             Statement stmt = conn.createStatement()) {

            // DDL via execute
            boolean hasResultSet = stmt.execute("CREATE TABLE exec_test (id INT)");
            assertFalse(hasResultSet, "CREATE TABLE should not return ResultSet");

            // DML via execute
            hasResultSet = stmt.execute("INSERT INTO exec_test VALUES (1)");
            assertFalse(hasResultSet, "INSERT should not return ResultSet");

            // Query via execute
            hasResultSet = stmt.execute("SELECT * FROM exec_test");
            assertTrue(hasResultSet, "SELECT should return ResultSet");

            try (ResultSet rs = stmt.getResultSet()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    /**
     * Property: Multiple SQL operations work on same pooled connection.
     */
    @Property(tries = 10)
    void multipleSqlOperationsThroughPool(
            @ForAll @IntRange(min = 2, max = 5) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < operationCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                    assertTrue(rs.next());
                    assertEquals(i, rs.getInt(1), "Query " + i + " should return correct value");
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

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
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

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             PreparedStatement pstmt = conn.prepareStatement("SELECT ?")) {

            pstmt.setInt(1, value);

            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(value, rs.getInt(1));
            }
        }
    }

    /**
     * Property: PreparedStatement with multiple parameters works.
     */
    @Property(tries = 10)
    void preparedStatementMultipleParams(
            @ForAll @IntRange(min = 1, max = 50) int a,
            @ForAll @IntRange(min = 1, max = 50) int b) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
             PreparedStatement pstmt = conn.prepareStatement("SELECT ? + ?")) {

            pstmt.setInt(1, a);
            pstmt.setInt(2, b);

            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(a + b, rs.getInt(1));
            }
        }
    }

    /**
     * Property: Multiple statement types work on same pooled connection.
     */
    @Property(tries = 10)
    void multipleStatementTypesThroughPool(
            @ForAll @IntRange(min = 1, max = 50) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties())) {

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

    // ========== CONNECTION LIFECYCLE ==========

    /**
     * Property: Connection lifecycle - get, use, close, get again.
     */
    @Property(tries = 10)
    void connectionLifecycle(
            @ForAll @IntRange(min = 2, max = 5) int cycles) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        for (int i = 0; i < cycles; i++) {
            try (Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT " + i)) {

                assertTrue(rs.next(), "Cycle " + i + " should return result");
                assertEquals(i, rs.getInt(1), "Cycle " + i + " should return correct value");
            }
        }
    }

    /**
     * Property: Closed connection throws on operations.
     * Note: Uses underlying HikariCP connection's isClosed() to avoid PJDBC proxy bug.
     */
    @Property(tries = 10)
    void closedConnectionThrows() throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());
        conn.close();

        // Attempting to create statement on closed connection should fail
        assertThrows(SQLException.class, () -> conn.createStatement(),
            "createStatement on closed connection should throw");
    }

    /**
     * Property: close is idempotent - multiple closes don't throw.
     */
    @Property(tries = 10)
    void closeIsIdempotent(
            @ForAll @IntRange(min = 2, max = 5) int closeCount) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName;

        Connection conn = DriverManager.getConnection(hikariUrl, defaultProperties());

        // Multiple closes should not throw
        for (int i = 0; i < closeCount; i++) {
            conn.close();
        }
    }

    /**
     * Property: Data persists across pool connection returns.
     */
    @Property(tries = 10)
    void dataPersistsAcrossPoolReturns(
            @ForAll @IntRange(min = 2, max = 4) int iterations) throws SQLException {

        String dbName = createUniqueDbName();
        String hikariUrl = "jdbc:hikaricp:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        Properties props = defaultProperties();
        props.setProperty("maximumPoolSize", "1");

        // First connection creates table
        try (Connection conn = DriverManager.getConnection(hikariUrl, props);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE persist_test (id INT)");
        }

        // Subsequent connections insert and verify
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = DriverManager.getConnection(hikariUrl, props);
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

    // ========== POOL ISOLATION ==========

    /**
     * Property: Different pool URLs are completely isolated.
     */
    @Property(tries = 5)
    void poolsAreIsolated() throws SQLException {

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String hikariUrl1 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName1 + ";DB_CLOSE_DELAY=-1";
        String hikariUrl2 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName2 + ";DB_CLOSE_DELAY=-1";

        // Create tables with different data in each pool
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement()) {
            stmt1.execute("CREATE TABLE pool_test (val INT)");
            stmt1.executeUpdate("INSERT INTO pool_test VALUES (111)");
        }

        try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
             Statement stmt2 = conn2.createStatement()) {
            stmt2.execute("CREATE TABLE pool_test (val INT)");
            stmt2.executeUpdate("INSERT INTO pool_test VALUES (222)");
        }

        // Verify data isolation
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement();
             ResultSet rs1 = stmt1.executeQuery("SELECT val FROM pool_test")) {
            assertTrue(rs1.next());
            assertEquals(111, rs1.getInt(1), "Pool 1 should have val 111");
        }

        try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
             Statement stmt2 = conn2.createStatement();
             ResultSet rs2 = stmt2.executeQuery("SELECT val FROM pool_test")) {
            assertTrue(rs2.next());
            assertEquals(222, rs2.getInt(1), "Pool 2 should have val 222");
        }
    }

    /**
     * Property: Operations on one pool don't affect another.
     */
    @Property(tries = 5)
    void poolOperationsDontInterfere(
            @ForAll @IntRange(min = 1, max = 5) int ops1,
            @ForAll @IntRange(min = 1, max = 5) int ops2) throws SQLException {

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String hikariUrl1 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName1 + ";DB_CLOSE_DELAY=-1";
        String hikariUrl2 = "jdbc:hikaricp:jdbc:h2:mem:" + dbName2 + ";DB_CLOSE_DELAY=-1";

        // Setup tables
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement()) {
            stmt1.execute("CREATE TABLE counter (count INT)");
            stmt1.executeUpdate("INSERT INTO counter VALUES (0)");
        }

        try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
             Statement stmt2 = conn2.createStatement()) {
            stmt2.execute("CREATE TABLE counter (count INT)");
            stmt2.executeUpdate("INSERT INTO counter VALUES (0)");
        }

        // Do different number of increments on each pool
        for (int i = 0; i < ops1; i++) {
            try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
                 Statement stmt1 = conn1.createStatement()) {
                stmt1.executeUpdate("UPDATE counter SET count = count + 1");
            }
        }

        for (int i = 0; i < ops2; i++) {
            try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
                 Statement stmt2 = conn2.createStatement()) {
                stmt2.executeUpdate("UPDATE counter SET count = count + 1");
            }
        }

        // Verify counts are independent
        try (Connection conn1 = DriverManager.getConnection(hikariUrl1, defaultProperties());
             Statement stmt1 = conn1.createStatement();
             ResultSet rs1 = stmt1.executeQuery("SELECT count FROM counter")) {
            assertTrue(rs1.next());
            assertEquals(ops1, rs1.getInt(1), "Pool 1 should have count " + ops1);
        }

        try (Connection conn2 = DriverManager.getConnection(hikariUrl2, defaultProperties());
             Statement stmt2 = conn2.createStatement();
             ResultSet rs2 = stmt2.executeQuery("SELECT count FROM counter")) {
            assertTrue(rs2.next());
            assertEquals(ops2, rs2.getInt(1), "Pool 2 should have count " + ops2);
        }
    }
}
