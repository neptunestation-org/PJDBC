package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM/**/secret_table");
                fail("Expected SQLException for access to secret_table via comment bypass");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver error, but got: " + e.getMessage(),
                    e.getMessage().contains("is not in allowed tables list"));
            }
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass_multiline";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT/* comment \n multi-line */*/* comment */FROM/* comment */secret_table");
                fail("Expected SQLException for access to secret_table via multi-line comment bypass");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver error, but got: " + e.getMessage(),
                    e.getMessage().contains("is not in allowed tables list"));
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass_line";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT *\nFROM -- line comment\n secret_table");
                fail("Expected SQLException for access to secret_table via line comment bypass");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver error, but got: " + e.getMessage(),
                    e.getMessage().contains("is not in allowed tables list"));
            }
        }
    }

    @Test
    public void testInsertColumnBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret_col]:jdbc:h2:mem:test_schema_bypass_insert";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO some_table/**/(secret_col) VALUES (1)");
                fail("Expected SQLException for blocked secret_col");
            } catch (SQLException e) {
                assertTrue("Expected Column blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("Column 'secret_col' is blocked"));
            }
        }
    }

    @Test
    public void testUpdateColumnBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret_col]:jdbc:h2:mem:test_schema_bypass_update";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE some_table SET/**/secret_col = 1");
                fail("Expected SQLException for blocked secret_col in UPDATE");
            } catch (SQLException e) {
                assertTrue("Expected Column blocked message, but got: " + e.getMessage(),
                    e.getMessage().contains("Column 'secret_col' is blocked"));
            }
        }
    }
}
