package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might pass if regex requires whitespace after FROM
                stmt.execute("SELECT * FROM/*comment*/secret_table");
                fail("Expected SQLException for SELECT FROM secret_table with comment instead of whitespace");
            }
        } catch (SQLException e) {
            assertTrue("Expected SchemaValidationDriver in error message, got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver"));
        }
    }

    @Test
    public void testUpdateColumnCommentBypass() throws SQLException {
        // Blacklist 'secret_column'
        String url = "jdbc:schema[blockedColumns=secret_column,mode=blacklist]:jdbc:h2:mem:test_schema_update_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might pass if regex requires whitespace after SET
                stmt.execute("UPDATE allowed_table SET/*comment*/secret_column = 'leak'");
                fail("Expected SQLException for UPDATE SET secret_column with comment instead of whitespace");
            }
        } catch (SQLException e) {
            assertTrue("Expected SchemaValidationDriver in error message, got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver"));
        }
    }
}
