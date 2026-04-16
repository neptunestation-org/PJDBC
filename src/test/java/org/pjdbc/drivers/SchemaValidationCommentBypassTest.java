package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class SchemaValidationCommentBypassTest {
    @Test
    public void testTableCommentBypass() throws SQLException {
        // Only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table;mode=whitelist]:jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        Properties info = new Properties();
        info.setProperty("user", "sa");
        info.setProperty("password", "");

        // Setup: create tables
        String directUrl = "jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(directUrl, info)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE allowed_table (id INT)");
                stmt.execute("CREATE TABLE secret_table (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url, info)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be allowed
                stmt.executeQuery("SELECT * FROM allowed_table");

                // This should be blocked
                try {
                    stmt.executeQuery("SELECT * FROM secret_table");
                    Assert.fail("SELECT from secret_table should have been blocked");
                } catch (SQLException e) {
                    // Expected
                }

                // This might bypass if comments are not handled in TABLE_PATTERN
                try {
                    stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                    // If we reach here, it bypassed!
                } catch (SQLException e) {
                    // It was correctly blocked
                    if (e.getMessage().contains("SchemaValidationDriver")) {
                        return;
                    }
                    throw e;
                }
                Assert.fail("SELECT with comment bypassed SchemaValidationDriver!");
            }
        }
    }
}
