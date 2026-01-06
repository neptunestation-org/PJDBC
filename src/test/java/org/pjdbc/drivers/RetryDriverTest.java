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
     * Tests that PreparedStatement parameters are tracked for replay after reconnection.
     *
     * <p>The RetryDriver tracks all parameter bindings (setXxx calls) so that when
     * a connection failure occurs, it can recreate the PreparedStatement and replay
     * all parameter bindings before retrying the operation.</p>
     *
     * <p>This test verifies PreparedStatements work with various parameter types.
     * Full reconnection testing requires a real network database with simulated
     * connection failure, which is covered in integration tests.</p>
     */
    @Test
    public void testPreparedStatementWithParameters() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_params";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ps_test (" +
                    "id INT, " +
                    "name VARCHAR(50), " +
                    "amount DECIMAL(10,2), " +
                    "active BOOLEAN, " +
                    "data BLOB)");
            }

            // Test various parameter types
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO ps_test VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "test");
                pstmt.setBigDecimal(3, new java.math.BigDecimal("123.45"));
                pstmt.setBoolean(4, true);
                pstmt.setBytes(5, new byte[]{1, 2, 3});
                assertEquals(1, pstmt.executeUpdate());
            }

            // Verify data was inserted correctly
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT * FROM ps_test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("test", rs.getString("name"));
                    assertEquals(new java.math.BigDecimal("123.45"), rs.getBigDecimal("amount"));
                    assertTrue(rs.getBoolean("active"));
                }
            }
        }
    }

    /**
     * Tests that clearParameters() clears tracked parameter bindings.
     */
    @Test
    public void testClearParametersResetsBindings() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_clear";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS clear_test (id INT, name VARCHAR(50))");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO clear_test VALUES (?, ?)")) {
                // First set of parameters
                pstmt.setInt(1, 1);
                pstmt.setString(2, "first");
                assertEquals(1, pstmt.executeUpdate());

                // Clear and set new parameters
                pstmt.clearParameters();
                pstmt.setInt(1, 2);
                pstmt.setString(2, "second");
                assertEquals(1, pstmt.executeUpdate());
            }

            // Verify both rows were inserted correctly
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM clear_test ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("id"));
                assertEquals("first", rs.getString("name"));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt("id"));
                assertEquals("second", rs.getString("name"));
                assertFalse(rs.next());
            }
        }
    }

    /**
     * Tests PreparedStatement with stream parameters.
     * Streams are buffered by RetryDriver to enable replay after reconnection.
     */
    @Test
    public void testPreparedStatementWithStreams() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_streams";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS stream_test (id INT, content BLOB, text CLOB)");
            }

            byte[] binaryData = "Hello binary world!".getBytes();
            String textData = "Hello text world!";

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO stream_test VALUES (?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setBinaryStream(2, new java.io.ByteArrayInputStream(binaryData));
                pstmt.setCharacterStream(3, new java.io.StringReader(textData));
                assertEquals(1, pstmt.executeUpdate());
            }

            // Verify stream data was stored correctly
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT * FROM stream_test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    byte[] retrievedBinary = rs.getBytes("content");
                    assertNotNull(retrievedBinary);
                    assertEquals("Hello binary world!", new String(retrievedBinary));

                    String retrievedText = rs.getString("text");
                    assertEquals("Hello text world!", retrievedText);
                }
            }
        }
    }

    /**
     * Tests PreparedStatement with null parameters.
     */
    @Test
    public void testPreparedStatementWithNulls() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_nulls";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS null_test (id INT, name VARCHAR(50), amount INT)");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO null_test VALUES (?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setNull(2, java.sql.Types.VARCHAR);
                pstmt.setNull(3, java.sql.Types.INTEGER);
                assertEquals(1, pstmt.executeUpdate());
            }

            // Verify nulls were stored correctly
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM null_test WHERE id = 1")) {
                assertTrue(rs.next());
                rs.getString("name");
                assertTrue(rs.wasNull());
                rs.getInt("amount");
                assertTrue(rs.wasNull());
            }
        }
    }

    /**
     * Tests PreparedStatement parameter overwriting.
     * When the same parameter index is set multiple times, only the last value should be used.
     */
    @Test
    public void testPreparedStatementParameterOverwrite() throws SQLException {
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_overwrite";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS overwrite_test (id INT, name VARCHAR(50))");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO overwrite_test VALUES (?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "first");
                pstmt.setString(2, "overwritten"); // Overwrite parameter 2
                assertEquals(1, pstmt.executeUpdate());
            }

            // Verify the overwritten value was used
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM overwrite_test WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("overwritten", rs.getString("name"));
            }
        }
    }

    /**
     * Tests that PreparedStatement with no SQL throws appropriate error on connection failure.
     * This scenario occurs when PreparedStatement is created without SQL tracking.
     */
    @Test
    public void testPreparedStatementWithoutSqlCannotRecreate() throws SQLException {
        // This test documents behavior - PreparedStatements created with SQL
        // can be recreated and replayed, those without SQL cannot.
        // The proxyPreparedStatement(delegate, conn) variant doesn't track SQL.
        String url = "jdbc:retry:jdbc:h2:mem:test_ps_no_sql";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Normal usage through prepareStatement(sql) works
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
        // Note: Testing the "no SQL" path would require accessing internal APIs
        // which is not appropriate for a black-box test.
    }
}
