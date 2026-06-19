package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
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
    public void testCommentBypass() throws SQLException {
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM/*comment*/blocked_table");
                fail("SchemaValidationDriver bypassed by comment between FROM and table name");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'is not in allowed tables list' in message: " + e.getMessage(),
                       e.getMessage().contains("is not in allowed tables list"));
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_multiline_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT\n/*\ncomment\n*/\n*\nFROM\n/*\ncomment\n*/\nblocked_table");
                fail("SchemaValidationDriver bypassed by multi-line comments");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'is not in allowed tables list' in message: " + e.getMessage(),
                       e.getMessage().contains("is not in allowed tables list"));
        }
    }
}
