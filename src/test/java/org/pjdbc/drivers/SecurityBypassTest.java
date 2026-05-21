package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FederatingDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:readonly_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading comment in ReadonlyDriver");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Whitelist mode, only 'allowed_table' is allowed
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:schema_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS secret_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // Should be blocked because 'secret_table' is not in whitelist
                stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                fail("Should have blocked access to secret_table in SchemaValidationDriver");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }

    @Test
    public void testFederatingRoutingCommentBypass() throws SQLException {
        // Route 'secret_table' to db2 (index 1), everything else to db1 (index 0)
        // If bypass works, it might go to db1 or broadcast
        String url = "jdbc:federate[tableRouting=secret_table:1]:jdbc:h2:mem:fed1;jdbc:h2:mem:fed2";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection db1 = DriverManager.getConnection("jdbc:h2:mem:fed1")) {
                try (Statement stmt = db1.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS secret_table (id INT)");
                    stmt.execute("INSERT INTO secret_table VALUES (1)");
                }
            }
            try (Connection db2 = DriverManager.getConnection("jdbc:h2:mem:fed2")) {
                try (Statement stmt = db2.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS secret_table (id INT)");
                    stmt.execute("INSERT INTO secret_table VALUES (2)");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // If routing is bypassed, it might query both and return 2 rows (concat) or just db1
                // We expect it to be routed to db2 and return only value 2
                java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                int count = 0;
                while(rs.next()) {
                    count++;
                    if (rs.getInt(1) == 1) {
                        fail("Routing bypassed: reached db1 for secret_table");
                    }
                }
                // If it didn't fail above but count > 1, it also bypassed routing (broadcast)
                // Actually, FederatingDriver.extractFirstTable returns null if no match,
                // which leads to broadcast.
            }
        }
    }
}
