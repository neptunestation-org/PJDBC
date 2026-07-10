package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* bypass */ DELETE FROM some_table");
                fail("Expected SQLException for DELETE with leading comment in ReadonlyDriver");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Table \"SOME_TABLE\" not found")) {
                fail("Bypassed ReadonlyDriver! SQL reached the database: " + e.getMessage());
            }
            assertTrue("Expected DML blocked message, got: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testReadonlyCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH x AS (DELETE FROM some_table) SELECT 1");
                fail("Expected SQLException for CTE with DELETE in ReadonlyDriver");
            }
        } catch (SQLException e) {
             if (e.getMessage().contains("Syntax error") || e.getMessage().contains("Table \"SOME_TABLE\" not found")) {
                fail("Bypassed ReadonlyDriver! SQL reached the database: " + e.getMessage());
            }
            assertTrue("Expected DML blocked message, got: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testSchemaCommentBypass() throws SQLException {
        // Whitelist 'users' table, but try to access 'secrets' with a comment
        String url = "jdbc:schema[allowedTables=users]:jdbc:h2:mem:test_schema_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* bypass */ SELECT * FROM secrets");
                fail("Expected SQLException for access to blocked table with leading comment in SchemaValidationDriver");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Table \"SECRETS\" not found")) {
                fail("Bypassed SchemaValidationDriver! SQL reached the database: " + e.getMessage());
            }
            assertTrue("Expected table not in allowed list message, got: " + e.getMessage(),
                e.getMessage().contains("is not in allowed tables list"));
        }
    }

    @Test
    public void testSchemaIntermediateCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=users]:jdbc:h2:mem:test_schema_intermediate";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Table pattern might fail if there's a comment between FROM and table name
                stmt.execute("SELECT * FROM /* bypass */ secrets");
                fail("Expected SQLException for access to blocked table with intermediate comment in SchemaValidationDriver");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Table \"SECRETS\" not found")) {
                fail("Bypassed SchemaValidationDriver! SQL reached the database: " + e.getMessage());
            }
            assertTrue("Expected table not in allowed list message, got: " + e.getMessage(),
                e.getMessage().contains("is not in allowed tables list"));
        }
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        // Filter that appends 'tenant_id=1'
        String url = "jdbc:filter[where=tenant_id=1]:jdbc:h2:mem:test_where_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // If bypassed, it won't add WHERE tenant_id=1
                // We'll use a table that doesn't exist to see what SQL reaches H2
                try {
                    stmt.execute("/* bypass */ SELECT * FROM non_existent");
                } catch (SQLException e) {
                    // Expected SQL if NOT bypassed: /* bypass */ SELECT * FROM non_existent WHERE tenant_id=1
                    // Expected SQL if bypassed: /* bypass */ SELECT * FROM non_existent
                    String msg = e.getMessage().toUpperCase();
                    assertTrue("Bypassed WhereTransformer! SQL reached database without transformation: " + e.getMessage(),
                        msg.contains("WHERE TENANT_ID=1"));
                }
            }
        }
    }
}
