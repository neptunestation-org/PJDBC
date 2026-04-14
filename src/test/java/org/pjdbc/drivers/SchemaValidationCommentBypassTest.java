package org.pjdbc.drivers;

import static org.junit.Assert.fail;
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
        // Mode: whitelist, allowedTables: users
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup table
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE users (id INT)");
                    stmt.execute("CREATE TABLE secrets (id INT)");
                }
            }

            // Normal check works
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT * FROM users");
            }

            // Normal block works
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT * FROM secrets");
                fail("Should have blocked access to 'secrets'");
            } catch (SQLException e) {
                // Expected
            }

            // Bypass check
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT * FROM/**/secrets");
                fail("Bypassed SchemaValidationDriver using comments!");
            } catch (SQLException e) {
                // Expected if fixed
            }
        }
    }
}
