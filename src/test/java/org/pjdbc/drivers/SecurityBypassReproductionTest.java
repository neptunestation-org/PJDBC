package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;

public class SecurityBypassReproductionTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
    }

    @Test
    public void testReadonlyLeadingCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* leading comment */ INSERT INTO bypass_test VALUES (1)");
                fail("Should have been blocked by ReadonlyDriver");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver block, but got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Bypass by putting a comment between FROM and the table name
                stmt.execute("SELECT * FROM/**/forbidden_table");
                fail("Should have been blocked by SchemaValidationDriver");
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver block, but got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver") && e.getMessage().contains("forbidden_table"));
            }
        }
    }

    @Test
    public void testWhereTransformerLeadingCommentBypass() throws SQLException {
        // Condition: tenant_id = 1
        String url = "jdbc:filter[where=tenant_id=1]:jdbc:h2:mem:where_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Prepare some data
                try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:where_bypass")) {
                    setup.createStatement().execute("CREATE TABLE data (id INT, tenant_id INT)");
                    setup.createStatement().execute("INSERT INTO data VALUES (1, 1), (2, 2)");
                }

                // Normal query should be transformed
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM data")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                    assertTrue(!rs.next());
                }

                // Bypass with leading comment - if not transformed, it will see both rows
                try (ResultSet rs = stmt.executeQuery("/* bypass */ SELECT id FROM data")) {
                    if (rs.next() && rs.next()) {
                        fail("WhereTransformer bypass detected: saw both rows!");
                    }
                }
            }
        }
    }

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        String url = "jdbc:filter[schema=tenant1]:jdbc:h2:mem:schema_trans_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Prepare data in two schemas
                try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:schema_trans_bypass")) {
                    setup.createStatement().execute("CREATE SCHEMA tenant1");
                    setup.createStatement().execute("CREATE TABLE tenant1.data (id INT)");
                    setup.createStatement().execute("INSERT INTO tenant1.data VALUES (1)");
                    setup.createStatement().execute("CREATE TABLE data (id INT)");
                    setup.createStatement().execute("INSERT INTO data VALUES (2)");
                }

                // Normal query should be transformed to tenant1.data
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM data")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }

                // Bypass with comment - if not transformed, it will query public.data (id=2)
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM/**/data")) {
                    if (rs.next() && rs.getInt(1) == 2) {
                        fail("SchemaTransformer bypass detected: queried public schema!");
                    }
                }
            }
        }
    }
}
