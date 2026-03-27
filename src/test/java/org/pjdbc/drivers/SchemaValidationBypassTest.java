package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked because 'secret_table' is not allowed
                // But it might bypass if we use a comment
                stmt.execute("SELECT * FROM/*comment*/secret_table");
                // If it doesn't throw a SchemaValidationDriver exception, it bypassed
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) {
                // Success - it was blocked
                return;
            }
            // If it's a "table not found" error from H2, it bypassed the driver's check
            if (e.getMessage().contains("Table \"SECRET_TABLE\" not found")) {
                fail("Bypassed SchemaValidationDriver! H2 caught the missing table instead.");
            }
            throw e;
        }
    }
}
