package org.pjdbc.drivers;

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
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) return;
            fail("Readonly Bypass successful! Statement reached database: " + e.getMessage());
        }
    }

    @Test
    public void testReadonlyCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH cte AS (DELETE FROM test_table) SELECT 1");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) return;
            fail("Readonly CTE Bypass successful! Statement reached database: " + e.getMessage());
        }
    }

    @Test
    public void testSchemaTableCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // TABLE_PATTERN: \\b(FROM|JOIN|INTO|UPDATE|TABLE|TRUNCATE)" + SEP + "([a-zA-Z_][a-zA-Z0-9_]*)
                stmt.execute("SELECT * FROM /**/ secrets");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) return;
            fail("Schema Table Bypass successful! Statement reached database: " + e.getMessage());
        }
    }

    @Test
    public void testSchemaColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=ssn]:jdbc:h2:mem:schema_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // SELECT_COLUMNS_PATTERN: \\bSELECT" + SEP + "(.+?)" + SEP + "FROM\\b
                stmt.execute("SELECT /* comment */ ssn FROM users");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) return;
            fail("Schema Column Bypass successful! Statement reached database: " + e.getMessage());
        }
    }
}
