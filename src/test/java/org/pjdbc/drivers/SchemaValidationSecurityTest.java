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

    @Test
    public void testTableCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE secrets (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // Table name preceded by comment might bypass
                stmt.executeQuery("SELECT * FROM /* comment */ secrets");
                fail("Expected SQLException for access to blocked table 'secrets'");
            }
        } catch (SQLException e) {
            assertTrue("Expected SchemaValidationDriver blocked message, got: " + e.getMessage(),
                       e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("blocked"));
        }
    }
}
