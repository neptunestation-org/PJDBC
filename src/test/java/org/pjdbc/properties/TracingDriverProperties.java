package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for TracingDriver.
 *
 * These tests verify tracing properties:
 * - Span creation for different operation types
 * - Operation naming with default and custom prefixes
 * - SQL inclusion/exclusion control
 * - Parameter inclusion/exclusion control
 * - Row count inclusion/exclusion control
 * - Error recording on failures
 * - Span timing properties
 * - Driver composition
 */
class TracingDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "trace_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    @BeforeProperty
    void clearTracer() {
        TracingDriver.getDefaultTracer().clear();
    }

    private void setupTestTable(String dbName) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
            }
        }
        // Clear spans created during setup
        TracingDriver.getDefaultTracer().clear();
    }

    // ========== SPAN CREATION ==========

    /**
     * Property: executeQuery creates exactly one span per query.
     */
    @Property(tries = 30)
    void queriesCreateSpans(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume result
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size(),
            "Each executeQuery should create exactly one span");
    }

    /**
     * Property: executeUpdate creates exactly one span per update.
     */
    @Property(tries = 30)
    void updatesCreateSpans(
            @ForAll @IntRange(min = 1, max = 5) int updateCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < updateCount; i++) {
                stmt.executeUpdate("UPDATE users SET name = 'Updated" + i + "' WHERE id = 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(updateCount, spans.size(),
            "Each executeUpdate should create exactly one span");
    }

    /**
     * Property: execute creates exactly one span per execution.
     */
    @Property(tries = 30)
    void executesCreateSpans(
            @ForAll @IntRange(min = 1, max = 5) int executeCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < executeCount; i++) {
                stmt.execute("SELECT " + i);
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(executeCount, spans.size(),
            "Each execute should create exactly one span");
    }

    // ========== OPERATION NAMING ==========

    /**
     * Property: executeQuery spans are named with "query" operation.
     */
    @Property(tries = 20)
    void querySpansNamedCorrectly(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // consume
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("db.query", spans.get(0).getOperationName(),
            "Query span should be named 'db.query'");
        assertEquals("query", spans.get(0).getAttribute("db.operation"),
            "db.operation attribute should be 'query'");
    }

    /**
     * Property: executeUpdate spans are named with "update" operation.
     */
    @Property(tries = 20)
    void updateSpansNamedCorrectly(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("db.update", spans.get(0).getOperationName(),
            "Update span should be named 'db.update'");
        assertEquals("update", spans.get(0).getAttribute("db.operation"),
            "db.operation attribute should be 'update'");
    }

    /**
     * Property: Custom spanPrefix is applied to operation names.
     */
    @Property(tries = 20)
    void customSpanPrefixApplied(
            @ForAll("spanPrefixes") String prefix) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[spanPrefix=" + prefix + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
            // consume
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).getOperationName().startsWith(prefix),
            "Operation name should start with custom prefix: " + prefix);
        assertEquals(prefix + "query", spans.get(0).getOperationName(),
            "Operation name should be prefix + operation");
    }

    // ========== SQL INCLUSION CONTROL ==========

    /**
     * Property: With includeSql=true (default), SQL is included in span.
     */
    @Property(tries = 20)
    void includeSqlTrueIncludesSql(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[includeSql=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // consume
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals(sql, spans.get(0).getAttribute("db.statement"),
            "SQL should be included in db.statement");
    }

    /**
     * Property: With includeSql=false, SQL is excluded from span.
     */
    @Property(tries = 20)
    void includeSqlFalseExcludesSql(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[includeSql=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // consume
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertNull(spans.get(0).getAttribute("db.statement"),
            "SQL should NOT be included when includeSql=false");
    }

    // ========== PARAMETER INCLUSION CONTROL ==========

    /**
     * Property: With includeParams=true, parameters are included in span.
     */
    @Property(tries = 20)
    void includeParamsTrueIncludesParams(
            @ForAll @IntRange(min = 1, max = 100) int paramValue) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[includeParams=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            pstmt.setInt(1, paramValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                // consume
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        String params = spans.get(0).getAttribute("db.parameters");
        assertNotNull(params, "Parameters should be included when includeParams=true");
        assertTrue(params.contains(String.valueOf(paramValue)),
            "Parameter value should be in db.parameters: " + params);
    }

    /**
     * Property: With includeParams=false (default), parameters are excluded.
     */
    @Property(tries = 20)
    void includeParamsFalseExcludesParams(
            @ForAll @IntRange(min = 1, max = 100) int paramValue) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        // Default is includeParams=false
        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            pstmt.setInt(1, paramValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                // consume
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertNull(spans.get(0).getAttribute("db.parameters"),
            "Parameters should NOT be included by default (security)");
    }

    // ========== ROW COUNT INCLUSION CONTROL ==========

    /**
     * Property: With includeRowCount=true (default), row count is included.
     */
    @Property(tries = 20)
    void includeRowCountTrueIncludesRows(
            @ForAll @IntRange(min = 1, max = 2) int userId) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[includeRowCount=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            int rows = stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = " + userId);
            assertEquals(1, rows);
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("1", spans.get(0).getAttribute("db.rows_affected"),
            "Row count should be included when includeRowCount=true");
    }

    /**
     * Property: With includeRowCount=false, row count is excluded.
     */
    @Property(tries = 20)
    void includeRowCountFalseExcludesRows(
            @ForAll @IntRange(min = 1, max = 2) int userId) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace[includeRowCount=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = " + userId);
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertNull(spans.get(0).getAttribute("db.rows_affected"),
            "Row count should NOT be included when includeRowCount=false");
    }

    // ========== ERROR RECORDING ==========

    /**
     * Property: Failed queries record error in span.
     */
    @Property(tries = 20)
    void failedQueriesRecordError(
            @ForAll("invalidTableNames") String tableName) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM " + tableName);
            fail("Should have thrown SQLException");
        } catch (SQLException expected) {
            // Expected - table doesn't exist
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertTrue(span.hasError(), "Span should have error flag set");
        assertEquals("true", span.getAttribute("error"),
            "error attribute should be 'true'");
        assertNotNull(span.getAttribute("error.type"),
            "error.type should be recorded");
        assertNotNull(span.getAttribute("error.message"),
            "error.message should be recorded");
    }

    /**
     * Property: Successful queries do not record error.
     */
    @Property(tries = 20)
    void successfulQueriesNoError(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Success
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertFalse(span.hasError(), "Successful query should not have error");
        assertNull(span.getAttribute("error"),
            "error attribute should be absent on success");
    }

    // ========== SPAN TIMING PROPERTIES ==========

    /**
     * Property: Span timing is valid (end >= start).
     */
    @Property(tries = 30)
    void spanTimingIsValid(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size());

        for (TracingDriver.SpanData span : spans) {
            assertTrue(span.getStartTimeNanos() > 0,
                "Start time should be positive");
            assertTrue(span.getEndTimeNanos() >= span.getStartTimeNanos(),
                "End time should be >= start time");
        }
    }

    /**
     * Property: Span duration is non-negative.
     */
    @Property(tries = 30)
    void durationIsNonNegative(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();

        for (TracingDriver.SpanData span : spans) {
            assertTrue(span.getDurationMs() >= 0,
                "Duration should be non-negative");
        }
    }

    /**
     * Property: Multiple spans have non-decreasing start times.
     */
    @Property(tries = 20)
    void spanTimingMonotonicity(
            @ForAll @IntRange(min = 2, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceUrl = "jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size());

        for (int i = 1; i < spans.size(); i++) {
            assertTrue(spans.get(i).getStartTimeNanos() >= spans.get(i - 1).getStartTimeNanos(),
                "Span start times should be monotonically non-decreasing");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:trace:X creates spans (cat is identity).
     */
    @Property(tries = 20)
    void catTraceComposition(
            @ForAll @IntRange(min = 1, max = 3) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String catTraceUrl = "jdbc:cat:jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(catTraceUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size(),
            "cat:trace:X should create spans (cat is identity)");
    }

    /**
     * Property: trace:cat:X creates spans (cat is identity).
     */
    @Property(tries = 20)
    void traceCatComposition(
            @ForAll @IntRange(min = 1, max = 3) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String traceCatUrl = "jdbc:trace:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(traceCatUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size(),
            "trace:cat:X should create spans");
    }

    /**
     * Property: log:trace:X creates spans (log is passthrough).
     */
    @Property(tries = 20)
    void logTraceComposition(
            @ForAll @IntRange(min = 1, max = 3) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String logTraceUrl = "jdbc:log:jdbc:trace:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(logTraceUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(queryCount, spans.size(),
            "log:trace:X should create spans (log is passthrough)");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> selectStatements() {
        return Arbitraries.of(
            "SELECT * FROM users",
            "SELECT id FROM users",
            "SELECT name FROM users WHERE id = 1",
            "SELECT COUNT(*) FROM users",
            "SELECT id, name FROM users ORDER BY id"
        );
    }

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "UPDATE users SET name = 'Updated' WHERE id = 1",
            "UPDATE users SET name = 'Changed' WHERE id = 2",
            "DELETE FROM users WHERE id = 99"
        );
    }

    @Provide
    Arbitrary<String> spanPrefixes() {
        return Arbitraries.of(
            "sql.",
            "jdbc.",
            "database.",
            "query.",
            "db."
        );
    }

    @Provide
    Arbitrary<String> invalidTableNames() {
        return Arbitraries.of(
            "nonexistent_table_abc",
            "missing_table_xyz",
            "no_such_table_123"
        );
    }
}
