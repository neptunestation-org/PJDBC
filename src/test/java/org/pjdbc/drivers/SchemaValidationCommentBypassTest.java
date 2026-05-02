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
    public void testCommentBypass() throws SQLException {
        // Block "secret_table"
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might bypass if regex expects whitespace after FROM
                stmt.execute("SELECT * FROM/*comment*/secret_table");
                fail("Expected SQLException for SELECT FROM secret_table with comment separator");
            }
        } catch (SQLException e) {
            assertTrue("Expected SchemaValidationDriver in error message, got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver"));
        }
    }
}
