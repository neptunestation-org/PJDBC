package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
    public void testCommentBypass() throws SQLException {
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This uses a comment instead of space, which bypassed the old regex
                stmt.execute("SELECT * FROM/**/secret_table");
                fail("Bypassed SchemaValidationDriver! Should have blocked 'secret_table'");
            } catch (SQLException e) {
                if (!e.getMessage().contains("SchemaValidationDriver")) {
                    fail("Bypassed SchemaValidationDriver! Got database error instead of driver block: " + e.getMessage());
                }
                assertTrue(e.getMessage().contains("secret_table"));
            }
        }
    }
}
