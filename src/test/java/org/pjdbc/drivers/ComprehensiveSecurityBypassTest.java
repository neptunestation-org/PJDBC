package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ComprehensiveSecurityBypassTest {
    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ CREATE TABLE bypass (id INT)");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) return;
            throw e;
        }
        fail("Bypassed ReadonlyDriver with leading comment!");
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Whitelist 'allowed_table', but try to access 'secret_table' using comments
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup secret table
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:schema_bypass")) {
                setup.createStatement().execute("CREATE TABLE secret_table (id INT)");
            }

            try (Statement stmt = conn.createStatement()) {
                // The regex \b(?:FROM|JOIN...)\s+table might fail if we use FROM/*comment*/table
                stmt.executeQuery("SELECT * FROM/*bypass*/secret_table");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) return;
            throw e;
        }
        fail("Bypassed SchemaValidationDriver with comment between keyword and table!");
    }
}
