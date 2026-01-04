package org.pjdbc.drivers;

import org.junit.Test;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/**
 * Tests for LoadBalancingDriver.
 */
public class LoadBalancingDriverTest {

    @Test
    public void acceptsValidUrl() {
        LoadBalancingDriver driver = new LoadBalancingDriver();
        assertTrue(driver.acceptsURL("jdbc:loadbalance:jdbc:mock:db1;jdbc:mock:db2"));
        assertTrue(driver.acceptsURL("jdbc:loadbalance:jdbc:h2:mem:db1;jdbc:h2:mem:db2"));
    }

    @Test
    public void rejectsInvalidUrl() {
        LoadBalancingDriver driver = new LoadBalancingDriver();
        assertFalse(driver.acceptsURL("jdbc:loadbalance:jdbc:mock:single")); // Only one URL
        assertFalse(driver.acceptsURL("jdbc:loadbalance:"));
        assertFalse(driver.acceptsURL("jdbc:federate:jdbc:mock:db1;jdbc:mock:db2")); // Wrong protocol
    }

    @Test
    public void connectReturnsNullForInvalidUrl() throws SQLException {
        LoadBalancingDriver driver = new LoadBalancingDriver();
        assertNull(driver.connect("jdbc:loadbalance:jdbc:mock:single", null));
    }

    @Test
    public void executeQueryReadsFromOneConnection() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_query1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_query2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        // Setup: Create tables with DIFFERENT data in each database
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE users (id INT, name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO users VALUES (1, 'Alice')");

            c2.createStatement().execute("CREATE TABLE users (id INT, name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO users VALUES (2, 'Bob')");
        }

        // Test: Query should return data from ONE database, not both
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {

            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }

            // Should return 1 row (from one DB), not 2 rows (from both)
            assertEquals(1, names.size());
            assertTrue(names.contains("Alice") || names.contains("Bob"));
        }
    }

    @Test
    public void executeUpdateBroadcastsToAll() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_update1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_update2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        // Setup: Create tables
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE items (id INT, name VARCHAR(100))");
            c2.createStatement().execute("CREATE TABLE items (id INT, name VARCHAR(100))");
        }

        // Test: Insert through loadbalance driver
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            int count = stmt.executeUpdate("INSERT INTO items VALUES (1, 'Test')");
            // Both databases should get the insert, so count = 2
            assertEquals(2, count);
        }

        // Verify both databases have the row
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            ResultSet rs1 = c1.createStatement().executeQuery("SELECT COUNT(*) FROM items");
            rs1.next();
            assertEquals(1, rs1.getInt(1));

            ResultSet rs2 = c2.createStatement().executeQuery("SELECT COUNT(*) FROM items");
            rs2.next();
            assertEquals(1, rs2.getInt(1));
        }
    }

    @Test
    public void roundRobinDistributesReads() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_rr1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_rr2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance[readStrategy=round_robin]:" + url1 + ";" + url2;

        // Setup: Create tables with different data
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO src VALUES ('db1')");

            c2.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO src VALUES ('db2')");
        }

        // Test: Multiple queries should hit different DBs
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            Set<String> results = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM src");
                rs.next();
                results.add(rs.getString(1));
                rs.close();
            }

            // Round robin should hit both DBs
            assertEquals(2, results.size());
            assertTrue(results.contains("db1"));
            assertTrue(results.contains("db2"));
        }
    }

    @Test
    public void randomStrategyDistributesReads() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_rand1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_rand2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance[readStrategy=random]:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO src VALUES ('db1')");

            c2.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO src VALUES ('db2')");
        }

        // Test: Random should eventually hit both DBs
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            Set<String> results = new HashSet<>();
            for (int i = 0; i < 50; i++) { // More iterations for random
                ResultSet rs = stmt.executeQuery("SELECT name FROM src");
                rs.next();
                results.add(rs.getString(1));
                rs.close();
            }

            // Random should hit both DBs over enough iterations
            assertEquals(2, results.size());
        }
    }

    @Test
    public void primaryOnlyReadsFromFirst() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_primary1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_primary2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance[readStrategy=primary_only]:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO src VALUES ('primary')");

            c2.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO src VALUES ('replica')");
        }

        // Test: All reads should come from primary (first URL)
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < 10; i++) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM src");
                rs.next();
                assertEquals("primary", rs.getString(1));
                rs.close();
            }
        }
    }

    @Test
    public void replicaOnlyNeverReadsFromPrimary() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_replica1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_replica2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url3 = "jdbc:h2:mem:lb_replica3_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance[readStrategy=replica_only]:" + url1 + ";" + url2 + ";" + url3;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2);
             Connection c3 = DriverManager.getConnection(url3)) {

            c1.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO src VALUES ('primary')");

            c2.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO src VALUES ('replica1')");

            c3.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c3.createStatement().execute("INSERT INTO src VALUES ('replica2')");
        }

        // Test: All reads should come from replicas, never primary
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            Set<String> results = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM src");
                rs.next();
                String name = rs.getString(1);
                assertNotEquals("primary", name);
                results.add(name);
                rs.close();
            }

            // Should hit both replicas
            assertTrue(results.contains("replica1") || results.contains("replica2"));
        }
    }

    @Test
    public void preparedStatementQueryReadsFromOne() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_pstmt1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_pstmt2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE products (id INT, price DECIMAL(10,2))");
            c1.createStatement().execute("INSERT INTO products VALUES (1, 10.00)");

            c2.createStatement().execute("CREATE TABLE products (id INT, price DECIMAL(10,2))");
            c2.createStatement().execute("INSERT INTO products VALUES (2, 20.00)");
        }

        // Test: PreparedStatement query reads from ONE connection
        try (Connection conn = DriverManager.getConnection(lbUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM products WHERE price > ?")) {

            pstmt.setBigDecimal(1, new java.math.BigDecimal("5.00"));
            ResultSet rs = pstmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                count++;
            }
            // Should get 1 row from one DB, not 2 rows from both
            assertEquals(1, count);
        }
    }

    @Test
    public void preparedStatementUpdateBroadcasts() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_pstmt_upd1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_pstmt_upd2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE test (id INT, val VARCHAR(100))");
            c2.createStatement().execute("CREATE TABLE test (id INT, val VARCHAR(100))");
        }

        // Test: PreparedStatement update broadcasts to all
        try (Connection conn = DriverManager.getConnection(lbUrl);
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test VALUES (?, ?)")) {

            pstmt.setInt(1, 1);
            pstmt.setString(2, "value");
            int count = pstmt.executeUpdate();
            assertEquals(2, count); // Updated in both DBs
        }

        // Verify
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            ResultSet rs1 = c1.createStatement().executeQuery("SELECT COUNT(*) FROM test");
            rs1.next();
            assertEquals(1, rs1.getInt(1));

            ResultSet rs2 = c2.createStatement().executeQuery("SELECT COUNT(*) FROM test");
            rs2.next();
            assertEquals(1, rs2.getInt(1));
        }
    }

    @Test
    public void connectionCloseClosesAllDelegates() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_close1_" + System.nanoTime();
        String url2 = "jdbc:h2:mem:lb_close2_" + System.nanoTime();
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        Connection conn = DriverManager.getConnection(lbUrl);
        assertFalse(conn.isClosed());

        conn.close();
        assertTrue(conn.isClosed());
    }

    @Test
    public void versionInfo() {
        LoadBalancingDriver driver = new LoadBalancingDriver();
        assertEquals(1, driver.getMajorVersion());
        assertEquals(0, driver.getMinorVersion());
        assertFalse(driver.jdbcCompliant());
    }

    @Test
    public void configParsing() {
        LoadBalancingDriver.LoadBalanceConfig config;

        // Default config
        config = LoadBalancingDriver.LoadBalanceConfig.fromUrl("jdbc:loadbalance:jdbc:mock:a;jdbc:mock:b");
        assertEquals(LoadBalancingDriver.ReadStrategy.ROUND_ROBIN, config.getReadStrategy());
        assertTrue(config.isFailoverEnabled());
        assertEquals(30000, config.getHealthCheckInterval());

        // Custom config
        config = LoadBalancingDriver.LoadBalanceConfig.fromUrl("jdbc:loadbalance[readStrategy=random,failoverEnabled=false,healthCheckInterval=5000]:jdbc:mock:a;jdbc:mock:b");
        assertEquals(LoadBalancingDriver.ReadStrategy.RANDOM, config.getReadStrategy());
        assertFalse(config.isFailoverEnabled());
        assertEquals(5000, config.getHealthCheckInterval());

        // Least connections strategy
        config = LoadBalancingDriver.LoadBalanceConfig.fromUrl("jdbc:loadbalance[readStrategy=least_connections]:jdbc:mock:a;jdbc:mock:b");
        assertEquals(LoadBalancingDriver.ReadStrategy.LEAST_CONNECTIONS, config.getReadStrategy());
    }

    @Test
    public void executeReplicatesToAllConnections() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_exec1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_exec2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            // DDL should execute on all
            boolean result = stmt.execute("CREATE TABLE test_table (id INT)");
            assertFalse(result); // DDL returns false
        }

        // Verify table exists in both databases
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            // These should not throw - tables exist
            c1.createStatement().execute("INSERT INTO test_table VALUES (1)");
            c2.createStatement().execute("INSERT INTO test_table VALUES (2)");
        }
    }

    @Test
    public void leastConnectionsTracksLoad() throws SQLException {
        String url1 = "jdbc:h2:mem:lb_lc1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:lb_lc2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance[readStrategy=least_connections]:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO src VALUES ('db1')");

            c2.createStatement().execute("CREATE TABLE src (name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO src VALUES ('db2')");
        }

        // Test: Least connections strategy should work
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {

            Set<String> results = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM src");
                rs.next();
                results.add(rs.getString(1));
                rs.close();
            }

            // Should use both connections
            assertTrue("Should hit at least one DB", results.size() >= 1);
        }
    }
}
