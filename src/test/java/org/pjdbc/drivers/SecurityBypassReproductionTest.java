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
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String baseUri = "jdbc:h2:mem:readonly_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:readonly:" + baseUri;

        try (Connection setupConn = DriverManager.getConnection(baseUri)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD fail if hardened, but currently might pass because of the leading comment
                stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment in ReadonlyDriver");
            } catch (SQLException e) {
                // If it failed with "Table NOT FOUND", it means bypass worked but H2 failed later.
                // If it failed with "ReadonlyDriver", it means it was blocked.
                assertTrue("Expected ReadonlyDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        String baseUri = "jdbc:h2:mem:schema_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:" + baseUri;

        try (Connection setupConn = DriverManager.getConnection(baseUri)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE secrets (data VARCHAR(255))");
                stmt.execute("INSERT INTO secrets VALUES ('hidden')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD fail if hardened, but currently might pass because of the comment
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM /* comment */ secrets")) {
                    if (rs.next()) {
                        fail("Bypass successful: accessed blocked table 'secrets' using comment");
                    }
                }
            } catch (SQLException e) {
                assertTrue("Expected SchemaValidationDriver in error message, got: " + e.getMessage(),
                    e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }

    @Test
    public void testDataMaskingAliasingBypass() throws SQLException {
        String baseUri = "jdbc:h2:mem:mask_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:" + baseUri;

        try (Connection setupConn = DriverManager.getConnection(baseUri)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(255))");
                stmt.execute("INSERT INTO users VALUES (1, '123-456-7890')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS my_alias FROM users")) {
                    assertTrue(rs.next());
                    // ssn is masked, but we are accessing it via alias 'my_alias'
                    // If it's not hardened, rs.getString("my_alias") might return the real ssn
                    String value = rs.getString("my_alias");
                    assertEquals("Bypass successful: masked column 'ssn' leaked via alias 'my_alias'",
                        "[REDACTED]", value);
                }
            }
        }
    }
}
