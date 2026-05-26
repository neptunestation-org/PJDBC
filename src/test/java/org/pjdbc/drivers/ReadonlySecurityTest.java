package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but might not be due to leading comment
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testMultilineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/*\n multiline comment \n*/ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading multiline comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_line";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- comment\n INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }
}
