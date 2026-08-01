package org.pjdbc.drivers;

import static org.junit.Assert.assertNotNull;
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
    public void testLeadingBlockCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment here */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading block comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testLeadingLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- comment here\nINSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCTEBasedDMLBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_dml";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH cte AS (SELECT 1) INSERT INTO test_table SELECT * FROM cte");
                fail("Expected SQLException for CTE-based DML write operation");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testBlockedKeywordInStringLiteralAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_literal_allowed";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This is a SELECT query but contains "INSERT" as a string literal. It must be allowed.
                stmt.execute("SELECT 'INSERT' AS val");
            }
        }
    }

    @Test
    public void testBlockedKeywordInDoubleQuotedIdentifierAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_identifier_allowed";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // "INSERT" is used as a column alias/identifier. It must be allowed.
                stmt.execute("SELECT 1 AS \"INSERT\"");
            }
        }
    }

    @Test
    public void testNestedCommentInLiteralAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_nested_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Comments inside single quotes shouldn't act as comments.
                stmt.execute("SELECT '/* comment */ INSERT' AS val");
            }
        }
    }
}
