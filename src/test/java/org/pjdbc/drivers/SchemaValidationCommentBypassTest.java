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
    public void testTableCommentBypass() throws SQLException {
        // Whitelist mode, only 'allowed_table' is allowed
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS secret_table (id INT)");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                stmt.executeQuery("SELECT * FROM/*comment*/secret_table");
                fail("Expected SQLException for SELECT FROM secret_table with comment instead of space");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain SchemaValidationDriver, but was: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver"));
        }
    }
}
