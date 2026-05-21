package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked, but if the regex only looks at the start of the string for whitespace,
                // a comment might bypass it.
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testMultilineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass_multi")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* \n comment \n */ INSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading multiline comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
