package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
            // First create a table using a separate connection
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_comment_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            // Now try to insert through readonly driver using a comment prefix
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* some comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with comment prefix");
            }
        } catch (SQLException e) {
            // Success - it was blocked
            assertTrue("Expected message to contain ReadonlyDriver, but was: " + e.getMessage(),
                       e.getMessage().contains("ReadonlyDriver"));
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_line_comment_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- some comment\n INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with line comment prefix");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain ReadonlyDriver, but was: " + e.getMessage(),
                       e.getMessage().contains("ReadonlyDriver"));
        }
    }
}
