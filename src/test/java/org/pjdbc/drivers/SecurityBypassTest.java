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
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked, but if it's bypassed it will try to execute
                // We use a syntax that is valid in H2 but starts with a comment
                stmt.execute("/**/ CREATE TABLE bypass (id INT)");
                // If it reached here, it bypassed the check (though CREATE might fail if H2 doesn't like it at the start,
                // but the point is it passed ReadonlyDriver)
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                // Correctly blocked
                return;
            }
            // Other SQL error
        }
        fail("ReadonlyDriver bypass: statement with leading comment was not blocked");
    }

    @Test
    public void testReadonlyCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("WITH t AS (INSERT INTO some_table VALUES (1)) SELECT 1");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                return;
            }
        }
        fail("ReadonlyDriver bypass: WITH statement with DML was not blocked");
    }

    @Test
    public void testSchemaValidationCommaBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=t1]:jdbc:h2:mem:schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Create t1 and t2 first in a non-proxy connection
                try (Connection raw = DriverManager.getConnection("jdbc:h2:mem:schema_bypass")) {
                    raw.createStatement().execute("CREATE TABLE t1 (id INT)");
                    raw.createStatement().execute("CREATE TABLE t2 (id INT)");
                }
                // Should only allow t1. But it might allow t2 if comma-separated.
                stmt.executeQuery("SELECT * FROM t1, t2");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("SchemaValidationDriver")) {
                return;
            }
        }
        fail("SchemaValidationDriver bypass: comma-separated table was not blocked");
    }
}
