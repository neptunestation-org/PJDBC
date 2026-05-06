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
    public void testTableBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:" + h2Url;

        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE forbidden_table (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might bypass if it uses a comment before the table name
                // The current TABLE_PATTERN is: \\b(?:FROM|JOIN|INTO|UPDATE|TABLE|TRUNCATE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)
                // So "FROM/*comment*/forbidden_table" might bypass if \\s+ doesn't match /*comment*/
                stmt.execute("SELECT * FROM/**/forbidden_table");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("forbidden_table")) {
                return; // Correctly blocked
            }
            throw e;
        }
        fail("Bypass successful: SELECT from forbidden_table with comment was not blocked");
    }
}
