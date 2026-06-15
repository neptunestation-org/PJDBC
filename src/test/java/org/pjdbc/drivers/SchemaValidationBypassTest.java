package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableBypass() throws SQLException {
        // Whitelist mode, only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE secret_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                stmt.execute("SELECT * FROM/*comment*/secret_table");
                fail("Expected SQLException for access to secret_table via comment bypass");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) {
                return;
            }
            throw e;
        }
    }

    @Test
    public void testColumnBypass() throws SQLException {
        // Block 'secret_col'
        String url = "jdbc:schema[blockedColumns=secret_col]:jdbc:h2:mem:test_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_col_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE users (secret_col INT, public_col INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                stmt.execute("SELECT/*comment*/secret_col FROM users");
                fail("Expected SQLException for access to secret_col via comment bypass");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) {
                return;
            }
            throw e;
        }
    }
}
