package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MetricsDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.MetricsDriver");
    }

    @Before
    public void resetMetrics() {
        MetricsDriver.getGlobalMetrics().reset();
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
            }
        }
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        MetricsDriver driver = new MetricsDriver();
        assertTrue(driver.acceptsURL("jdbc:metrics:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:metrics[slowThreshold=500]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testQueryMetrics() throws SQLException {
        setupTestTable("metrics_test_query");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
                stmt.executeQuery("SELECT * FROM users WHERE id = 2");
            }

            assertEquals(2, metrics.getTotalQueries());
            assertEquals(0, metrics.getTotalUpdates());
            assertEquals(2, metrics.getTotalOperations());
            assertTrue(metrics.getAvgTimeMs() >= 0);
        }
    }

    @Test
    public void testUpdateMetrics() throws SQLException {
        setupTestTable("metrics_test_update");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                int rows = stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
                assertEquals(1, rows);
            }

            assertEquals(0, metrics.getTotalQueries());
            assertEquals(1, metrics.getTotalUpdates());
            assertEquals(1, metrics.getTotalRowsAffected());
        }
    }

    @Test
    public void testErrorMetrics() throws SQLException {
        setupTestTable("metrics_test_error");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_error;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                // First do a successful query
                stmt.executeQuery("SELECT * FROM users");
                // Then a failing one
                stmt.executeQuery("SELECT * FROM nonexistent_table");
            } catch (SQLException expected) {
                // Expected
            }

            assertEquals(1, metrics.getTotalErrors());
            assertEquals(1, metrics.getTotalQueries()); // Only successful one counted
            assertTrue(metrics.getErrorRate() > 0);
        }
    }

    @Test
    public void testPreparedStatementMetrics() throws SQLException {
        setupTestTable("metrics_test_pstmt");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                pstmt.executeQuery();
            }

            assertEquals(1, metrics.getTotalQueries());
        }
    }

    @Test
    public void testGlobalMetrics() throws SQLException {
        setupTestTable("metrics_test_global");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_global;DB_CLOSE_DELAY=-1";

        // Reset and verify
        MetricsDriver.getGlobalMetrics().reset();
        assertEquals(0, MetricsDriver.getGlobalMetrics().getTotalQueries());

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
        }

        assertEquals(1, MetricsDriver.getGlobalMetrics().getTotalQueries());
    }

    @Test
    public void testConnectionMetrics() throws SQLException {
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_conn;DB_CLOSE_DELAY=-1";

        long initialActive = MetricsDriver.getGlobalMetrics().getActiveConnections();
        long initialTotal = MetricsDriver.getGlobalMetrics().getTotalConnections();

        Connection conn1 = DriverManager.getConnection(url);
        assertEquals(initialActive + 1, MetricsDriver.getGlobalMetrics().getActiveConnections());
        assertEquals(initialTotal + 1, MetricsDriver.getGlobalMetrics().getTotalConnections());

        Connection conn2 = DriverManager.getConnection(url);
        assertEquals(initialActive + 2, MetricsDriver.getGlobalMetrics().getActiveConnections());
        assertEquals(initialTotal + 2, MetricsDriver.getGlobalMetrics().getTotalConnections());

        conn1.close();
        assertEquals(initialActive + 1, MetricsDriver.getGlobalMetrics().getActiveConnections());

        conn2.close();
        assertEquals(initialActive, MetricsDriver.getGlobalMetrics().getActiveConnections());
        assertEquals(initialTotal + 2, MetricsDriver.getGlobalMetrics().getTotalConnections());
    }

    @Test
    public void testOperationTypeMetrics() throws SQLException {
        setupTestTable("metrics_test_types");
        String url = "jdbc:metrics[trackByType=true]:jdbc:h2:mem:metrics_test_types;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie')");
                stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = 1");
                stmt.executeUpdate("DELETE FROM users WHERE id = 3");
            }

            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.SELECT).getCount());
            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.INSERT).getCount());
            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.UPDATE).getCount());
            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.DELETE).getCount());
        }
    }

    @Test
    public void testTimingMetrics() throws SQLException {
        setupTestTable("metrics_test_timing");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_timing;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }

            assertTrue(metrics.getTotalTimeMs() >= 0);
            assertTrue(metrics.getMinTimeMs() >= 0);
            assertTrue(metrics.getMaxTimeMs() >= metrics.getMinTimeMs());
            assertTrue(metrics.getAvgTimeMs() >= 0);
        }
    }

    @Test
    public void testMetricsReset() throws SQLException {
        setupTestTable("metrics_test_reset");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_reset;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, metrics.getTotalQueries());

            metrics.reset();
            assertEquals(0, metrics.getTotalQueries());
            assertEquals(0, metrics.getTotalOperations());
        }
    }

    @Test
    public void testDisabledMetrics() throws SQLException {
        setupTestTable("metrics_test_disabled");
        String url = "jdbc:metrics[enabled=false]:jdbc:h2:mem:metrics_test_disabled;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }

            assertEquals(0, metrics.getTotalQueries());
        }
    }

    @Test
    public void testSlowQueryThreshold() throws SQLException, InterruptedException {
        setupTestTable("metrics_test_slow");
        // Very low threshold to catch any query as slow
        String url = "jdbc:metrics[slowThreshold=0]:jdbc:h2:mem:metrics_test_slow;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }

            assertTrue(metrics.getSlowQueries() >= 1);
            assertTrue(metrics.getSlowQueryRate() > 0);
        }
    }

    @Test
    public void testExecuteMetrics() throws SQLException {
        setupTestTable("metrics_test_execute");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_execute;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }

            assertEquals(1, metrics.getTotalExecutes());
        }
    }

    @Test
    public void testConfigDefaults() {
        MetricsDriver.MetricsConfig config = new MetricsDriver.MetricsConfig("jdbc:metrics:jdbc:h2:mem:test");
        assertEquals(1000, config.getSlowThresholdMs());
        assertTrue(config.isEnabled());
        assertTrue(config.isTrackByType());
    }

    @Test
    public void testCustomConfig() {
        MetricsDriver.MetricsConfig config = new MetricsDriver.MetricsConfig(
            "jdbc:metrics[slowThreshold=500,enabled=true,trackByType=false]:jdbc:h2:mem:test"
        );
        assertEquals(500, config.getSlowThresholdMs());
        assertTrue(config.isEnabled());
        assertFalse(config.isTrackByType());
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.LogDriver");
        setupTestTable("metrics_test_chain");
        String url = "jdbc:metrics:jdbc:log:jdbc:h2:mem:metrics_test_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
            }

            assertEquals(1, metrics.getTotalQueries());
        }
    }

    @Test
    public void testMetricsToString() throws SQLException {
        setupTestTable("metrics_test_tostring");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_tostring;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }

            String str = metrics.toString();
            assertTrue(str.contains("ops="));
            assertTrue(str.contains("queries="));
        }
    }

    @Test
    public void testTypeMetricsRowsAffected() throws SQLException {
        setupTestTable("metrics_test_rows");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_rows;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Test'");
            }

            MetricsDriver.TypeMetrics updateMetrics = metrics.getTypeMetrics(MetricsDriver.OperationType.UPDATE);
            assertEquals(2, updateMetrics.getRowsAffected());
        }
    }

    @Test
    public void testPreparedStatementUpdate() throws SQLException {
        setupTestTable("metrics_test_pstmt_update");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_pstmt_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
                pstmt.setString(1, "Updated");
                pstmt.setInt(2, 1);
                int rows = pstmt.executeUpdate();
                assertEquals(1, rows);
            }

            assertEquals(1, metrics.getTotalUpdates());
            assertEquals(1, metrics.getTotalRowsAffected());
        }
    }

    @Test
    public void testMultipleUpdateVariants() throws SQLException {
        setupTestTable("metrics_test_update_variants");
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_update_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'V1' WHERE id = 1", Statement.NO_GENERATED_KEYS);
                stmt.executeUpdate("UPDATE users SET name = 'V2' WHERE id = 2", new int[]{1});
                stmt.executeUpdate("UPDATE users SET name = 'V3' WHERE id = 1", new String[]{"id"});
            }

            assertEquals(3, metrics.getTotalUpdates());
        }
    }

    @Test
    public void testExecuteVariants() throws SQLException {
        String url = "jdbc:metrics:jdbc:h2:mem:metrics_test_exec_variants;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1", Statement.NO_GENERATED_KEYS);
                stmt.execute("SELECT 2", new int[]{1});
                stmt.execute("SELECT 3", new String[]{"col"});
            }

            assertEquals(3, metrics.getTotalExecutes());
        }
    }
}
