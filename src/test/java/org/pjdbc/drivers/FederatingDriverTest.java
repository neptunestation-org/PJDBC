package org.pjdbc.drivers;

import org.junit.Test;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Tests for FederatingDriver.
 */
public class FederatingDriverTest {

    @Test
    public void acceptsValidUrl() {
        FederatingDriver driver = new FederatingDriver();
        assertTrue(driver.acceptsURL("jdbc:federate:jdbc:mock:db1;jdbc:mock:db2"));
        assertTrue(driver.acceptsURL("jdbc:federate:jdbc:h2:mem:db1;jdbc:h2:mem:db2"));
    }

    @Test
    public void rejectsInvalidUrl() {
        FederatingDriver driver = new FederatingDriver();
        assertFalse(driver.acceptsURL("jdbc:federate:jdbc:mock:single")); // Only one URL
        assertFalse(driver.acceptsURL("jdbc:federate:"));
        assertFalse(driver.acceptsURL("jdbc:tee:jdbc:mock:db1;jdbc:mock:db2")); // Wrong protocol
    }

    @Test
    public void connectReturnsNullForInvalidUrl() throws SQLException {
        FederatingDriver driver = new FederatingDriver();
        assertNull(driver.connect("jdbc:federate:jdbc:mock:single", null));
    }

    @Test
    public void executeQueryMergesResults() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_test1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_test2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        // Setup: Create tables and insert data in each database
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE users (id INT, name VARCHAR(100))");
            c1.createStatement().execute("INSERT INTO users VALUES (1, 'Alice')");
            c1.createStatement().execute("INSERT INTO users VALUES (2, 'Bob')");

            c2.createStatement().execute("CREATE TABLE users (id INT, name VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO users VALUES (3, 'Charlie')");
            c2.createStatement().execute("INSERT INTO users VALUES (4, 'Diana')");
        }

        // Test: Query through federate driver should return all 4 rows
        try (Connection conn = DriverManager.getConnection(federateUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {

            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }

            assertEquals(4, names.size());
            assertTrue(names.contains("Alice"));
            assertTrue(names.contains("Bob"));
            assertTrue(names.contains("Charlie"));
            assertTrue(names.contains("Diana"));
        }
    }

    @Test
    public void executeQueryWithPreparedStatement() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_pstmt1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_pstmt2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE products (id INT, price DECIMAL(10,2))");
            c1.createStatement().execute("INSERT INTO products VALUES (1, 10.00)");

            c2.createStatement().execute("CREATE TABLE products (id INT, price DECIMAL(10,2))");
            c2.createStatement().execute("INSERT INTO products VALUES (2, 20.00)");
        }

        // Test: PreparedStatement query
        try (Connection conn = DriverManager.getConnection(federateUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM products WHERE price > ?")) {

            pstmt.setBigDecimal(1, new java.math.BigDecimal("5.00"));
            ResultSet rs = pstmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                count++;
            }
            assertEquals(2, count);
        }
    }

    @Test
    public void executeUpdateBroadcastsToAll() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_update1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_update2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        // Setup: Create tables
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE items (id INT, name VARCHAR(100))");
            c2.createStatement().execute("CREATE TABLE items (id INT, name VARCHAR(100))");
        }

        // Test: Insert through federate driver
        try (Connection conn = DriverManager.getConnection(federateUrl);
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
    public void firstNonEmptyStrategyReturnsFirstWithData() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_fne1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_fne2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate[mergeStrategy=first_non_empty]:" + url1 + ";" + url2;

        // Setup: First DB empty, second has data
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE testdata (id INT, val VARCHAR(100))");
            // No inserts in c1

            c2.createStatement().execute("CREATE TABLE testdata (id INT, val VARCHAR(100))");
            c2.createStatement().execute("INSERT INTO testdata VALUES (1, 'from_db2')");
        }

        // Test: Should return data from second DB
        try (Connection conn = DriverManager.getConnection(federateUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM testdata")) {

            assertTrue(rs.next());
            assertEquals("from_db2", rs.getString("val"));
            assertFalse(rs.next());
        }
    }

    @Test
    public void connectionCloseClosesAllDelegates() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_close1_" + System.nanoTime();
        String url2 = "jdbc:h2:mem:federate_close2_" + System.nanoTime();
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        Connection conn = DriverManager.getConnection(federateUrl);
        assertFalse(conn.isClosed());

        conn.close();
        assertTrue(conn.isClosed());
    }

    @Test
    public void versionInfo() {
        FederatingDriver driver = new FederatingDriver();
        assertEquals(1, driver.getMajorVersion());
        assertEquals(0, driver.getMinorVersion());
        assertFalse(driver.jdbcCompliant());
    }

    @Test
    public void configParsing() {
        FederatingDriver.FederateConfig config;

        // Default config
        config = FederatingDriver.FederateConfig.fromUrl("jdbc:federate:jdbc:mock:a;jdbc:mock:b", List.of());
        assertEquals(FederatingDriver.MergeStrategy.CONCAT, config.getMergeStrategy());
        assertFalse(config.isParallelExecution());
        assertEquals(30000, config.getTimeout());

        // Custom config
        config = FederatingDriver.FederateConfig.fromUrl("jdbc:federate[mergeStrategy=union_all,parallelExecution=true,timeout=5000]:jdbc:mock:a;jdbc:mock:b", List.of());
        assertEquals(FederatingDriver.MergeStrategy.UNION_ALL, config.getMergeStrategy());
        assertTrue(config.isParallelExecution());
        assertEquals(5000, config.getTimeout());
    }

    @Test
    public void parallelExecutionWorks() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_parallel1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_parallel2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate[parallelExecution=true]:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE nums (n INT)");
            c1.createStatement().execute("INSERT INTO nums VALUES (1)");

            c2.createStatement().execute("CREATE TABLE nums (n INT)");
            c2.createStatement().execute("INSERT INTO nums VALUES (2)");
        }

        // Test with parallel execution
        try (Connection conn = DriverManager.getConnection(federateUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM nums")) {

            List<Integer> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getInt("n"));
            }
            assertEquals(2, values.size());
            assertTrue(values.contains(1));
            assertTrue(values.contains(2));
        }
    }

    @Test
    public void executeReplicatesToAll() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_exec1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_exec2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(federateUrl);
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
    public void resultSetMetadataFromFirst() throws SQLException {
        String url1 = "jdbc:h2:mem:federate_meta1_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:h2:mem:federate_meta2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String federateUrl = "jdbc:federate:" + url1 + ";" + url2;

        // Setup
        try (Connection c1 = DriverManager.getConnection(url1);
             Connection c2 = DriverManager.getConnection(url2)) {

            c1.createStatement().execute("CREATE TABLE meta_test (col1 INT, col2 VARCHAR(50))");
            c2.createStatement().execute("CREATE TABLE meta_test (col1 INT, col2 VARCHAR(50))");
        }

        try (Connection conn = DriverManager.getConnection(federateUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT col1, col2 FROM meta_test")) {

            ResultSetMetaData meta = rs.getMetaData();
            assertEquals(2, meta.getColumnCount());
            assertEquals("COL1", meta.getColumnName(1).toUpperCase());
            assertEquals("COL2", meta.getColumnName(2).toUpperCase());
        }
    }
}
