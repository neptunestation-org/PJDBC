package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testReadonlyMultilineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading multiline comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testReadonlySingleLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_single";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- comment\n INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading single-line comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Whitelist mode, test_table is not in allowed list (empty allowed list means all allowed, so we use a non-empty one)
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT * FROM/*comment*/test_table");
                fail("Expected SQLException for SELECT with interspersed comment");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver"));
                assertTrue("Expected 'test_table' in error message, got: " + e.getMessage(),
                    e.getMessage().contains("test_table"));
            }
        }
    }

    @Test
    public void testReadonlyDdlCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_ddl";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ DROP TABLE test_table");
                fail("Expected SQLException for DROP with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
                assertTrue("Expected 'DDL blocked' in error message, got: " + e.getMessage(),
                    e.getMessage().contains("DDL blocked"));
            }
        }
    }
}
