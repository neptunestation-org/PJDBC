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

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), ssn VARCHAR(11))");
            }
        }
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        setupTestTable("test_schema_bypass");
        // Whitelist only allows 'users', but 'secrets' is not in the list
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Normal block
                try {
                    stmt.execute("SELECT * FROM secrets");
                    fail("Should have blocked access to 'secrets' table");
                } catch (SQLException e) {
                    // Expected
                }

                // Comment bypass attempt
                try {
                    stmt.execute("SELECT * FROM/**/secrets");
                    fail("Should have blocked access to 'secrets' table with comment separator");
                } catch (SQLException e) {
                    // Expected if fixed
                }

                // Leading comment bypass attempt
                try {
                    stmt.execute("/* leading */ SELECT * FROM users");
                    // Should be fine, but let's see if it's correctly identified as a SELECT
                } catch (SQLException e) {
                    fail("Should have allowed SELECT with leading comment: " + e.getMessage());
                }
            }
        }
    }
}
