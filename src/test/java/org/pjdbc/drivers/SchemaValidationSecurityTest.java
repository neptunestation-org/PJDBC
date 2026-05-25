package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationSecurityTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked because 'secret_table' is not in whitelist
                stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                fail("Expected SQLException for table not in whitelist with comment");
            } catch (SQLException e) {
                assertTrue("Expected table not allowed message, but got: " + e.getMessage(),
                    e.getMessage().contains("is not in allowed tables list"));
            }
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        // Block 'secret_column'
        String url = "jdbc:schema[blockedColumns=secret_column]:jdbc:h2:mem:test_schema_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked because 'secret_column' is blocked
                stmt.executeQuery("SELECT /* comment */ secret_column FROM allowed_table");
                fail("Expected SQLException for blocked column with comment");
            } catch (SQLException e) {
                assertTrue("Expected column blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("is blocked"));
            }
        }
    }

    @Test
    public void testInsertCommentBypass() throws SQLException {
        // Block 'secret_column'
        String url = "jdbc:schema[blockedColumns=secret_column]:jdbc:h2:mem:test_schema_ins_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                stmt.execute("INSERT /* comment */ INTO /* comment */ test (/* comment */ secret_column) VALUES (1)");
                fail("Expected SQLException for blocked column in INSERT with comments");
            } catch (SQLException e) {
                assertTrue("Expected column blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("is blocked"));
            }
        }
    }

    @Test
    public void testMultilineCommentBypass() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_multi_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                stmt.executeQuery("SELECT * FROM /*\n multiline comment \n*/ secret_table");
                fail("Expected SQLException for table not in whitelist with multiline comment");
            } catch (SQLException e) {
                assertTrue("Expected table not allowed message, but got: " + e.getMessage(),
                    e.getMessage().contains("is not in allowed tables list"));
            }
        }
    }
}
