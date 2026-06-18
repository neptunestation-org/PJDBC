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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* leading comment */ INSERT INTO bypass_test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_multiline";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* leading \n multi-line \n comment */ UPDATE bypass_test SET id = 1");
                fail("Expected SQLException for UPDATE with multi-line comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_line";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- leading line comment\n DELETE FROM bypass_test");
                fail("Expected SQLException for DELETE with line comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testNestedCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_nested";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* outer /* inner */ outer */ INSERT INTO bypass_test VALUES (1)");
                fail("Expected SQLException for INSERT with nested comment");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Testing with a query that would be syntactically valid in some DBs or at least valid enough for the regex
                stmt.execute("WITH cte AS (INSERT INTO bypass_test VALUES (1)) SELECT * FROM cte");
                fail("Expected SQLException for CTE with INSERT");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked in CTE message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked in CTE"));
            }
        }
    }

    @Test
    public void testCTEWithCommentsBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_cte_comments";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH cte AS /* comment */ ( /* comment */ UPDATE bypass_test SET id = 1) SELECT * FROM cte");
                fail("Expected SQLException for CTE with UPDATE and comments");
            } catch (SQLException e) {
                assertTrue("Expected DML blocked in CTE message, but got: " + e.getMessage(),
                    e.getMessage().contains("DML blocked in CTE"));
            }
        }
    }
}
