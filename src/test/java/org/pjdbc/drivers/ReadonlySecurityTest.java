package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlySecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This might bypass because of leading comment
                stmt.execute("/* comment */ DELETE FROM some_table");
                fail("Expected SQLException for DELETE with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML might bypass
                stmt.execute("WITH deleted AS (DELETE FROM test_table RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for DELETE inside CTE");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }
}
