package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobsTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_array INTEGER ARRAY)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST(X'48454C4C4F' AS BLOB), CAST('secret text' AS CLOB), ARRAY[1, 2, 3])");
            }
        }
    }

    private void assertMasked(ResultSet rs, String column, String method) throws SQLException {
        try {
            switch (method) {
                case "getBlob": rs.getBlob(column); break;
                case "getClob": rs.getClob(column); break;
                case "getNClob": rs.getNClob(column); break;
                case "getArray": rs.getArray(column); break;
                case "getSQLXML": rs.getSQLXML(column); break;
                case "getRef": rs.getRef(column); break;
                case "getURL": rs.getURL(column); break;
                case "getRowId": rs.getRowId(column); break;
                case "getObjectClass": rs.getObject(column, Integer.class); break;
                case "getObjectMap": rs.getObject(column, new HashMap<String, Class<?>>()); break;
            }
            fail("Expected SQLException for masked column '" + column + "' using " + method);
        } catch (SQLException e) {
            assertTrue("Expected message to contain 'masked', but was: " + e.getMessage(),
                e.getMessage().contains("masked"));
        }
    }

    @Test
    public void testLobMaskingBypass() throws SQLException {
        setupLobsTable("test_lobs");
        String url = "jdbc:mask[columns=secret_.*]:jdbc:h2:mem:test_lobs;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob, secret_clob, secret_array FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // These should now throw SQLException
                    assertMasked(rs, "secret_blob", "getBlob");
                    assertMasked(rs, "secret_clob", "getClob");
                    assertMasked(rs, "secret_array", "getArray");
                }
            }
        }
    }

    @Test
    public void testObjectVariantsMaskingBypass() throws SQLException {
        setupLobsTable("test_objects");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_objects;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // These should now throw SQLException
                    assertMasked(rs, "secret_clob", "getObjectClass");
                    assertMasked(rs, "secret_clob", "getObjectMap");
                }
            }
        }
    }
}
