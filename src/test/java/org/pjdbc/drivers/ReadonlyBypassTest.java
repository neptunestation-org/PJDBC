package org.pjdbc.drivers;

import static org.junit.Assert.*;

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
    public void testBlockCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* leading comment */ INSERT INTO some_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading block comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked: INSERT"));
            }
        }
    }

    @Test
    public void testLineCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- leading comment \n UPDATE some_table SET col = 1");
                fail("Expected SQLException for UPDATE with leading line comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked: UPDATE"));
            }
        }
    }

    @Test
    public void testCteBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH cte AS (DELETE FROM some_table) SELECT * FROM cte");
                fail("Expected SQLException for DELETE inside CTE");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked: DELETE"));
            }
        }
    }

    @Test
    public void testKeywordInLiteralAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_keyword_in_literal";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // INSERT is inside string literal, should be allowed
                try (var rs = stmt.executeQuery("SELECT 'this is an INSERT statement'")) {
                    assertTrue(rs.next());
                    assertEquals("this is an INSERT statement", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testKeywordInQuotedIdentifierAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_keyword_in_identifier";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // "INSERT" is a double-quoted identifier, should be allowed
                try (var rs = stmt.executeQuery("SELECT 1 AS \"INSERT\"")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testKeywordInBacktickIdentifierAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_keyword_in_backtick";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // `CREATE` is a backtick-quoted identifier, should be allowed
                try (var rs = stmt.executeQuery("SELECT 1 AS `CREATE`")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testKeywordInCommentAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_keyword_in_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // DELETE inside a comment should be ignored and statement allowed
                try (var rs = stmt.executeQuery("SELECT 1 /* DELETE FROM some_table */")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testMixedCommentsAndCteBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_mixed_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* some leading comment */ WITH x AS (CREATE TABLE y (id int)) SELECT 1");
                fail("Expected SQLException for CREATE inside CTE with leading block comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DDL blocked: CREATE"));
            }
        }
    }
}
