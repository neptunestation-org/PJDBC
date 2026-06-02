package org.pjdbc.drivers;

import static org.junit.Assert.fail;
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
                // This should be blocked, but if it bypasses, it will try to execute on H2
                // We use a comment to try to bypass the ^\s* regex
                stmt.executeUpdate("/* bypass */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                // Expected if it didn't bypass
                if (!e.getMessage().contains("ReadonlyDriver")) {
                    // If it's an H2 error (e.g. Table not found), it means it bypassed the ReadonlyDriver check
                    fail("Bypassed ReadonlyDriver! H2 Error: " + e.getMessage());
                }
            }
        }
    }
}
