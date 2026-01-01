package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TracingDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.TracingDriver");
    }

    @Before
    public void clearTracer() {
        TracingDriver.getDefaultTracer().clear();
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
            }
        }
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        TracingDriver driver = new TracingDriver();
        assertTrue(driver.acceptsURL("jdbc:trace:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:trace[tracerName=custom]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_basic";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testQueryTracing() throws SQLException {
        setupTestTable("test_query");
        String url = "jdbc:trace:jdbc:h2:mem:test_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertEquals("db.query", span.getOperationName());
        assertEquals("query", span.getAttribute("db.operation"));
        assertEquals("SELECT * FROM users WHERE id = 1", span.getAttribute("db.statement"));
        assertFalse(span.hasError());
        assertTrue(span.getDurationMs() >= 0);
    }

    @Test
    public void testUpdateTracing() throws SQLException {
        setupTestTable("test_update");
        String url = "jdbc:trace:jdbc:h2:mem:test_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                int rows = stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
                assertEquals(1, rows);
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertEquals("db.update", span.getOperationName());
        assertEquals("1", span.getAttribute("db.rows_affected"));
    }

    @Test
    public void testPreparedStatementTracing() throws SQLException {
        setupTestTable("trace_test_prepared");
        String url = "jdbc:trace:jdbc:h2:mem:trace_test_prepared;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertEquals("db.query", span.getOperationName());
        assertEquals("SELECT * FROM users WHERE id = ?", span.getAttribute("db.statement"));
    }

    @Test
    public void testPreparedStatementWithParameters() throws SQLException {
        setupTestTable("test_params");
        String url = "jdbc:trace[includeParams=true]:jdbc:h2:mem:test_params;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 42);
                try (ResultSet rs = pstmt.executeQuery()) {
                    // No results expected
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertEquals("1=42", span.getAttribute("db.parameters"));
    }

    @Test
    public void testErrorTracing() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_error;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM nonexistent_table");
            }
        } catch (SQLException expected) {
            // Expected
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertTrue(span.hasError());
        assertEquals("true", span.getAttribute("error"));
        assertNotNull(span.getAttribute("error.type"));
        assertNotNull(span.getAttribute("error.message"));
    }

    @Test
    public void testCustomSpanPrefix() throws SQLException {
        String url = "jdbc:trace[spanPrefix=sql.]:jdbc:h2:mem:test_prefix;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("sql.execute", spans.get(0).getOperationName());
    }

    @Test
    public void testExcludeSql() throws SQLException {
        setupTestTable("test_nosql");
        String url = "jdbc:trace[includeSql=false]:jdbc:h2:mem:test_nosql;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertNull(spans.get(0).getAttribute("db.statement"));
    }

    @Test
    public void testExcludeRowCount() throws SQLException {
        setupTestTable("test_norowcount");
        String url = "jdbc:trace[includeRowCount=false]:jdbc:h2:mem:test_norowcount;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Test'");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertNull(spans.get(0).getAttribute("db.rows_affected"));
    }

    @Test
    public void testMultipleOperations() throws SQLException {
        setupTestTable("trace_test_multi");
        String url = "jdbc:trace:jdbc:h2:mem:trace_test_multi;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
                stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = 1");
                stmt.execute("SELECT 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(3, spans.size());
        assertEquals("db.query", spans.get(0).getOperationName());
        assertEquals("db.update", spans.get(1).getOperationName());
        assertEquals("db.execute", spans.get(2).getOperationName());
    }

    @Test
    public void testExecuteMethods() throws SQLException {
        setupTestTable("test_execute");
        String url = "jdbc:trace:jdbc:h2:mem:test_execute;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("db.execute", spans.get(0).getOperationName());
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.LogDriver");
        setupTestTable("trace_test_chain");
        String url = "jdbc:trace:jdbc:log:jdbc:h2:mem:trace_test_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                }
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("db.query", spans.get(0).getOperationName());
    }

    @Test
    public void testConfigDefaults() {
        TracingDriver.TracingConfig config = new TracingDriver.TracingConfig("jdbc:trace:jdbc:h2:mem:test");
        assertEquals("jdbc", config.getTracerName());
        assertEquals("db.", config.getSpanPrefix());
        assertTrue(config.isIncludeSql());
        assertFalse(config.isIncludeParams());
        assertTrue(config.isIncludeRowCount());
    }

    @Test
    public void testCustomTracer() throws SQLException {
        // Create and register a custom tracer
        final int[] spanCount = {0};
        TracingDriver.JdbcTracer customTracer = new TracingDriver.JdbcTracer() {
            @Override
            public TracingDriver.Span startSpan(String operationName) {
                spanCount[0]++;
                return new TracingDriver.Span() {
                    public TracingDriver.Span setAttribute(String key, String value) { return this; }
                    public TracingDriver.Span setAttribute(String key, long value) { return this; }
                    public TracingDriver.Span setError(Throwable t) { return this; }
                    public void end() {}
                };
            }
        };
        TracingDriver.setTracer("custom", customTracer);

        String url = "jdbc:trace[tracerName=custom]:jdbc:h2:mem:test_custom;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        assertEquals(1, spanCount[0]);
    }

    @Test
    public void testSpanTiming() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_timing;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());

        TracingDriver.SpanData span = spans.get(0);
        assertTrue(span.getStartTimeNanos() > 0);
        assertTrue(span.getEndTimeNanos() >= span.getStartTimeNanos());
        assertTrue(span.getDurationMs() >= 0);
    }

    @Test
    public void testInMemoryTracerClear() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_clear;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        TracingDriver.InMemoryTracer tracer = TracingDriver.getDefaultTracer();
        assertEquals(1, tracer.getSpanCount());

        tracer.clear();
        assertEquals(0, tracer.getSpanCount());
        assertTrue(tracer.getSpans().isEmpty());
    }

    @Test
    public void testDbSystemAttribute() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_system;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("jdbc", spans.get(0).getAttribute("db.system"));
    }

    @Test
    public void testPreparedStatementUpdate() throws SQLException {
        setupTestTable("test_pstmt_update");
        String url = "jdbc:trace:jdbc:h2:mem:test_pstmt_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
                pstmt.setString(1, "Charlie");
                pstmt.setInt(2, 1);
                int rows = pstmt.executeUpdate();
                assertEquals(1, rows);
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(1, spans.size());
        assertEquals("db.update", spans.get(0).getOperationName());
        assertEquals("1", spans.get(0).getAttribute("db.rows_affected"));
    }

    @Test
    public void testUpdateVariants() throws SQLException {
        setupTestTable("test_update_variants");
        String url = "jdbc:trace:jdbc:h2:mem:test_update_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Test different executeUpdate variants
                stmt.executeUpdate("UPDATE users SET name = 'V1' WHERE id = 1", Statement.NO_GENERATED_KEYS);
                stmt.executeUpdate("UPDATE users SET name = 'V2' WHERE id = 2", new int[]{1});
                stmt.executeUpdate("UPDATE users SET name = 'V3' WHERE id = 1", new String[]{"id"});
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(3, spans.size());
        for (TracingDriver.SpanData span : spans) {
            assertEquals("db.update", span.getOperationName());
        }
    }

    @Test
    public void testExecuteVariants() throws SQLException {
        String url = "jdbc:trace:jdbc:h2:mem:test_exec_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1", Statement.NO_GENERATED_KEYS);
                stmt.execute("SELECT 2", new int[]{1});
                stmt.execute("SELECT 3", new String[]{"col"});
            }
        }

        List<TracingDriver.SpanData> spans = TracingDriver.getDefaultTracer().getSpans();
        assertEquals(3, spans.size());
        for (TracingDriver.SpanData span : spans) {
            assertEquals("db.execute", span.getOperationName());
        }
    }
}
