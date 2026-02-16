package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyDriverSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked, but if it bypasses, it will try to execute it on H2.
                // H2 might fail because the table doesn't exist, but we want to see if ReadonlyDriver blocks it.
                try {
                    stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                    fail("Expected SQLException for INSERT with leading comment");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        fail("Expected ReadonlyDriver to block the statement, but it might have been passed to H2: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.executeUpdate("-- line comment\nINSERT INTO test_table VALUES (1)");
                    fail("Expected SQLException for INSERT with leading line comment");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        fail("Expected ReadonlyDriver to block the statement: " + e.getMessage());
                    }
                }
            }
        }
    }
}
