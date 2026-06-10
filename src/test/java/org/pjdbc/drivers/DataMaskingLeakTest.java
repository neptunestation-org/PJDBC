package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingLeakTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, X'48454C4C4F', 'SECRET_CLOB_CONTENT')");
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupLOBTable("test_blob_leak");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Should have thrown SQLException for masked Blob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupLOBTable("test_clob_leak");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Should have thrown SQLException for masked Clob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeMasked() throws SQLException {
        setupLOBTable("test_getobject_type");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_getobject_type;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getObject("secret_clob", String.class));
                    try {
                        rs.getObject("secret_clob", java.sql.Clob.class);
                        fail("Should have thrown SQLException for masked Clob object");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasNoBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS sensitive (ssn VARCHAR(11))");
                stmt.execute("INSERT INTO sensitive VALUES ('123-45-6789')");
            }
        }
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_info FROM sensitive")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("public_info"));
                }
            }
        }
    }
}
