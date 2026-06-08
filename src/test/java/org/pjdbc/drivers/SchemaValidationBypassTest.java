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
    public void testCommentBypass() throws SQLException {
        // Blacklist mode, blocking 'secrets' table
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                stmt.execute("SELECT * FROM /* comment */ secrets");
                fail("Bypassed SchemaValidationDriver: secrets table was not blocked due to comment");
            } catch (SQLException e) {
                if (e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("blocked")) {
                    // Success: it was blocked
                    return;
                }
                // If it's another error (like table not found), it might mean it bypassed the driver's check
                // but failed in the backend.
                fail("Bypassed SchemaValidationDriver: " + e.getMessage());
            }
        }
    }
}
