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
        // Block "secrets" table
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    // Try to bypass by putting a comment where whitespace is expected
                    stmt.execute("SELECT * FROM/**/secrets");
                    fail("Should have blocked access to 'secrets' table even with comment separator");
                } catch (SQLException e) {
                    assertTrue("Expected SchemaValidationDriver error message, got: " + e.getMessage(),
                        e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("blocked"));
                }
            }
        }
    }
}
