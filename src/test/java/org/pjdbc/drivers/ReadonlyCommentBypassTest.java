package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyCommentBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked, but it's likely not because of the leading comment
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                // If we get here, it bypassed the check (or the table doesn't exist, but the check should happen BEFORE execution)
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked")) {
                // Blocked as expected
                return;
            }
            // Other SQL error (like table not found) is also acceptable as long as it's not a bypass,
            // but ReadonlyDriver should throw its own exception first.
            if (e.getMessage().contains("Table \"TEST_TABLE\" not found")) {
                 fail("Bypassed ReadonlyDriver check, failed at database level instead");
            }
            throw e;
        }
    }
}
