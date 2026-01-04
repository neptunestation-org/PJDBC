package org.pjdbc.drivers;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.drivers.AuditDriver.AuditConfig;
import org.pjdbc.drivers.AuditDriver.AuditEvent;
import org.pjdbc.drivers.AuditDriver.LogLevel;
import org.pjdbc.drivers.AuditDriver.OperationType;

public class AuditDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.AuditDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
    }

    @After
    public void cleanup() {
        AuditDriver.clearAuditLog();
        AuditDriver.clearAuditHandlers();
    }

    // ========== URL Acceptance Tests ==========

    @Test
    public void testAcceptsURL() throws SQLException {
        AuditDriver driver = new AuditDriver();
        assertTrue(driver.acceptsURL("jdbc:audit:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:audit[logLevel=failure]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:audit[name=payments,includeSql=false]:jdbc:postgresql://localhost/db"));
        assertFalse(driver.acceptsURL("jdbc:log:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:h2:mem:test"));
    }

    // ========== Configuration Tests ==========

    @Test
    public void testDefaultConfig() {
        AuditConfig config = new AuditConfig("jdbc:audit:jdbc:h2:mem:test");
        assertEquals("audit", config.getName());
        assertEquals(LogLevel.ALL, config.getLogLevel());
        assertTrue(config.isIncludeSql());
        assertTrue(config.isIncludeTime());
        assertTrue(config.isIncludeRows());
    }

    @Test
    public void testCustomName() {
        AuditConfig config = new AuditConfig("jdbc:audit[name=payments-db]:jdbc:h2:mem:test");
        assertEquals("payments-db", config.getName());
    }

    @Test
    public void testLogLevelSuccess() {
        AuditConfig config = new AuditConfig("jdbc:audit[logLevel=success]:jdbc:h2:mem:test");
        assertEquals(LogLevel.SUCCESS, config.getLogLevel());
        assertTrue(config.shouldLog(true));
        assertFalse(config.shouldLog(false));
    }

    @Test
    public void testLogLevelFailure() {
        AuditConfig config = new AuditConfig("jdbc:audit[logLevel=failure]:jdbc:h2:mem:test");
        assertEquals(LogLevel.FAILURE, config.getLogLevel());
        assertFalse(config.shouldLog(true));
        assertTrue(config.shouldLog(false));
    }

    @Test
    public void testLogLevelAll() {
        AuditConfig config = new AuditConfig("jdbc:audit[logLevel=all]:jdbc:h2:mem:test");
        assertEquals(LogLevel.ALL, config.getLogLevel());
        assertTrue(config.shouldLog(true));
        assertTrue(config.shouldLog(false));
    }

    @Test
    public void testIncludeSqlFalse() {
        AuditConfig config = new AuditConfig("jdbc:audit[includeSql=false]:jdbc:h2:mem:test");
        assertFalse(config.isIncludeSql());
    }

    @Test
    public void testIncludeTimeFalse() {
        AuditConfig config = new AuditConfig("jdbc:audit[includeTime=false]:jdbc:h2:mem:test");
        assertFalse(config.isIncludeTime());
    }

    @Test
    public void testIncludeRowsFalse() {
        AuditConfig config = new AuditConfig("jdbc:audit[includeRows=false]:jdbc:h2:mem:test");
        assertFalse(config.isIncludeRows());
    }

    @Test
    public void testMultipleParameters() {
        AuditConfig config = new AuditConfig(
            "jdbc:audit[name=mydb,logLevel=failure,includeSql=false,includeTime=true,includeRows=false]:jdbc:h2:mem:test"
        );
        assertEquals("mydb", config.getName());
        assertEquals(LogLevel.FAILURE, config.getLogLevel());
        assertFalse(config.isIncludeSql());
        assertTrue(config.isIncludeTime());
        assertFalse(config.isIncludeRows());
    }

    @Test
    public void testConfigToString() {
        AuditConfig config = new AuditConfig("audit", LogLevel.ALL, true, true, true);
        String str = config.toString();
        assertTrue(str.contains("audit"));
        assertTrue(str.contains("ALL"));
    }

    // ========== Audit Event Tests ==========

    @Test
    public void testAuditEventCreation() {
        AuditEvent event = new AuditEvent(
            "test-audit", "conn-1", "testuser",
            OperationType.QUERY, "SELECT 1",
            100, 5, true, null, null
        );

        assertEquals("test-audit", event.getAuditName());
        assertEquals("conn-1", event.getConnectionId());
        assertEquals("testuser", event.getUser());
        assertEquals(OperationType.QUERY, event.getOperationType());
        assertEquals("SELECT 1", event.getSql());
        assertEquals(100, event.getExecutionTimeMs());
        assertEquals(5, event.getRowsAffected());
        assertTrue(event.isSuccess());
        assertNull(event.getErrorMessage());
        assertNotNull(event.getTimestamp());
        assertTrue(event.getId() > 0);
    }

    @Test
    public void testAuditEventFailure() {
        AuditEvent event = new AuditEvent(
            "test-audit", "conn-1", "testuser",
            OperationType.UPDATE, "DELETE FROM users",
            50, -1, false, "Access denied", "42501"
        );

        assertFalse(event.isSuccess());
        assertEquals("Access denied", event.getErrorMessage());
        assertEquals("42501", event.getErrorSqlState());
    }

    @Test
    public void testAuditEventToLogString() {
        AuditConfig config = new AuditConfig("test", LogLevel.ALL, true, true, true);
        AuditEvent event = new AuditEvent(
            "test", "conn-1", "testuser",
            OperationType.QUERY, "SELECT * FROM users",
            100, 10, true, null, null
        );

        String logStr = event.toLogString(config);
        assertTrue(logStr.contains("[AUDIT-"));
        assertTrue(logStr.contains("test"));
        assertTrue(logStr.contains("testuser"));
        assertTrue(logStr.contains("QUERY"));
        assertTrue(logStr.contains("SELECT * FROM users"));
        assertTrue(logStr.contains("100ms"));
        assertTrue(logStr.contains("rows: 10"));
        assertTrue(logStr.contains("SUCCESS"));
    }

    @Test
    public void testAuditEventToLogStringWithoutSql() {
        AuditConfig config = new AuditConfig("test", LogLevel.ALL, false, true, true);
        AuditEvent event = new AuditEvent(
            "test", "conn-1", "testuser",
            OperationType.QUERY, "SELECT secret FROM passwords",
            100, 10, true, null, null
        );

        String logStr = event.toLogString(config);
        assertFalse(logStr.contains("SELECT secret"));
    }

    // ========== Integration Tests ==========

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:audit:jdbc:h2:mem:aud_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);

            AuditConfig config = AuditDriver.getAuditConfig(conn);
            assertNotNull(config);
            assertEquals("audit", config.getName());
        }
    }

    @Test
    public void testQueryAuditLogged() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-query]:jdbc:h2:mem:aud_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42 AS answer")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("answer"));
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-query");
        assertEquals(1, events.size());

        AuditEvent event = events.get(0);
        assertEquals("test-query", event.getAuditName());
        assertEquals(OperationType.QUERY, event.getOperationType());
        assertEquals("SELECT 42 AS answer", event.getSql());
        assertTrue(event.isSuccess());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    public void testUpdateAuditLogged() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-update]:jdbc:h2:mem:aud_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
                int rows = stmt.executeUpdate("INSERT INTO test VALUES (1)");
                assertEquals(1, rows);
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-update");
        assertEquals(2, events.size());

        // Second event is the INSERT
        AuditEvent insertEvent = events.get(1);
        assertEquals(OperationType.UPDATE, insertEvent.getOperationType());
        assertEquals(1, insertEvent.getRowsAffected());
        assertTrue(insertEvent.isSuccess());
    }

    @Test
    public void testFailureAuditLogged() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-fail]:jdbc:h2:mem:aud_fail;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.executeQuery("SELECT * FROM nonexistent_table");
                    fail("Should have thrown");
                } catch (SQLException e) {
                    // Expected
                }
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-fail");
        assertEquals(1, events.size());

        AuditEvent event = events.get(0);
        assertFalse(event.isSuccess());
        assertNotNull(event.getErrorMessage());
        assertNotNull(event.getErrorSqlState());
    }

    @Test
    public void testLogLevelFailureOnlyLogsFailures() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=fail-only,logLevel=failure]:jdbc:h2:mem:aud_failonly;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This succeeds - should NOT be logged
                stmt.executeQuery("SELECT 1");

                // This fails - should be logged
                try {
                    stmt.executeQuery("SELECT * FROM nonexistent_table");
                } catch (SQLException e) {
                    // Expected
                }
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("fail-only");
        assertEquals(1, events.size());
        assertFalse(events.get(0).isSuccess());
    }

    @Test
    public void testLogLevelSuccessOnlyLogsSuccesses() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=success-only,logLevel=success]:jdbc:h2:mem:aud_successonly;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This succeeds - should be logged
                stmt.executeQuery("SELECT 1");

                // This fails - should NOT be logged
                try {
                    stmt.executeQuery("SELECT * FROM nonexistent_table");
                } catch (SQLException e) {
                    // Expected
                }
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("success-only");
        assertEquals(1, events.size());
        assertTrue(events.get(0).isSuccess());
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-pstmt]:jdbc:h2:mem:aud_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
                stmt.execute("INSERT INTO test VALUES (1, 'Alice')");
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString(1));
                }
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-pstmt");
        assertTrue(events.size() >= 3);

        // Find the prepared statement query
        AuditEvent pstmtEvent = null;
        for (AuditEvent e : events) {
            if (e.getSql() != null && e.getSql().contains("SELECT name FROM test WHERE id = ?")) {
                pstmtEvent = e;
                break;
            }
        }
        assertNotNull("Should have logged prepared statement", pstmtEvent);
        assertEquals(OperationType.QUERY, pstmtEvent.getOperationType());
    }

    @Test
    public void testBatchExecution() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-batch]:jdbc:h2:mem:aud_batch;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");

                stmt.addBatch("INSERT INTO test VALUES (1)");
                stmt.addBatch("INSERT INTO test VALUES (2)");
                stmt.addBatch("INSERT INTO test VALUES (3)");

                int[] results = stmt.executeBatch();
                assertEquals(3, results.length);
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-batch");

        // Find the batch event
        AuditEvent batchEvent = null;
        for (AuditEvent e : events) {
            if (e.getOperationType() == OperationType.BATCH) {
                batchEvent = e;
                break;
            }
        }
        assertNotNull("Should have logged batch execution", batchEvent);
        assertEquals(3, batchEvent.getRowsAffected());
    }

    @Test
    public void testDriverChaining() throws SQLException {
        AuditDriver.clearAuditLog();
        // Chain: audit -> log -> h2
        String url = "jdbc:audit[name=test-chain]:jdbc:log:jdbc:h2:mem:aud_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 123")) {
                assertTrue(rs.next());
                assertEquals(123, rs.getInt(1));
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-chain");
        assertEquals(1, events.size());
    }

    @Test
    public void testCustomAuditHandler() throws SQLException {
        AuditDriver.clearAuditLog();
        List<AuditEvent> capturedEvents = new ArrayList<>();
        AuditDriver.addAuditHandler(capturedEvents::add);

        try {
            String url = "jdbc:audit[name=test-handler]:jdbc:h2:mem:aud_handler;DB_CLOSE_DELAY=-1";
            try (Connection conn = DriverManager.getConnection(url)) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    rs.next();
                }
            }

            assertEquals(1, capturedEvents.size());
            assertEquals("test-handler", capturedEvents.get(0).getAuditName());
        } finally {
            AuditDriver.clearAuditHandlers();
        }
    }

    @Test
    public void testGetAuditConfigFromNonAuditConnection() throws SQLException {
        String url = "jdbc:h2:mem:aud_other;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            AuditConfig config = AuditDriver.getAuditConfig(conn);
            assertNull(config);
        }
    }

    @Test
    public void testClearAuditLog() throws SQLException {
        String url = "jdbc:audit[name=test-clear]:jdbc:h2:mem:aud_clear;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
        }

        assertFalse(AuditDriver.getAuditLog().isEmpty());

        AuditDriver.clearAuditLog();

        assertTrue(AuditDriver.getAuditLog().isEmpty());
    }

    @Test
    public void testMultipleConnections() throws SQLException {
        AuditDriver.clearAuditLog();
        String url1 = "jdbc:audit[name=conn1]:jdbc:h2:mem:aud_multi1;DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:audit[name=conn2]:jdbc:h2:mem:aud_multi2;DB_CLOSE_DELAY=-1";

        try (Connection conn1 = DriverManager.getConnection(url1);
             Connection conn2 = DriverManager.getConnection(url2)) {

            try (Statement stmt1 = conn1.createStatement()) {
                stmt1.executeQuery("SELECT 1");
            }
            try (Statement stmt2 = conn2.createStatement()) {
                stmt2.executeQuery("SELECT 2");
            }
        }

        assertEquals(1, AuditDriver.getAuditLog("conn1").size());
        assertEquals(1, AuditDriver.getAuditLog("conn2").size());
    }

    @Test
    public void testAuditEventIds() throws SQLException {
        AuditDriver.clearAuditLog();
        String url = "jdbc:audit[name=test-ids]:jdbc:h2:mem:aud_ids;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                stmt.executeQuery("SELECT 2");
                stmt.executeQuery("SELECT 3");
            }
        }

        List<AuditEvent> events = AuditDriver.getAuditLog("test-ids");
        assertEquals(3, events.size());

        // IDs should be sequential
        assertTrue(events.get(1).getId() > events.get(0).getId());
        assertTrue(events.get(2).getId() > events.get(1).getId());
    }
}
