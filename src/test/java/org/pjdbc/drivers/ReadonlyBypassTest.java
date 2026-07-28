package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testBlockCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_comment;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* some comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading block comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testLineCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- some line comment\nINSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCteDmlBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH deleted AS (DELETE FROM users) SELECT * FROM deleted");
                fail("Expected SQLException for CTE containing DML");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testValidSelectWithKeywordsInLiteralAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_literal_allowed;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 'INSERT INTO some_table'")) {
                    assertTrue(rs.next());
                    assertEquals("INSERT INTO some_table", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testValidSelectWithKeywordsInIdentifierAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_identifier_allowed;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1 AS \"insert\"")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }
}
