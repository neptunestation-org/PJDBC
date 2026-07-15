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
                // This should be blocked but might bypass if regex only looks for leading whitespace
                stmt.executeUpdate("/* leading comment */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- leading line comment\nINSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }
}
