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
    public void testBlockCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_block_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Leading block comment
                stmt.executeUpdate("/* comment */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading block comment");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'ReadonlyDriver' and 'INSERT', got: " + e.getMessage(),
                       e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("INSERT"));
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Leading line comment
                stmt.executeUpdate("-- comment\nINSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'ReadonlyDriver' and 'INSERT', got: " + e.getMessage(),
                       e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("INSERT"));
        }
    }

    @Test
    public void testMixedCommentsBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_mixed_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Mixed whitespace and comments
                stmt.executeUpdate("  \n /* block */ \n -- line \n INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with mixed leading comments");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'ReadonlyDriver' and 'INSERT', got: " + e.getMessage(),
                       e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("INSERT"));
        }
    }

    @Test
    public void testMultiKeywordBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_keyword_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Truncate
                stmt.execute("/* comment */ TRUNCATE TABLE test");
                fail("Expected SQLException for TRUNCATE");
            }
        } catch (SQLException e) {
            assertTrue("Error message should contain 'TRUNCATE', got: " + e.getMessage(),
                       e.getMessage().contains("TRUNCATE"));
        }
    }
}
