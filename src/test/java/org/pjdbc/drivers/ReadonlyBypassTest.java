package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
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
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked, but if it bypasses the regex, it will attempt to execute.
                // H2 might fail if the table doesn't exist, but the point is if checkStatement throws or not.
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                // If it reached here, it bypassed the check (or table doesn't exist and it failed during execution, but checkStatement should have caught it first)
            } catch (SQLException e) {
                if (e.getMessage().contains("DML blocked")) {
                    return; // Correctly blocked
                }
                // If it's another error (like table not found), it still bypassed the security check
                fail("Bypassed security check, but failed later: " + e.getMessage());
            }
            fail("Expected SQLException for INSERT with leading comment");
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML
                stmt.execute("WITH deleted AS (DELETE FROM some_table RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for CTE with DELETE");
            } catch (SQLException e) {
                if (e.getMessage().contains("DML blocked")) {
                    return; // Correctly blocked
                }
                fail("Bypassed security check, but failed later: " + e.getMessage());
            }
        }
    }

    @Test
    public void testMixedCommentsBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_mixed_comments";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("  /* multi \n line */ -- single line \n INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with mixed comments");
            } catch (SQLException e) {
                if (e.getMessage().contains("DML blocked")) {
                    return; // Correctly blocked
                }
                fail("Bypassed security check, but failed later: " + e.getMessage());
            }
        }
    }

    @Test
    public void testCTEMultipleDML() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH t1 AS (SELECT 1), t2 AS (UPDATE test SET x=1) SELECT * FROM t1");
                fail("Expected SQLException for CTE with UPDATE");
            } catch (SQLException e) {
                if (e.getMessage().contains("DML blocked")) {
                    return; // Correctly blocked
                }
                fail("Bypassed security check, but failed later: " + e.getMessage());
            }
        }
    }
}
