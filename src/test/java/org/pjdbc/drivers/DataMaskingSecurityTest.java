package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Blob;
import java.sql.Clob;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupSecurityTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS security_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_ssn VARCHAR(100), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                // Insert some data
                java.sql.PreparedStatement pstmt = conn.prepareStatement("INSERT INTO security_data VALUES (?, ?, ?, ?)");
                pstmt.setInt(1, 1);
                pstmt.setString(2, "123-45-6789");

                Blob blob = conn.createBlob();
                blob.setBytes(1, "SENSITIVE_BLOB_DATA".getBytes());
                pstmt.setBlob(3, blob);

                Clob clob = conn.createClob();
                clob.setString(1, "SENSITIVE_CLOB_DATA");
                pstmt.setClob(4, clob);

                pstmt.execute();
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupSecurityTable("test_getObject_bypass");
        String url = "jdbc:mask[columns=secret_ssn,strategy=REDACT]:jdbc:h2:mem:test_getObject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_ssn FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // getString is masked
                    assertEquals("[REDACTED]", rs.getString("secret_ssn"));

                    // getObject(String, Class) bypasses masking currently
                    String bypassedValue = rs.getObject("secret_ssn", String.class);
                    if ("123-45-6789".equals(bypassedValue)) {
                        fail("VULNERABILITY: getObject(columnLabel, Class<T>) bypassed masking!");
                    }
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupSecurityTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getBlob bypasses masking currently
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            if (new String(bytes).equals("SENSITIVE_BLOB_DATA")) {
                                fail("VULNERABILITY: getBlob(columnLabel) bypassed masking!");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupSecurityTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getClob bypasses masking currently
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String value = clob.getSubString(1, (int) clob.length());
                            if ("SENSITIVE_CLOB_DATA".equals(value)) {
                                fail("VULNERABILITY: getClob(columnLabel) bypassed masking!");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                    }
                }
            }
        }
    }
}
