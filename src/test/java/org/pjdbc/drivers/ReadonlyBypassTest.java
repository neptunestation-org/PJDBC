package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but currently might not be
                stmt.execute("/* comment */ DELETE FROM some_table");
                fail("Expected SQLException for DELETE with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message", e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but currently is not because it starts with WITH
                stmt.execute("WITH t AS (DELETE FROM some_table RETURNING *) SELECT * FROM t");
                fail("Expected SQLException for DELETE inside CTE");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message", e.getMessage().contains("DML blocked"));
        }
    }
}
