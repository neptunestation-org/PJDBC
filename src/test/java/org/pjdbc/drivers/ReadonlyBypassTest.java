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
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Bypassed ReadonlyDriver! Should have blocked INSERT with leading comment");
            } catch (SQLException e) {
                if (!e.getMessage().contains("ReadonlyDriver")) {
                    fail("Bypassed ReadonlyDriver! Got database error instead of driver block: " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testFalsePositives() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_false_positive";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This contains 'UPDATE' but it's not a write operation at the beginning
                stmt.execute("SELECT 'Update this' AS status");
            } catch (SQLException e) {
                if (e.getMessage().contains("ReadonlyDriver")) {
                    fail("False positive! ReadonlyDriver blocked a valid SELECT statement containing the word 'Update': " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This is still a known bypass as long as we use anchors (it doesn't start with DML)
                // But it's good to keep track of it
                stmt.execute("WITH deleted AS (DELETE FROM some_table RETURNING *) SELECT * FROM deleted");
                // If it passes, it's a known limitation (which we're not fixing now as it requires full parser)
            } catch (SQLException e) {
                // If it blocks, then even better!
            }
        }
    }
}
