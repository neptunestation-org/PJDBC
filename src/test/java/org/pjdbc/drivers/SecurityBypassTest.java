package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FederatingDriver");
    }

    @Test
    public void testSchemaValidationTableBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Blocked table with comments between FROM and table name
                stmt.executeQuery("SELECT * FROM /* comment */ secrets");
                fail("Expected SQLException for blocked table with comments");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'SchemaValidationDriver' and 'secrets', got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("secrets"));
        }
    }

    @Test
    public void testSchemaValidationColumnBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=ssn,mode=blacklist]:jdbc:h2:mem:schema_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Blocked column with comments after SELECT
                stmt.executeQuery("SELECT /* comment */ ssn FROM users");
                fail("Expected SQLException for blocked column with comments");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'SchemaValidationDriver' and 'ssn', got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("ssn"));
        }
    }

    @Test
    public void testFederatingTableRoutingBypass() throws SQLException {
        // Table-based routing: users->db0, secrets->db1
        // We'll use two databases, one that has a table and one that doesn't
        String db1 = "jdbc:h2:mem:fed_bypass_1;DB_CLOSE_DELAY=-1";
        String db2 = "jdbc:h2:mem:fed_bypass_2;DB_CLOSE_DELAY=-1";

        // Setup db1 with 'users'
        try (Connection c = DriverManager.getConnection(db1)) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE users (id INT)");
            }
        }

        String url = "jdbc:federate[tableRouting=users:0;secrets:1]:" + db1 + ";" + db2;
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should route to db1 if it correctly identifies 'users' even with comments
                stmt.executeQuery("SELECT * FROM /* comment */ users");
            }
        } finally {
            // Clean up to avoid impacting other tests
            try (Connection c = DriverManager.getConnection(db1)) {
                try (Statement s = c.createStatement()) {
                    s.execute("DROP TABLE users");
                }
            }
        }
    }
}
