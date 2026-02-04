package org.pjdbc.drivers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingDriverSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupSecurityTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS security_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_array INT ARRAY)");
                stmt.execute("INSERT INTO security_data VALUES (1, X'48454C4C4F', 'secret text', ARRAY[1, 2, 3])");
            }
        }
    }

    @Test
    public void testComplexTypesAreBlockedWhenMasked() throws SQLException {
        setupSecurityTable("test_block");
        // Mask all secret columns
        String url = "jdbc:mask[columns=secret_.*,strategy=REDACT]:jdbc:h2:mem:test_block;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob, secret_clob, secret_array FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // Test Blob is blocked
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getBlob"));
                    }

                    // Test Clob is blocked
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }

                    // Test Array is blocked
                    try {
                        rs.getArray("secret_array");
                        fail("Expected SQLException for masked Array");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getArray"));
                    }

                    // Test getObject(int, Class) is blocked
                    try {
                        rs.getObject("secret_clob", String.class);
                        fail("Expected SQLException for masked getObject(String, Class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getObject"));
                    }
                }
            }
        }
    }
}
