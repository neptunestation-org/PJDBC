package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationDriverBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM /* comment */ sensitive_table");
                fail("Expected SQLException for unauthorized table access with comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("SchemaValidationDriver") || !e.getMessage().contains("not in allowed tables list")) {
                fail("Expected validation error, but got: " + e.getMessage());
            }
        }
    }
}
