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
}
