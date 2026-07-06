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
    public void testBlockCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_block";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but might not be due to the comment
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with block comment prefix");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_line";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but might not be due to the comment
                stmt.execute("-- line comment\nINSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with line comment prefix");
            }
        } catch (SQLException e) {
            assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }
}
