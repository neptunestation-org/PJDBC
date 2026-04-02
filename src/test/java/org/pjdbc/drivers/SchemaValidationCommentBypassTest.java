package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationCommentBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        String baseH2Url = "jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        // Setup tables using direct connection
        try (Connection conn = DriverManager.getConnection(baseH2Url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE allowed_table (id INT)");
                stmt.execute("CREATE TABLE secret_table (id INT)");
            }
        }

        // Whitelist mode: only allow allowed_table
        String schemaUrl = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:" + baseH2Url;
        try (Connection conn = DriverManager.getConnection(schemaUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Normal access to allowed table
                stmt.executeQuery("SELECT * FROM allowed_table");

                // Normal access to secret table should be blocked
                try {
                    stmt.executeQuery("SELECT * FROM secret_table");
                    fail("Should have blocked access to secret_table");
                } catch (SQLException e) {
                    // Expected
                }

                // Bypass attempt using comments
                try {
                    stmt.executeQuery("SELECT * FROM/* comment */secret_table");
                    fail("Should have blocked access to secret_table with comment separator");
                } catch (SQLException e) {
                    // Expected
                }

                try {
                    stmt.executeQuery("SELECT * FROM --\nsecret_table");
                    fail("Should have blocked access to secret_table with line comment separator");
                } catch (SQLException e) {
                    // Expected
                }
            }
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        String baseH2Url = "jdbc:h2:mem:test_column_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(baseH2Url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(10))");
            }
        }

        // Block ssn column
        String schemaUrl = "jdbc:schema[blockedColumns=ssn]:" + baseH2Url;
        try (Connection conn = DriverManager.getConnection(schemaUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Normal access to ssn should be blocked
                try {
                    stmt.executeQuery("SELECT ssn FROM users");
                    fail("Should have blocked access to ssn column");
                } catch (SQLException e) {
                    // Expected
                }

                // Bypass attempt using comments in SELECT
                try {
                    stmt.executeQuery("SELECT/* comment */ssn FROM users");
                    fail("Should have blocked access to ssn column with comment in SELECT");
                } catch (SQLException e) {
                    // Expected
                }
            }
        }
    }
}
