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
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_comment_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but might be bypassed if regex only checks for leading whitespace
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Bypassed ReadonlyDriver using a leading comment!");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver to block the statement, but got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
