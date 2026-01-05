package org.pjdbc.drivers;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.drivers.TimeoutDriver.TimeoutConfig;

public class TimeoutDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.TimeoutDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
    }

    // ========== URL Acceptance Tests ==========

    @Test
    public void testAcceptsURL() throws SQLException {
        TimeoutDriver driver = new TimeoutDriver();
        assertTrue(driver.acceptsURL("jdbc:timeout:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:timeout[queryTimeout=60]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:timeout[queryTimeout=10,cancelOnTimeout=false]:jdbc:postgresql://localhost/db"));
        assertFalse(driver.acceptsURL("jdbc:retry:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:h2:mem:test"));
    }

    // ========== Configuration Tests ==========

    @Test
    public void testDefaultConfig() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout:jdbc:h2:mem:test");
        assertEquals(30, config.getQueryTimeout());
        assertTrue(config.isCancelOnTimeout());
    }

    @Test
    public void testCustomQueryTimeout() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[queryTimeout=60]:jdbc:h2:mem:test");
        assertEquals(60, config.getQueryTimeout());
    }

    @Test
    public void testZeroTimeout() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[queryTimeout=0]:jdbc:h2:mem:test");
        assertEquals(0, config.getQueryTimeout());
    }

    @Test
    public void testCancelOnTimeoutFalse() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[cancelOnTimeout=false]:jdbc:h2:mem:test");
        assertFalse(config.isCancelOnTimeout());
    }

    @Test
    public void testMultipleParameters() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[queryTimeout=120,cancelOnTimeout=false]:jdbc:h2:mem:test");
        assertEquals(120, config.getQueryTimeout());
        assertFalse(config.isCancelOnTimeout());
    }

    @Test
    public void testInvalidTimeoutUsesDefault() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[queryTimeout=invalid]:jdbc:h2:mem:test");
        assertEquals(30, config.getQueryTimeout());
    }

    @Test
    public void testNegativeTimeoutUsesDefault() {
        TimeoutConfig config = new TimeoutConfig("jdbc:timeout[queryTimeout=-5]:jdbc:h2:mem:test");
        assertEquals(30, config.getQueryTimeout());
    }

    @Test
    public void testConfigToString() {
        TimeoutConfig config = new TimeoutConfig(60, true);
        String str = config.toString();
        assertTrue(str.contains("60"));
        assertTrue(str.contains("cancelOnTimeout"));
    }

    // ========== Integration Tests ==========

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:timeout:jdbc:h2:mem:to_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);

            TimeoutConfig config = TimeoutDriver.getTimeoutConfig(conn);
            assertNotNull(config);
            assertEquals(30, config.getQueryTimeout());
        }
    }

    @Test
    public void testQueryExecution() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=60]:jdbc:h2:mem:to_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42 AS answer")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("answer"));
            }
        }
    }

    @Test
    public void testTimeoutAppliedToStatement() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=45]:jdbc:h2:mem:to_stmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                assertEquals(45, stmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testTimeoutAppliedToPreparedStatement() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=20]:jdbc:h2:mem:to_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                assertEquals(20, pstmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testCustomTimeoutOverride() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=30]:jdbc:h2:mem:to_override;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                assertEquals(30, stmt.getQueryTimeout());

                // Override the timeout
                stmt.setQueryTimeout(90);
                assertEquals(90, stmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testZeroTimeoutMeansNoLimit() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=0]:jdbc:h2:mem:to_zero;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                assertEquals(0, stmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testPreparedStatementExecution() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=15]:jdbc:h2:mem:to_pstmt_exec;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
                stmt.execute("INSERT INTO test VALUES (1, 'Alice')");
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
                assertEquals(15, pstmt.getQueryTimeout());
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testUpdateExecution() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=25]:jdbc:h2:mem:to_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, val VARCHAR(100))");
                assertEquals(25, stmt.getQueryTimeout());

                int rows = stmt.executeUpdate("INSERT INTO test VALUES (1, 'test')");
                assertEquals(1, rows);
            }
        }
    }

    @Test
    public void testDriverChaining() throws SQLException {
        // Chain: timeout -> cat -> h2
        String url = "jdbc:timeout[queryTimeout=50]:jdbc:cat:jdbc:h2:mem:to_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 123")) {
                assertTrue(rs.next());
                assertEquals(123, rs.getInt(1));
                assertEquals(50, stmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testMultipleStatements() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=35]:jdbc:h2:mem:to_multi;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Each statement should have the timeout applied
            try (Statement stmt1 = conn.createStatement()) {
                assertEquals(35, stmt1.getQueryTimeout());
            }

            try (Statement stmt2 = conn.createStatement()) {
                assertEquals(35, stmt2.getQueryTimeout());
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                assertEquals(35, pstmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testGetTimeoutConfigFromNonTimeoutConnection() throws SQLException {
        String url = "jdbc:h2:mem:to_other;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            TimeoutConfig config = TimeoutDriver.getTimeoutConfig(conn);
            assertNull(config);
        }
    }

    @Test
    public void testStatementCreateVariants() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=40]:jdbc:h2:mem:to_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Default createStatement
            try (Statement stmt = conn.createStatement()) {
                assertEquals(40, stmt.getQueryTimeout());
            }

            // createStatement with result set type and concurrency
            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY)) {
                assertEquals(40, stmt.getQueryTimeout());
            }

            // createStatement with holdability
            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY,
                    ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
                assertEquals(40, stmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testPreparedStatementVariants() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=55]:jdbc:h2:mem:to_ps_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Basic prepareStatement
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                assertEquals(55, pstmt.getQueryTimeout());
            }

            // prepareStatement with auto-generated keys
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1", Statement.NO_GENERATED_KEYS)) {
                assertEquals(55, pstmt.getQueryTimeout());
            }

            // prepareStatement with result set type and concurrency
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1",
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY)) {
                assertEquals(55, pstmt.getQueryTimeout());
            }
        }
    }

    @Test
    public void testBatchExecution() throws SQLException {
        String url = "jdbc:timeout[queryTimeout=30]:jdbc:h2:mem:to_batch;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");

                stmt.addBatch("INSERT INTO test VALUES (1)");
                stmt.addBatch("INSERT INTO test VALUES (2)");
                stmt.addBatch("INSERT INTO test VALUES (3)");

                int[] results = stmt.executeBatch();
                assertEquals(3, results.length);
                assertEquals(30, stmt.getQueryTimeout());
            }
        }
    }

}
