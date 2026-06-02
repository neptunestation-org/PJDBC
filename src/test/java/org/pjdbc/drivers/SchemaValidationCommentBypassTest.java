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
    public void testTableBypass() throws SQLException {
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked because 'secret_table' is not in whitelist.
                // We use a comment to try to bypass the \s+ in TABLE_PATTERN
                stmt.executeQuery("SELECT * FROM/*bypass*/secret_table");
                fail("Expected SQLException for SELECT from secret_table");
            } catch (SQLException e) {
                if (!e.getMessage().contains("SchemaValidationDriver")) {
                    fail("Bypassed SchemaValidationDriver! H2 Error: " + e.getMessage());
                }
            }
        }
    }
}
