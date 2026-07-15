package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked because 'secret_table' is not allowed.
                // But if the regex fails to match because of the comment, it might pass.
                stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                fail("Expected SQLException for unauthorized table with comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected unauthorized table message, but got: " + e.getMessage(),
                       e.getMessage().contains("is not in allowed tables list") || e.getMessage().contains("is blocked"));
        }
    }
}
