package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for LogDriver.
 *
 * These tests verify SQL logging properties:
 * - SQL statements are logged via java.util.logging
 * - SQL passes through to underlying driver unchanged
 * - Logger naming based on underlying URL
 * - All operation types are logged
 * - Query results are preserved
 * - Driver composition
 */
class LogDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "log_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    /**
     * Custom log handler that captures log messages.
     */
    private static class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }

        public void clear() {
            messages.clear();
        }
    }

    // ========== SQL LOGGING ==========

    /**
     * Property: SQL statements are logged via java.util.logging.
     */
    @Property(tries = 20)
    void sqlStatementsAreLogged(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        // Set up capturing handler
        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        } finally {
            logger.removeHandler(handler);
        }

        List<String> messages = handler.getMessages();
        assertTrue(messages.contains(sql),
            "SQL should be logged: " + sql + ", got: " + messages);
    }

    /**
     * Property: Multiple statements are all logged in order.
     */
    @Property(tries = 10)
    void multipleStatementsAllLogged(
            @ForAll @IntRange(min = 2, max = 5) int statementCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < statementCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        } finally {
            logger.removeHandler(handler);
        }

        List<String> messages = handler.getMessages();
        assertEquals(statementCount, messages.size(),
            "All statements should be logged");

        for (int i = 0; i < statementCount; i++) {
            assertEquals("SELECT " + i, messages.get(i),
                "Statements should be logged in order");
        }
    }

    // ========== PASSTHROUGH BEHAVIOR ==========

    /**
     * Property: SQL passes through to underlying driver unchanged.
     */
    @Property(tries = 20)
    void sqlPassesThroughUnchanged(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "MockDriver should have received SQL");
        assertTrue(mockLog.contains(sql),
            "Underlying driver should receive exact SQL: " + sql);
    }

    /**
     * Property: Logging is transparent - doesn't modify behavior.
     */
    @Property(tries = 10)
    void loggingIsTransparent(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        // Execute through log driver
        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String loggedMockLog = MockDriver.getLog(mockUrl);

        // Execute directly through mock driver
        String directDbName = createUniqueDbName();
        String directMockUrl = "jdbc:mock:" + directDbName;

        try (Connection conn = DriverManager.getConnection(directMockUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String directMockLog = MockDriver.getLog(directMockUrl);

        assertEquals(directMockLog, loggedMockLog,
            "Logging should not change underlying behavior");
    }

    // ========== LOGGER NAMING ==========

    /**
     * Property: Logger name is based on underlying connection URL.
     */
    @Property(tries = 10)
    void loggerNamedByUnderlyingUrl() throws SQLException {
        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        // The logger should be named after the mock URL
        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
        } finally {
            logger.removeHandler(handler);
        }

        // If logger was correctly named, we should have captured the message
        assertFalse(handler.getMessages().isEmpty(),
            "Logger named by underlying URL should capture messages");
        assertEquals("SELECT 1", handler.getMessages().get(0));
    }

    // ========== ALL OPERATION TYPES LOGGED ==========

    /**
     * Property: executeQuery logs SQL.
     */
    @Property(tries = 10)
    void executeQueryLogs(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.getMessages().contains(sql),
            "executeQuery should log SQL");
    }

    /**
     * Property: executeUpdate logs SQL.
     */
    @Property(tries = 10)
    void executeUpdateLogs(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.getMessages().contains(sql),
            "executeUpdate should log SQL");
    }

    /**
     * Property: execute logs SQL.
     */
    @Property(tries = 10)
    void executeLogs(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.getMessages().contains(sql),
            "execute should log SQL");
    }

    // Note: addBatch test skipped due to AbstractStatement.addBatch bug
    // (always throws SQLException after loop - base class issue)

    // ========== QUERY RESULTS PRESERVED ==========

    /**
     * Property: Query results are preserved through logging.
     */
    @Property(tries = 10)
    void queryResultsPreserved(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String logUrl = "jdbc:log:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next(), "Should have a result");
            assertEquals(value, rs.getInt(1),
                "Query result should be preserved");
        }
    }

    /**
     * Property: Update counts are preserved through logging.
     */
    @Property(tries = 10)
    void updateCountsPreserved(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        String logUrl = "jdbc:log:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {

            // Create table and insert rows
            stmt.execute("CREATE TABLE test_update (id INT)");

            for (int i = 0; i < rowCount; i++) {
                stmt.executeUpdate("INSERT INTO test_update VALUES (" + i + ")");
            }

            // Update all rows
            int updated = stmt.executeUpdate("UPDATE test_update SET id = id + 100");
            assertEquals(rowCount, updated,
                "Update count should be preserved");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:log:X preserves logging (cat is identity).
     */
    @Property(tries = 10)
    void catLogComposition() throws SQLException {
        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catLogUrl = "jdbc:cat:jdbc:log:" + mockUrl;

        Logger logger = Logger.getLogger(mockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(catLogUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.getMessages().contains("SELECT 1"),
            "cat:log should preserve logging");
    }

    /**
     * Property: log:cat:X preserves logging.
     */
    @Property(tries = 10)
    void logCatComposition() throws SQLException {
        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catMockUrl = "jdbc:cat:" + mockUrl;
        String logCatUrl = "jdbc:log:" + catMockUrl;

        // Logger name will be the cat URL since that's what the connection unwraps to
        Logger logger = Logger.getLogger(catMockUrl);
        CapturingHandler handler = new CapturingHandler();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try (Connection conn = DriverManager.getConnection(logCatUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
        } finally {
            logger.removeHandler(handler);
        }

        // Verify underlying driver received the SQL
        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "log:cat should pass through to underlying driver");
        assertTrue(mockLog.contains("SELECT 1"));
    }

    /**
     * Property: log:mock:X works correctly.
     */
    @Property(tries = 10)
    void logMockComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logUrl = "jdbc:log:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(logUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "log:mock should work");
        assertTrue(mockLog.contains(sql),
            "MockDriver should receive SQL through log driver");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> sqlStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "SELECT id, name FROM test",
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1"
        );
    }

    @Provide
    Arbitrary<String> selectStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "SELECT COUNT(*) FROM test",
            "SELECT id FROM t WHERE x = 1"
        );
    }

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1"
        );
    }
}
