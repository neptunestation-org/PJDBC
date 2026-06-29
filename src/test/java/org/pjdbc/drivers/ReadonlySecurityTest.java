package org.pjdbc.drivers;

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

    @Test
    public void testLeadingCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading block comment");
            } catch (SQLException e) {
                // Expected
            }
        }
    }

    @Test
    public void testLeadingLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_line_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- comment\nINSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading line comment");
            } catch (SQLException e) {
                // Expected
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH t AS (DELETE FROM test_table) SELECT 1");
                fail("Should have blocked DELETE nested in CTE");
            } catch (SQLException e) {
                // Expected
            }
        }
    }

    @Test
    public void testCteCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_cte_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH t AS ( /* comment */ UPDATE test_table SET x=1) SELECT 1");
                fail("Should have blocked UPDATE nested in CTE with comment");
            } catch (SQLException e) {
                // Expected
            }
        }
    }
}
