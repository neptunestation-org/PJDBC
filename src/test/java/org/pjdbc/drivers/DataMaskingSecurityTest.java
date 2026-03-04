package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB))");
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupLOBTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked BLOB column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_getobject_type_bypass;DB_CLOSE_DELAY=-1")) {
             try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS string_data (id INT PRIMARY KEY, secret_val VARCHAR(100))");
                stmt.execute("INSERT INTO string_data VALUES (1, 'very secret')");
             }
        }

        String url = "jdbc:mask[columns=secret_val,strategy=REDACT]:jdbc:h2:mem:test_getobject_type_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_val FROM string_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("secret_val"));

                    // This should return the masked value if String.class is requested
                    assertEquals("[REDACTED]", rs.getObject("secret_val", String.class));

                    // This should throw for other types
                    try {
                        rs.getObject("secret_val", Integer.class);
                        fail("Expected SQLException for masked column with non-string type");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1")) {
             try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS secret_data (id INT PRIMARY KEY, secret_val VARCHAR(100))");
                stmt.execute("INSERT INTO secret_data VALUES (1, 'hidden')");
             }
        }

        String url = "jdbc:mask[columns=secret_val,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_val AS alias_val FROM secret_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("alias_val"));
                }
            }
        }
    }
}
