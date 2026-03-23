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
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_readonly_comment")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // Leading comment bypasses ^\\s* regex
                try {
                    stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                    fail("Expected SQLException for ReadonlyDriver bypass");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        throw e;
                    }
                }
            }
        }
    }

    @Test
    public void testReadonlyCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_readonly_cte")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // CTE bypasses DML pattern that starts with INSERT/UPDATE/DELETE
                try {
                    stmt.execute("WITH x AS (INSERT INTO test_table VALUES (1)) SELECT 1");
                    fail("Expected SQLException for CTE bypass");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        throw e;
                    }
                }
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Whitelist only allows table 'ALLOWED'
        String url = "jdbc:schema[allowedTables=ALLOWED]:jdbc:h2:mem:test_schema_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_comment")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE ALLOWED (id INT)");
                    stmt.execute("CREATE TABLE SECRET (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                // Should work for allowed table
                stmt.execute("SELECT * FROM ALLOWED");

                // Should fail for secret table
                try {
                    stmt.execute("SELECT * FROM SECRET");
                    fail("Expected SQLException for SECRET table");
                } catch (SQLException e) {
                    // Expected
                }

                // Bypass using comment: FROM/**/SECRET
                // Current pattern: \\b(?:FROM|JOIN|INTO|UPDATE|TABLE|TRUNCATE)\\s+([a-zA-Z_]...)
                // \\s+ won't match /**/
                try {
                    stmt.execute("SELECT * FROM/**/SECRET");
                    fail("Expected SQLException for SECRET table with comment bypass");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        throw e;
                    }
                }
            }
        }
    }
}
