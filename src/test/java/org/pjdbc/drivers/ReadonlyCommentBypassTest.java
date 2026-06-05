package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyCommentBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might bypass due to the comment
                stmt.execute("/* bypass */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup table
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might bypass because it starts with WITH
                stmt.execute("WITH t AS (INSERT INTO test VALUES (1) RETURNING id) SELECT * FROM t");
                fail("Expected SQLException for INSERT inside CTE");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testFalsePositivePrevention() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_fp";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Legitimate query with a blocked keyword in a string literal
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT 'DELETE' AS action")) {
                    assertTrue(rs.next());
                    org.junit.Assert.assertEquals("DELETE", rs.getString("action"));
                }
            }
        }
    }
}
