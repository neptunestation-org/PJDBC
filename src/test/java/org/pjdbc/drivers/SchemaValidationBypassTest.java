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
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Setup:
                try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
                    try (Statement setupStmt = setupConn.createStatement()) {
                        setupStmt.execute("CREATE TABLE secret_table (id INT)");
                    }
                }

                // The bypass attempt:
                // Use a comment instead of a space between FROM and table name
                try {
                    stmt.executeQuery("SELECT * FROM/**/secret_table");
                    fail("Bypassed SchemaValidationDriver check using comments!");
                } catch (SQLException e) {
                    // Expected
                    assertTrue(e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }
}
