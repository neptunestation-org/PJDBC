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
    }

    @Test
    public void testReadonlyMultilineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* leading \n multiline \n comment */ CREATE TABLE bypass (id INT)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("ReadonlyDriver")) {
                    fail("Should have been blocked by ReadonlyDriver, but got: " + e.getMessage());
                }
                return;
            }
            fail("ReadonlyDriver was bypassed using a multiline leading comment!");
        }
    }

    @Test
    public void testReadonlySingleLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass_single";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- leading single line comment \n CREATE TABLE bypass (id INT)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("ReadonlyDriver")) {
                    fail("Should have been blocked by ReadonlyDriver, but got: " + e.getMessage());
                }
                return;
            }
            fail("ReadonlyDriver was bypassed using a single-line leading comment!");
        }
    }

    @Test
    public void testSchemaValidationInterspersedCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed,mode=whitelist]:jdbc:h2:mem:schema_bypass_inter";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:schema_bypass_inter")) {
                    setup.createStatement().execute("CREATE TABLE forbidden (id INT)");
                }

                stmt.executeQuery("SELECT * FROM/*comment*/forbidden");
            } catch (SQLException e) {
                if (!e.getMessage().contains("SchemaValidationDriver")) {
                    fail("Should have been blocked by SchemaValidationDriver, but got: " + e.getMessage());
                }
                return;
            }
            fail("SchemaValidationDriver was bypassed using an interspersed comment!");
        }
    }

    @Test
    public void testSchemaValidationInterspersedSingleLineCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed,mode=whitelist]:jdbc:h2:mem:schema_bypass_inter_single";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:schema_bypass_inter_single")) {
                    setup.createStatement().execute("CREATE TABLE forbidden (id INT)");
                }

                stmt.executeQuery("SELECT * FROM --comment\nforbidden");
            } catch (SQLException e) {
                if (!e.getMessage().contains("SchemaValidationDriver")) {
                    fail("Should have been blocked by SchemaValidationDriver, but got: " + e.getMessage());
                }
                return;
            }
            fail("SchemaValidationDriver was bypassed using an interspersed single-line comment!");
        }
    }

    @Test
    public void testSchemaValidationColumnAliasBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret,mode=blacklist]:jdbc:h2:mem:schema_column_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:schema_column_bypass")) {
                    setup.createStatement().execute("CREATE TABLE t (secret INT)");
                }

                stmt.executeQuery("SELECT secret/**/AS/**/alias FROM t");
            } catch (SQLException e) {
                if (!e.getMessage().contains("SchemaValidationDriver")) {
                    fail("Should have been blocked by SchemaValidationDriver, but got: " + e.getMessage());
                }
                return;
            }
            fail("SchemaValidationDriver was bypassed using comments in column alias!");
        }
    }
}
