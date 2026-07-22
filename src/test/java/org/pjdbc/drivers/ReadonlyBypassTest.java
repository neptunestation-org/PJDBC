package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
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
    public void testLeadingBlockCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_comment;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* comment */ INSERT INTO users VALUES (1)");
                fail("Expected SQLException for INSERT with leading block comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("[DML blocked: INSERT]"));
            }
        }
    }

    @Test
    public void testLeadingLineCommentBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- comment\nINSERT INTO users VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("[DML blocked: INSERT]"));
            }
        }
    }

    @Test
    public void testCteDmlBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH cte AS (DELETE FROM users) SELECT 1");
                fail("Expected SQLException for CTE containing DML");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("[DML blocked: DELETE]"));
            }
        }
    }

    @Test
    public void testValidSelectWithStringLiteralAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_select_literal;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Should not throw, SELECT with blocked keywords in string literal is allowed
                stmt.execute("SELECT 'INSERT' FROM dual");
                stmt.execute("SELECT 'DELETE' AS action");
            }
        }
    }
}
