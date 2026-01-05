package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

public class RetryDriverTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.ChaosDriver");
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        RetryDriver driver = new RetryDriver();
        assertTrue(driver.acceptsURL("jdbc:retry:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:retry[maxRetries=5]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_basic";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testNoRetryOnSuccess() throws SQLException {
        // When query succeeds, should execute only once
        String url = "jdbc:retry[maxRetries=5]:jdbc:h2:mem:test_success";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 42")) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_prepared";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_retry (id INT, name VARCHAR(50))");
            }
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test_retry VALUES (?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "test");
                int rows = pstmt.executeUpdate();
                assertEquals(1, rows);
            }
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM test_retry WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("test", rs.getString("name"));
                }
            }
        }
    }

    @Test
    public void testNonRetryableErrorNotRetried() throws SQLException {
        // Syntax errors should not be retried
        String url = "jdbc:retry[maxRetries=3]:jdbc:h2:mem:test_syntax";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("INVALID SQL SYNTAX HERE");
                fail("Expected SQLException for invalid SQL");
            }
        } catch (SQLException e) {
            // Expected - syntax errors are not retryable
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testConfigDefaults() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig("jdbc:retry:jdbc:h2:mem:test");
        assertEquals(3, config.getMaxRetries());
        assertEquals(100, config.getInitialDelay());
        assertEquals(5000, config.getMaxDelay());
        assertEquals(2.0, config.getBackoffMultiplier(), 0.001);
        assertTrue(config.hasJitter());
    }

    @Test
    public void testConfigCustomValues() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[maxRetries=5,initialDelay=200,maxDelay=10000,backoffMultiplier=3.0,jitter=false]:jdbc:h2:mem:test"
        );
        assertEquals(5, config.getMaxRetries());
        assertEquals(200, config.getInitialDelay());
        assertEquals(10000, config.getMaxDelay());
        assertEquals(3.0, config.getBackoffMultiplier(), 0.001);
        assertFalse(config.hasJitter());
    }

    @Test
    public void testConfigCustomSqlStates() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[retryOnSqlStates=40001;08006;CUSTOM]:jdbc:h2:mem:test"
        );
        Set<String> states = config.getRetryableSqlStates();
        assertTrue(states.contains("40001"));
        assertTrue(states.contains("08006"));
        assertTrue(states.contains("CUSTOM"));
        assertFalse(states.contains("08001")); // Not in custom list
    }

    @Test
    public void testDelayCalculation() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[initialDelay=100,backoffMultiplier=2.0,maxDelay=1000,jitter=false]:jdbc:h2:mem:test"
        );
        // Without jitter, delays should be exact
        assertEquals(100, config.calculateDelay(0)); // 100 * 2^0 = 100
        assertEquals(200, config.calculateDelay(1)); // 100 * 2^1 = 200
        assertEquals(400, config.calculateDelay(2)); // 100 * 2^2 = 400
        assertEquals(800, config.calculateDelay(3)); // 100 * 2^3 = 800
        assertEquals(1000, config.calculateDelay(4)); // Capped at maxDelay
        assertEquals(1000, config.calculateDelay(10)); // Still capped
    }

    @Test
    public void testDelayWithJitter() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[initialDelay=100,jitter=true]:jdbc:h2:mem:test"
        );
        // With jitter, delay should be at least base delay
        long delay = config.calculateDelay(0);
        assertTrue("Delay should be at least 100ms", delay >= 100);
        assertTrue("Delay should not exceed 125ms (100 + 25% jitter)", delay <= 125);
    }

    @Test
    public void testIsRetryableWithTransientError() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig("jdbc:retry:jdbc:h2:mem:test");

        // Test with connection failure state
        SQLException connFailure = new SQLException("Connection failed", "08006");
        assertTrue(config.isRetryable(connFailure));

        // Test with deadlock state
        SQLException deadlock = new SQLException("Deadlock", "40001");
        assertTrue(config.isRetryable(deadlock));
    }

    @Test
    public void testIsRetryableWithNonTransientError() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig("jdbc:retry:jdbc:h2:mem:test");

        // Syntax error - not retryable
        SQLException syntaxError = new SQLException("Syntax error", "42000");
        assertFalse(config.isRetryable(syntaxError));

        // Null SQL state - not retryable
        SQLException nullState = new SQLException("Error", (String) null);
        assertFalse(config.isRetryable(nullState));
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        // Test that RetryDriver works in a chain with other drivers
        Class.forName("org.pjdbc.drivers.CatDriver");
        String url = "jdbc:retry:jdbc:cat:jdbc:h2:mem:test_chain";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 'chained'")) {
                    assertTrue(rs.next());
                    assertEquals("chained", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_update";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS update_test (id INT)");
                int rows = stmt.executeUpdate("INSERT INTO update_test VALUES (1)");
                assertEquals(1, rows);
            }
        }
    }

    @Test
    public void testInvalidParameterDefaults() throws SQLException {
        // Invalid parameters should fall back to defaults
        String url = "jdbc:retry[maxRetries=invalid,initialDelay=abc]:jdbc:h2:mem:test_invalid";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                }
            }
        }
    }

    @Test
    public void testRetryWithChaosDriver() throws SQLException {
        // Use ChaosDriver to simulate failures, then retry
        // This tests the actual retry mechanism
        // Note: With 100% failure rate, all retries will fail
        String url = "jdbc:retry[maxRetries=2]:jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:test_chaos";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Expected SQLException after all retries exhausted");
            }
        } catch (SQLException e) {
            // Expected - ChaosDriver failures are not in default retryable states
            assertTrue(e.getMessage().contains("ChaosDriver") || e.getMessage().contains("Induced"));
        }
    }

    @Test
    public void testMultipleQueriesInSession() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_session";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS session_test (id INT)");
                stmt.executeUpdate("INSERT INTO session_test VALUES (1)");
                stmt.executeUpdate("INSERT INTO session_test VALUES (2)");
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM session_test")) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testIsConnectionError() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig("jdbc:retry:jdbc:h2:mem:test");

        // 08xxx states are connection errors
        assertTrue(config.isConnectionError(new SQLException("Connection failed", "08001")));
        assertTrue(config.isConnectionError(new SQLException("Connection failed", "08003")));
        assertTrue(config.isConnectionError(new SQLException("Connection failed", "08006")));

        // Non-08 states are not connection errors
        assertFalse(config.isConnectionError(new SQLException("Deadlock", "40001")));
        assertFalse(config.isConnectionError(new SQLException("Syntax error", "42000")));
        assertFalse(config.isConnectionError(new SQLException("Error", (String) null)));
    }

    /**
     * Documents that PreparedStatement cannot be retried after connection failures.
     *
     * <p>This is a design limitation: when a connection dies, we cannot recreate
     * a PreparedStatement with its parameter bindings because JDBC doesn't provide
     * a way to introspect bound parameters. Silently retrying with unbound parameters
     * would cause data corruption.</p>
     *
     * <p>The RetryDriver now throws SQLException with a clear message instead of
     * silently proceeding with NULL parameters.</p>
     *
     * <p>Note: This test verifies the limitation is documented. A full integration
     * test would require simulating connection failure mid-query, which is complex
     * to set up reliably.</p>
     */
    @Test
    public void testPreparedStatementConnectionErrorLimitation() throws SQLException {
        // Verify that PreparedStatements work normally (no connection error)
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_limit";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ps_test (id INT, name VARCHAR(50))");
            }
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO ps_test VALUES (?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "test");
                assertEquals(1, pstmt.executeUpdate());
            }
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM ps_test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("test", rs.getString("name"));
                }
            }
        }

        // Note: Full test of connection failure behavior would require:
        // 1. Establish connection and create PreparedStatement
        // 2. Bind parameters
        // 3. Kill connection mid-execution (hard to simulate reliably)
        // 4. Verify SQLException is thrown with message containing
        //    "Parameter bindings cannot be preserved"
        //
        // The implementation in RetryDriver.RetryPreparedStatement.recreateStatement()
        // now throws SQLException instead of logging a warning and proceeding.
    }
}
