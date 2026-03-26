package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Properties;

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
                    "secret_clob CLOB, " +
                    "secret_blob BLOB)");

                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (?, ?, ?)");
                pstmt.setInt(1, 1);
                pstmt.setString(2, "This is a very secret message in a CLOB");
                pstmt.setBytes(3, "Secret BLOB data".getBytes());
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupLOBTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString() should be masked
                    assertEquals("[REDACTED]", rs.getString("secret_clob"));

                    // getClob() currently might NOT be masked and return the raw data!
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String rawData = clob.getSubString(1, (int) clob.length());
                            if (!"[REDACTED]".equals(rawData)) {
                                fail("VULNERABILITY: Mask bypassed via getClob()");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected after fix
                    }
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupLOBTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getBlob() currently might NOT be masked
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] rawData = blob.getBytes(1, (int) blob.length());
                            String rawString = new String(rawData);
                            if (!"[REDACTED]".equals(rawString)) {
                                fail("VULNERABILITY: Mask bypassed via getBlob()");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected after fix
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        // Mask the 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias the column: SELECT ssn AS secret_alias
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS secret_alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // If the driver only checks the column label 'secret_alias', it might not mask it!
                    String masked = rs.getString("secret_alias");
                    if (!"[REDACTED]".equals(masked)) {
                        fail("VULNERABILITY: Mask bypassed via column aliasing: " + masked);
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(int, Class<T>) or getObject(String, Class<T>) might bypass masking
                    try {
                        String ssn = rs.getObject(1, String.class);
                        if (!"[REDACTED]".equals(ssn)) {
                            fail("VULNERABILITY: Mask bypassed via getObject(int, Class): " + ssn);
                        }
                    } catch (SQLException e) {
                        // Expected after fix if it throws
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithMapBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_getobject_map;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_map;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(int, Map) or getObject(String, Map) might bypass masking
                    try {
                        Object ssn = rs.getObject(1, new HashMap<String, Class<?>>());
                        if (ssn instanceof String s && !"[REDACTED]".equals(s)) {
                            fail("VULNERABILITY: Mask bypassed via getObject(int, Map): " + s);
                        }
                    } catch (SQLException e) {
                        // Expected after fix if it throws
                    }
                }
            }
        }
    }
}
