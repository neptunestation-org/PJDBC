package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    private void setupTable(String dbName, String tableName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + tableName + " (id INT)");
            }
        }
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        setupTable("test_schema_bypass", "users");
        setupTable("test_schema_bypass", "secrets");

        // Whitelist 'users' table
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be allowed
                stmt.execute("SELECT * FROM users");

                // This should be blocked but might bypass if regex doesn't handle comments
                try {
                    stmt.execute("SELECT * FROM /* comment */ secrets");
                    fail("Should have blocked access to 'secrets' table");
                } catch (SQLException e) {
                    assertTrue("Expected message to contain 'is not in allowed tables list', but was: " + e.getMessage(),
                        e.getMessage().contains("is not in allowed tables list"));
                }
            }
        }
    }
}
