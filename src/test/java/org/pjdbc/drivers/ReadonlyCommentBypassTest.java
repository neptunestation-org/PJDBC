package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
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
                // This SHOULD be blocked, but if it bypasses the regex, it might succeed (or fail at DB level)
                // H2 allows comments before the statement.
                try {
                    stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                    // If we reach here, it might have bypassed the driver check.
                    // We need to check if it actually executed or if it failed at the DB level because the table doesn't exist.
                    // But if the driver doesn't throw a "ReadonlyDriver" exception, it's a bypass of the driver's security.
                } catch (SQLException e) {
                    if (e.getMessage().contains("ReadonlyDriver")) {
                        // Correctly blocked by driver
                        return;
                    }
                    // If it failed with "Table not found", it still bypassed the driver's check!
                    if (e.getMessage().contains("Table") && e.getMessage().contains("not found")) {
                         fail("Bypassed ReadonlyDriver check (failed at DB level instead)");
                    }
                    throw e;
                }
                fail("Bypassed ReadonlyDriver check (succeeded!)");
            }
        }
    }
}
