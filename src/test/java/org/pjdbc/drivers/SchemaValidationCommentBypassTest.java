package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
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
    public void testTableValidationWithComments() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_comments;DB_CLOSE_DELAY=-1";
        // Create the table first so H2 doesn't complain
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_comments;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE allowed_table (id INT)");
            }
        }
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be allowed
                stmt.execute("SELECT * FROM /* comment */ allowed_table");

                // This should be blocked
                try {
                    stmt.execute("SELECT * FROM /* comment */ secret_table");
                    fail("Should have blocked access to secret_table");
                } catch (SQLException e) {
                    assertTrue("Expected SchemaValidationDriver error, got: " + e.getMessage(),
                               e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("secret_table"));
                }
            }
        }
    }

    @Test
    public void testColumnValidationWithComments() throws SQLException {
        // Block 'secret_col'
        String url = "jdbc:schema[blockedColumns=secret_col]:jdbc:h2:mem:test_schema_cols";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                try {
                    stmt.execute("SELECT /* comment */ secret_col /* comment */ FROM /* comment */ some_table");
                    fail("Should have blocked access to secret_col");
                } catch (SQLException e) {
                    assertTrue("Expected SchemaValidationDriver error, got: " + e.getMessage(),
                               e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("secret_col"));
                }
            }
        }
    }
}
