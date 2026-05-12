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
    }

    @Test
    public void testReadonlyDriverLeadingCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Leading comment might bypass ^\\s*
                String sql = "/* comment */ INSERT INTO test VALUES (1)";
                try {
                    stmt.executeUpdate(sql);
                    fail("ReadonlyDriver: Bypass successful - INSERT allowed with leading comment");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                         fail("ReadonlyDriver: Bypass successful - reached database: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void testSchemaValidationDriverCommentSeparatorBypass() throws SQLException {
        // Whitelist 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Comment instead of space between FROM and table name
                String sql = "SELECT * FROM/**/blocked_table";
                try {
                    stmt.executeQuery(sql);
                    fail("SchemaValidationDriver: Bypass successful - SELECT from blocked_table allowed with comment separator");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        fail("SchemaValidationDriver: Bypass successful - reached database: " + e.getMessage());
                    }
                }
            }
        }
    }
}
