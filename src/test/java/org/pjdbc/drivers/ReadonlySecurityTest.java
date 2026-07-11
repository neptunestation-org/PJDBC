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
                // This should be blocked, but if the regex only looks for ^\s* it might be bypassed by comments
                stmt.executeUpdate("/* leading comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Error message should contain 'DML blocked', got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This is a common bypass for simple regex-based readonly drivers
                stmt.execute("WITH deleted AS (DELETE FROM some_table RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for DML inside CTE");
            } catch (SQLException e) {
                assertTrue("Error message should contain 'blocked', got: " + e.getMessage(),
                    e.getMessage().contains("blocked"));
            }
        }
    }
}
