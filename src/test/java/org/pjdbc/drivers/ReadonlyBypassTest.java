package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked by ReadonlyDriver
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Bypass succeeded: SQL comment allowed INSERT in readonly mode");
            }
        } catch (SQLException e) {
            // We expect the exception to come from ReadonlyDriver, not H2
            if (e.getMessage().contains("ReadonlyDriver")) {
                return; // Fixed
            }
            // If it's an H2 error (like "Table not found"), it means ReadonlyDriver let it through
            fail("Bypass succeeded: SQL reached database (Error: " + e.getMessage() + ")");
        }
    }

    @Test
    public void testCTEWithDML() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE-based DML bypass
                stmt.execute("WITH t AS (INSERT INTO test_table VALUES (1)) SELECT 1");
                fail("Bypass succeeded: CTE allowed INSERT in readonly mode");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                return;
            }
            fail("Bypass succeeded: CTE DML reached database (Error: " + e.getMessage() + ")");
        }
    }
}
