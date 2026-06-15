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
            // Setup table
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_comment_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but currently might not be if it starts with a comment
                stmt.execute("/* comment */ DELETE FROM test");
                fail("Expected SQLException for DELETE with leading comment");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                // Success - it was blocked
                return;
            }
            throw e;
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup tables
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test1 (id INT)");
                    stmt.execute("CREATE TABLE test2 (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML (PostgreSQL/Oracle/SQL Server style, H2 doesn't support DELETE in CTE easily but we check if it bypasses our check)
                // Actually H2 doesn't support DML in CTE.
                // We can use a query that H2 would reject but our driver SHOULD block first.
                stmt.execute("WITH x AS (DELETE FROM test1) SELECT 1 FROM test2");
                fail("Expected SQLException for CTE with DELETE");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                // Success - it was blocked
                return;
            }
            // If it reached H2, it's a bypass of our driver
            fail("Bypass detected: Statement reached the database and failed there instead of being blocked by ReadonlyDriver: " + e.getMessage());
        }
    }
}
