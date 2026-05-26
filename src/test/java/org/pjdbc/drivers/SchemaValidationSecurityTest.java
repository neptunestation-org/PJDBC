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
        // Whitelist 'users', block 'secrets'
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:schema_sec";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Blocked
                try {
                    stmt.executeQuery("SELECT * FROM secrets");
                    fail("Should have blocked secrets table");
                } catch (SQLException e) {}

                // Should be blocked even with comments
                try {
                    stmt.executeQuery("SELECT * FROM /* comment */ secrets");
                    fail("Should have blocked secrets table with comment");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=password]:jdbc:h2:mem:schema_sec_col";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Blocked
                try {
                    stmt.executeQuery("SELECT password FROM users");
                    fail("Should have blocked password column");
                } catch (SQLException e) {}

                // Should be blocked even with comments
                try {
                    stmt.executeQuery("SELECT /* comment */ password FROM users");
                    fail("Should have blocked password column with comment");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }

    @Test
    public void testInsertNoSpaceBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=password]:jdbc:h2:mem:schema_sec_insert";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Should be blocked even if no space before parenthesis
                try {
                    stmt.execute("INSERT INTO users(password) VALUES ('secret')");
                    fail("Should have blocked password column in INSERT without space");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }

    @Test
    public void testMultiLineColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=password]:jdbc:h2:mem:schema_sec_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Should be blocked even with multi-line comment
                try {
                    stmt.executeQuery("SELECT /* \n comment \n */ password FROM users");
                    fail("Should have blocked password column with multi-line comment");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }
}
