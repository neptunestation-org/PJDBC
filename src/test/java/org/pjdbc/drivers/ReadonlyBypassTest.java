package org.pjdbc.drivers;

import static org.junit.Assert.fail;
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
                // This SHOULD be blocked but might not be due to the comment
                stmt.execute("/* comment */ CREATE TABLE bypass (id INT)");
                // If it reaches here, it bypassed the check (or H2 failed for other reasons)
                // We check if table was actually created to be sure, but the execute itself should have thrown.
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                // Success - it was blocked
                return;
            }
            // If it's another SQL error, it might still have bypassed ReadonlyDriver check
            throw e;
        }
        fail("Bypassed ReadonlyDriver with comment!");
    }
}
