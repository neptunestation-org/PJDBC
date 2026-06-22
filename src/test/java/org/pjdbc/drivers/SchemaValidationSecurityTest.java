package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
        // Whitelist mode: only 'public_table' allowed
        String url = "jdbc:schema[allowedTables=public_table]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked but currently is NOT because of the comment
                stmt.execute("SELECT * FROM /* comment */ secret_table");
                fail("Bypass succeeded: SQL comment allowed access to unauthorized table");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) {
                return; // Fixed
            }
            fail("Bypass succeeded: SQL reached database (Error: " + e.getMessage() + ")");
        }
    }
}
