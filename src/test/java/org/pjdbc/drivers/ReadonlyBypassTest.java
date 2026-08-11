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
                // This might bypass the current regex ^\s*
                stmt.executeUpdate("/* leading comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, got: " + e.getMessage(), e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup table
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML might bypass the current regex
                stmt.execute("WITH moved_rows AS (DELETE FROM test_table RETURNING *) SELECT * FROM moved_rows");
                fail("Expected SQLException for CTE with DELETE");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, got: " + e.getMessage(), e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testWithPrecedingInsertBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_with_preceding_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup table
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_with_preceding_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // WITH followed by INSERT might bypass the current anchored regex
                stmt.execute("WITH t AS (SELECT 1) INSERT INTO test_table SELECT * FROM t");
                fail("Expected SQLException for WITH preceding INSERT");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, got: " + e.getMessage(), e.getMessage().contains("DML blocked"));
            }
        }
    }
}
