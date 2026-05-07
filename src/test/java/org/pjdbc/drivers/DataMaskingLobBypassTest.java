package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingLobBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testLobBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_mask_lob;DB_CLOSE_DELAY=-1";
        String url = "jdbc:mask[columns=secret]:" + h2Url;

        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (secret CLOB)");
                stmt.execute("INSERT INTO test VALUES ('sensitive data')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM test")) {
                    if (rs.next()) {
                        // getString() should be masked
                        String masked = rs.getString(1);
                        System.out.println("Masked string: " + masked);

                        // getClob() might NOT be masked if not overridden!
                        try {
                            java.sql.Clob clob = rs.getClob(1);
                            String unmasked = clob.getSubString(1, (int) clob.length());
                            if (unmasked.equals("sensitive data")) {
                                fail("Bypass successful: getClob() returned unmasked data: " + unmasked);
                            }
                        } catch (SQLException e) {
                            // Correct behavior: should probably throw or return masked Clob
                            System.out.println("getClob correctly blocked/masked: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_mask_alias;DB_CLOSE_DELAY=-1";
        String url = "jdbc:mask[columns=secret]:" + h2Url;

        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (secret VARCHAR(255))");
                stmt.execute("INSERT INTO test VALUES ('sensitive data')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // If we alias the column, does the masking still work?
                // DataMaskingDriver.MaskingResultSet.initMaskedColumns checks both name and label
                try (ResultSet rs = stmt.executeQuery("SELECT secret AS alias FROM test")) {
                    if (rs.next()) {
                        String masked = rs.getString("alias");
                        if (!masked.contains("*") && masked.equals("sensitive data")) {
                            fail("Bypass successful: alaised column was not masked");
                        }
                    }
                }
            }
        }
    }
}
