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
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked, even with leading comments
                stmt.execute("/* bypass */ INSERT INTO test_table VALUES (1)");
            } catch (SQLException e) {
                if (e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked")) {
                    return;
                }
                fail("Expected ReadonlyDriver DML blocked exception, but got: " + e.getMessage());
            }
            fail("ReadonlyDriver bypass: INSERT with leading comment was not blocked");
        }
    }

    @Test
    public void testReadonlyMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/*\n multi-line \n*/ DELETE FROM test_table");
            } catch (SQLException e) {
                if (e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked")) {
                    return;
                }
                fail("Expected ReadonlyDriver DML blocked exception for multi-line comment, but got: " + e.getMessage());
            }
            fail("ReadonlyDriver bypass: DELETE with multi-line leading comment was not blocked");
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT * FROM /* bypass */ secrets");
            } catch (SQLException e) {
                if (e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("blocked")) {
                    return;
                }
                fail("Expected SchemaValidationDriver blocked exception, but got: " + e.getMessage());
            }
            fail("SchemaValidationDriver bypass: Table check bypassed with interspersed comment");
        }
    }

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException, ClassNotFoundException {
        // We use MockDriver to see what SQL actually gets through
        Class.forName("org.pjdbc.drivers.MockDriver");
        String url = "jdbc:filter[schema=tenant1]:jdbc:mock:test_transformer_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM /* comment */ users");
                String log = MockDriver.getLog("jdbc:mock:test_transformer_bypass");
                assertTrue("SchemaTransformer should have prefixed table name despite comment. Log: " + log,
                           log.contains("tenant1.users"));
            }
        }
    }
}
