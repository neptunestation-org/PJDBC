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
                // This should be blocked but might be bypassed due to the leading comment
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                // If it doesn't throw, it's a bypass (assuming H2 doesn't fail on missing table yet)
                // Actually, H2 will fail because the table doesn't exist, but it should be blocked by ReadonlyDriver first.
            } catch (SQLException e) {
                if (e.getMessage().contains("ReadonlyDriver")) {
                    // Success: it was blocked
                    return;
                }
                // If it's another error (like table not found), it means it bypassed ReadonlyDriver
                fail("Bypassed ReadonlyDriver: " + e.getMessage());
            }
        }
    }
}
