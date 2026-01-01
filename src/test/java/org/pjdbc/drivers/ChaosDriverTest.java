
package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
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
    public void testLatency() throws SQLException {
        String url = "jdbc:chaos:jdbc:h2:mem:test_latency?latency=100";
        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
        }
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 100);
    }

    @Test
    public void testFailure() throws SQLException {
        String url = "jdbc:chaos:jdbc:h2:mem:test_failure?failureRate=1.0";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Expected a SQLException to be thrown");
            }
        } catch (SQLException e) {
            assertEquals("ChaosDriver: Intentionally failing the query.", e.getMessage());
        }
    }
}
