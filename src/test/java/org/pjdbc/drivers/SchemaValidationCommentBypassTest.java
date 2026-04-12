package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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

    private void assertBlocked(String url, String sql, String bypassType) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                fail("Bypassed SchemaValidationDriver check (" + bypassType + "), expected SQLException");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("blocked")) {
                // Blocked as expected
                return;
            }
            // If we get here, it means it was NOT blocked by our driver.
            // It might have failed at database level because table doesn't exist.
            // We want to FAIL the test if it wasn't blocked by our driver.
            fail("Bypassed SchemaValidationDriver check (" + bypassType + "), error: " + e.getMessage());
        }
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";

        assertBlocked(url, "/* comment */ SELECT * FROM secret_table", "leading comment");
        assertBlocked(url, "SELECT * FROM/*comment*/secret_table", "delimiter comment");
    }

    @Test
    public void testCommaSeparatedBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass_comma";

        assertBlocked(url, "SELECT * FROM public_table, secret_table", "comma separated tables");
    }

    @Test
    public void testJoinBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass_join";

        assertBlocked(url, "SELECT * FROM public_table JOIN secret_table ON 1=1", "join tables");
    }
}
