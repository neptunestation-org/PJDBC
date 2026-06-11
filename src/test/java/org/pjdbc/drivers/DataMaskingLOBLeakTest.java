package org.pjdbc.drivers;

import static org.junit.Assert.assertNotNull;
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

public class DataMaskingLOBLeakTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testLOBLeak() throws SQLException {
        String dbUrl = "jdbc:h2:mem:test_lob_leak;DB_CLOSE_DELAY=-1";
        String url = "jdbc:mask[columns=secret_blob;secret_clob]:" + dbUrl;
        try (Connection setupConn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test_lob (id INT, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO test_lob VALUES (1, X'01020304', 'secret data')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM test_lob")) {
                    if (rs.next()) {
                        try {
                            Blob blob = rs.getBlob("secret_blob");
                            // If we get here, it means it didn't throw SQLException
                            fail("VULNERABILITY: Successfully retrieved BLOB from masked column. Should have thrown SQLException.");
                        } catch (SQLException e) {
                            if (!e.getMessage().contains("DataMaskingDriver")) {
                                throw e;
                            }
                        }

                        try {
                            Clob clob = rs.getClob("secret_clob");
                            // If we get here, it means it didn't throw SQLException
                            fail("VULNERABILITY: Successfully retrieved CLOB from masked column. Should have thrown SQLException.");
                        } catch (SQLException e) {
                            if (!e.getMessage().contains("DataMaskingDriver")) {
                                throw e;
                            }
                        }
                    }
                }
            }
        }
    }
}
