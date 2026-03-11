package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.ResultSet;

public class ReadonlyBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This might bypass the current regex: ^\s*(INSERT|...)
                stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected error to mention ReadonlyDriver, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=test]:jdbc:h2:mem:test_schema_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
                // This might bypass if we use \s+ instead of SQL_SEP
                stmt.execute("/* comment */ INSERT INTO /* comment */ test /* comment */ VALUES (1)");
            }
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                stmt.execute("SELECT * FROM /* comment */ secrets");
                fail("Expected SQLException for SELECT from secrets with comment");
            } catch (SQLException e) {
                // Expected
            }
        }
    }

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        // jdbc:filter[schema=tenant_1]:jdbc:h2:mem:test_schema_transformer
        String url = "jdbc:filter[schema=tenant_1]:jdbc:h2:mem:test_schema_transformer";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_transformer")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE SCHEMA tenant_1");
                    stmt.execute("CREATE TABLE tenant_1.test (id INT)");
                    stmt.execute("INSERT INTO tenant_1.test VALUES (1)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // This should be transformed to: SELECT * FROM /* comment */ tenant_1.test
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM /* comment */ test")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML
                stmt.execute("WITH deleted AS (DELETE FROM test RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for CTE with DML");
            } catch (SQLException e) {
                assertTrue("Expected error to mention ReadonlyDriver, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
