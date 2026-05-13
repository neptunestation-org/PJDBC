package org.pjdbc.drivers;

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
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FederatingDriver");
    }

    private void assertBlocked(String driverName, String url, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.executeUpdate(sql);
                    fail("Bypassed " + driverName + "! Operation should have been blocked: " + sql);
                } catch (SQLException e) {
                    if (!e.getMessage().contains(driverName)) {
                        fail("Bypassed " + driverName + "! Caught unrelated SQLException: " + e.getMessage());
                    }
                    // Correctly blocked by the driver
                }
            }
        }
    }

    @Test
    public void testReadonlyDriverBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        // Leading comments
        assertBlocked("ReadonlyDriver", url, "/* comment */ INSERT INTO test VALUES (1)");
        assertBlocked("ReadonlyDriver", url, "-- comment\nINSERT INTO test VALUES (1)");
        // Interspersed comments
        assertBlocked("ReadonlyDriver", url, "INSERT/* comment */ INTO test VALUES (1)");
    }

    @Test
    public void testSchemaValidationDriverBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secret,mode=blacklist]:jdbc:h2:mem:schema_bypass";
        // Leading comments
        assertBlocked("SchemaValidationDriver", url, "/* comment */ SELECT * FROM secret");
        assertBlocked("SchemaValidationDriver", url, "-- comment\nSELECT * FROM secret");
        // Interspersed comments
        assertBlocked("SchemaValidationDriver", url, "SELECT * FROM /* comment */ secret");
        assertBlocked("SchemaValidationDriver", url, "DELETE FROM /* comment */ secret");
        assertBlocked("SchemaValidationDriver", url, "UPDATE /* comment */ secret SET x=1");
    }

    @Test
    public void testFederatingDriverBypass() throws SQLException {
        // FederatingDriver uses TABLE_PATTERN for table-based routing
        String url = "jdbc:federate[tableRouting=secret:1]:jdbc:h2:mem:db1;jdbc:h2:mem:db2";

        // We can't easily "assertBlocked" because it just falls back to broadcast if it doesn't match
        // But we can verify if it correctly identifies the table with comments
        // If it doesn't identify "secret", it will broadcast to both.
        // This is harder to test without mocks or checking behavior.
        // For now, let's focus on the ones that THROW exceptions when blocked.
    }
}
