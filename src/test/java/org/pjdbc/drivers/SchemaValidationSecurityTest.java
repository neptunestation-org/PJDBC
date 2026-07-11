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
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked if it was 'SELECT * FROM secret_table'
                // But with comments it might bypass the regex
                stmt.execute("SELECT * FROM /* comment */ secret_table");
                fail("Expected SQLException for access to non-whitelisted table with comment");
            } catch (SQLException e) {
                assertTrue("Error message should contain 'SchemaValidationDriver', got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }
}
