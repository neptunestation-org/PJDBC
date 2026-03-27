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
                // This should be blocked but might bypass if comments aren't handled
                stmt.execute("/* comment */ INSERT INTO bypass_test VALUES (1)");
                // If it doesn't throw, we've bypassed the check
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                // Success - it was blocked
                return;
            }
            // If it's another error (like table not found), it might still have bypassed the PJDBC check
            // but failed in H2. However, ReadonlyDriver should check BEFORE passing to H2.
            throw e;
        }
    }
}
