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

    @Test
    public void testLeadingCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* bypass */ INSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked' in message, but got: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testLeadingLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_2";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- bypass\nINSERT INTO test_table VALUES (1)");
                fail("Should have blocked INSERT with leading -- comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked' in message, but got: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_3";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* \n comment \n */ DELETE FROM test_table");
                fail("Should have blocked DELETE with multiline comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked: DELETE' in message, but got: " + e.getMessage(),
                e.getMessage().contains("DML blocked: DELETE"));
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_4";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH t AS (INSERT INTO test_table VALUES (1)) SELECT 1");
                fail("Should have blocked DML inside CTE");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked: INSERT' in message, but got: " + e.getMessage(),
                e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testNormalSelectNotBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_5";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ SELECT 1");
                // Should not throw exception
            }
        }
    }

    @Test
    public void testSelectWithBlockedKeywordInLiteral() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_6";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 'This is not an INSERT' FROM (SELECT 1)");
                // Should not throw exception
            }
        }
    }
}
