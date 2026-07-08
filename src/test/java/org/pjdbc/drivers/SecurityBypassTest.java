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
import org.pjdbc.sql.WhereTransformer;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* leading comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment in ReadonlyDriver");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain 'DML blocked', but was: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testReadonlyCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML should be blocked
                stmt.executeQuery("WITH x AS (DELETE FROM test_table) SELECT 1");
                fail("Expected SQLException for CTE-based DELETE in ReadonlyDriver");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain 'DML blocked', but was: " + e.getMessage(),
                e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testSchemaTableCommentBypass() throws SQLException {
        // Whitelist mode: only 'users' allowed
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked because 'secret_table' is not in whitelist.
                stmt.executeQuery("SELECT * FROM /* comment */ secret_table");
                fail("Expected SQLException for unauthorized table with comment in SchemaValidationDriver");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain 'is not in allowed tables list', but was: " + e.getMessage(),
                e.getMessage().contains("is not in allowed tables list"));
        }
    }

    @Test
    public void testSchemaColumnCommentBypass() throws SQLException {
        // Blocked columns: 'password'
        String url = "jdbc:schema[blockedColumns=password]:jdbc:h2:mem:test_schema_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT /* comment */ password FROM users");
                fail("Expected SQLException for blocked column with comment in SchemaValidationDriver");
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain 'is blocked', but was: " + e.getMessage(),
                e.getMessage().contains("is blocked"));
        }
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        // We use FilterDriver with WhereTransformer to test this
        FilterDriver driver = new FilterDriver();
        driver.setTransformer(new WhereTransformer("tenant_id=1"));

        // Mock a connection to test transformation
        String sql = "/* comment */ SELECT * FROM orders";
        String transformed = driver.getTransformer().transformSql(sql);

        // If bypass works, it might append WHERE in a wrong place or not at all if it doesn't match MODIFIABLE_STATEMENT
        assertTrue("Transformed SQL should contain WHERE tenant_id=1. Was: " + transformed,
            transformed.contains("WHERE tenant_id=1"));
    }
}
