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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                try {
                    stmt.execute("INSERT INTO test VALUES (1)");
                    fail("Should have blocked INSERT");
                } catch (SQLException e) {
                    // Expected
                }

                // This might bypass if comments are not handled
                try {
                    stmt.execute("/* comment */ INSERT INTO test VALUES (2)");
                    // If we reach here, it bypassed!
                    fail("VULNERABILITY CONFIRMED: Leading comment bypassed ReadonlyDriver");
                } catch (SQLException e) {
                    // Expected if fixed
                }
            }
        }
    }
}
