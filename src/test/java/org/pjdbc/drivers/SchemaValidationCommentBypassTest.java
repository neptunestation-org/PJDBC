package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationCommentBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testCommentBypassBetweenKeywordAndTable() throws SQLException {
        // Only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_comment_bypass_2";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_comment_bypass_2")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS forbidden_table (id INT)");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // Testing comment between FROM and table name
                stmt.executeQuery("SELECT * FROM /**/ forbidden_table");
                fail("Bypassed SchemaValidationDriver using a comment between FROM and table name!");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver to block the statement, but got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }

    @Test
    public void testCommentAtStartBypass() throws SQLException {
        // Only allow 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_comment_bypass_3";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_comment_bypass_3")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS forbidden_table (id INT)");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // If it uses ^\\s* for some reason (it doesn't seem to, but good to check)
                stmt.executeQuery("/* comment */ SELECT * FROM forbidden_table");
                fail("Bypassed SchemaValidationDriver using a leading comment!");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver to block the statement, but got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }
}
