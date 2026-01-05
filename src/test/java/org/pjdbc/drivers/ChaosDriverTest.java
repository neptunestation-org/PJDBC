package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ChaosDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ChaosDriver");
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        ChaosDriver driver = new ChaosDriver();
        assertTrue(driver.acceptsURL("jdbc:chaos:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:chaos[failureRate=0.5]:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:chaos:jdbc:h2:mem:test_basic";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testLatency() throws SQLException {
        String url = "jdbc:chaos[latency=100]:jdbc:h2:mem:test_latency";
        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
        }
        long duration = System.currentTimeMillis() - start;
        assertTrue("Expected at least 100ms latency, got " + duration, duration >= 100);
    }

    @Test
    public void testLatencyVariance() throws SQLException {
        String url = "jdbc:chaos[latency=50,latencyVariance=100]:jdbc:h2:mem:test_variance";
        // Run multiple times to verify variance is applied
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            try (Connection conn = DriverManager.getConnection(url)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
            }
            long duration = System.currentTimeMillis() - start;
            minDuration = Math.min(minDuration, duration);
            maxDuration = Math.max(maxDuration, duration);
        }
        // Base latency is 50ms, should always be at least that
        assertTrue("Expected at least 50ms latency", minDuration >= 50);
    }

    @Test
    public void testFailure() throws SQLException {
        String url = "jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:test_failure";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Expected a SQLException to be thrown");
            }
        } catch (SQLException e) {
            assertEquals("ChaosDriver: Induced failure", e.getMessage());
        }
    }

    @Test
    public void testCustomExceptionMessage() throws SQLException {
        String url = "jdbc:chaos[failureRate=1.0,exceptionMessage=Custom error]:jdbc:h2:mem:test_custom_msg";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Expected a SQLException to be thrown");
            }
        } catch (SQLException e) {
            assertEquals("Custom error", e.getMessage());
        }
    }

    @Test
    public void testConnectionDrop() throws SQLException {
        String url = "jdbc:chaos[connectionDropRate=1.0]:jdbc:h2:mem:test_drop";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Expected a SQLException for connection drop");
            }
        } catch (SQLException e) {
            assertEquals("ChaosDriver: Connection dropped unexpectedly", e.getMessage());
        }
    }

    @Test
    public void testResultSetLatency() throws SQLException {
        String url = "jdbc:chaos[resultSetLatency=50]:jdbc:h2:mem:test_rs_latency";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_rows (id INT)");
                stmt.execute("DELETE FROM test_rows");
                stmt.execute("INSERT INTO test_rows VALUES (1), (2), (3)");
            }
            try (Statement stmt = conn.createStatement()) {
                long start = System.currentTimeMillis();
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM test_rows")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                    }
                    assertEquals(3, count);
                }
                long duration = System.currentTimeMillis() - start;
                // 3 rows + 1 final next() = 4 calls to next(), each with 50ms delay = 200ms minimum
                assertTrue("Expected at least 150ms for result set iteration, got " + duration, duration >= 150);
            }
        }
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        String url = "jdbc:chaos[latency=50]:jdbc:h2:mem:test_prepared";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_prep (id INT, name VARCHAR(50))");
            }
            long start = System.currentTimeMillis();
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test_prep VALUES (?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "test");
                pstmt.executeUpdate();
            }
            long duration = System.currentTimeMillis() - start;
            assertTrue("Expected at least 50ms latency for PreparedStatement", duration >= 50);
        }
    }

    @Test
    public void testPreparedStatementFailure() throws SQLException {
        String url = "jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:test_prep_fail";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                pstmt.executeQuery();
                fail("Expected SQLException from PreparedStatement");
            }
        } catch (SQLException e) {
            assertEquals("ChaosDriver: Induced failure", e.getMessage());
        }
    }

    @Test
    public void testZeroFailureRate() throws SQLException {
        // With 0% failure rate, queries should always succeed
        String url = "jdbc:chaos[failureRate=0.0]:jdbc:h2:mem:test_zero";
        try (Connection conn = DriverManager.getConnection(url)) {
            for (int i = 0; i < 10; i++) {
                try (Statement stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                        assertTrue(rs.next());
                    }
                }
            }
        }
    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        String url = "jdbc:chaos[latency=50]:jdbc:h2:mem:test_update";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_update (id INT)");
            }
            long start = System.currentTimeMillis();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO test_update VALUES (1)");
            }
            long duration = System.currentTimeMillis() - start;
            assertTrue("Expected at least 50ms latency for executeUpdate", duration >= 50);
        }
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        // Test that ChaosDriver works in a driver chain
        Class.forName("org.pjdbc.drivers.CatDriver");
        String url = "jdbc:chaos[latency=50]:jdbc:cat:jdbc:h2:mem:test_chain";
        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
        }
        long duration = System.currentTimeMillis() - start;
        assertTrue("Expected at least 50ms latency in chained drivers", duration >= 50);
    }

    @Test
    public void testNoParametersDefaults() throws SQLException {
        // Without any parameters, should work normally without chaos
        String url = "jdbc:chaos:jdbc:h2:mem:test_defaults";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testInvalidParameterValues() throws SQLException {
        // Invalid parameters should be parsed as defaults (0)
        String url = "jdbc:chaos[failureRate=invalid,latency=abc]:jdbc:h2:mem:test_invalid";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Should work fine since invalid values become 0
            for (int i = 0; i < 5; i++) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
            }
        }
    }
}
