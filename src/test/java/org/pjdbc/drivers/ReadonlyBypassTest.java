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
                // This should be blocked, but if it bypasses the regex, it might try to execute
                // and fail because the table doesn't exist, OR it might actually execute if we setup the table.

                // Setup:
                try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass")) {
                    try (Statement setupStmt = setupConn.createStatement()) {
                        setupStmt.execute("CREATE TABLE bypass_test (id INT)");
                    }
                }

                // The bypass attempt:
                try {
                    stmt.execute("/* bypass */ INSERT INTO bypass_test VALUES (1)");
                    // If it gets here, it bypassed the check!
                } catch (SQLException e) {
                    // Expected
                    assertTrue(e.getMessage().contains("ReadonlyDriver"));
                    return;
                }
                // We should check if it actually inserted data to be sure.
                try (Connection verifyConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass")) {
                    try (Statement verifyStmt = verifyConn.createStatement()) {
                        try (java.sql.ResultSet rs = verifyStmt.executeQuery("SELECT COUNT(*) FROM bypass_test")) {
                            assertTrue(rs.next());
                            if (rs.getInt(1) > 0) {
                                fail("Bypassed ReadonlyDriver check using comments!");
                            }
                        }
                    }
                }
            }
        }
    }
}
