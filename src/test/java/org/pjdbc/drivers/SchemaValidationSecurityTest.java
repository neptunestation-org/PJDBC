package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:test_schema_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM /* comment */ secrets");
                fail("Expected SQLException for blocked table with comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message, but got: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=ssn]:jdbc:h2:mem:test_schema_col_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT /* comment */ ssn FROM users");
                fail("Expected SQLException for blocked column with comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message, but got: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }

    @Test
    public void testInsertColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=ssn]:jdbc:h2:mem:test_schema_insert_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO users /* comment */ (ssn) VALUES ('123')");
                fail("Expected SQLException for blocked column in INSERT with comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message, but got: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }
}
