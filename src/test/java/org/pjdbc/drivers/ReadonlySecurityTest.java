package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlySecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    private void assertBlocked(Connection conn, String sql, String type) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            fail("Bypassed ReadonlyDriver check for " + type + ": " + sql);
        } catch (SQLException e) {
            if (e.getMessage().contains(type + " blocked")) {
                // Correctly blocked
                return;
            }
            // If it's a different SQLException (e.g. table not found), then it bypassed the ReadonlyDriver check
            fail("Bypassed ReadonlyDriver check (incorrect error) for " + type + ": " + e.getMessage() + " SQL: " + sql);
        }
    }

    @Test
    public void testBlockCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertBlocked(conn, "/* bypass */ INSERT INTO test_table VALUES (1)", "DML");
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertBlocked(conn, "-- bypass \n INSERT INTO test_table VALUES (1)", "DML");
        }
    }

    @Test
    public void testNestedCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_nested_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertBlocked(conn, "/* block */ -- line \n /* another */ UPDATE test_table SET val = 1", "DML");
        }
    }

    @Test
    public void testCteDmlBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            // DML inside CTE
            assertBlocked(conn, "WITH cte AS (DELETE FROM test_table) SELECT 1", "DML");
            assertBlocked(conn, "WITH cte AS (INSERT INTO t1 SELECT * FROM t2) SELECT 1", "DML");
        }
    }

    @Test
    public void testLeadingWhitespaceAndComments() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_whitespace_comments";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertBlocked(conn, "  \n  /* comment */  \n  DROP TABLE test_table", "DDL");
        }
    }

    @Test
    public void testValidSelectWithKeywordsInStrings() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_valid_select";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // These should NOT be blocked
                stmt.execute("SELECT 'INSERT' FROM (SELECT 1)");
                stmt.execute("SELECT 1 -- This is an INSERT comment");
                stmt.execute("/* This is a DELETE block */ SELECT 1");
            }
        }
    }
}
