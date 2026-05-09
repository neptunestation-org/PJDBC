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
                // This SHOULD be blocked but might not be due to the leading comment
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                // If it doesn't throw SQLException, it bypassed the check (or the table doesn't exist, but the check should happen first)
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked")) {
                // Success: it was blocked
                return;
            }
            // Other SQL exception (e.g. table not found) might mean it passed the proxy check
            fail("Expected ReadonlyDriver block, but got: " + e.getMessage());
        }
    }
}
