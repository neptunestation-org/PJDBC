package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.sql.WhereTransformer;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Normal case
        assertEquals("SELECT * FROM users WHERE tenant_id=1",
                     transformer.transformSql("SELECT * FROM users"));

        // Leading block comment bypass attempt
        assertEquals("/* comment */ SELECT * FROM users WHERE tenant_id=1",
                     transformer.transformSql("/* comment */ SELECT * FROM users"));

        // Leading line comment bypass attempt
        assertEquals("-- line\nSELECT * FROM users WHERE tenant_id=1",
                     transformer.transformSql("-- line\nSELECT * FROM users"));
    }

    @Test
    public void testSchemaValidationBypass() throws SQLException {
        // Whitelist 'users' table
        String url = "jdbc:schema[allowedTables=users]:jdbc:h2:mem:schema_test;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Should work
                try {
                    stmt.execute("CREATE TABLE users (id INT)");
                    stmt.execute("SELECT * FROM users");
                } catch (SQLException e) {
                    fail("Should have allowed access to 'users' table: " + e.getMessage());
                }

                // Should be blocked
                try {
                    stmt.execute("SELECT * FROM secrets");
                    fail("Should have blocked access to 'secrets' table");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("is not in allowed tables list"));
                }

                // Comment bypass attempt
                try {
                    stmt.execute("SELECT * FROM /* comment */ secrets");
                    fail("Should have blocked access to 'secrets' table with comment bypass");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("is not in allowed tables list"));
                }
            }
        }
    }
}
