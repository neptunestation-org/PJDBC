package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Clob;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testClobBypass() throws SQLException {
        String url = "jdbc:mask[columns=secret]:jdbc:h2:mem:test_clob_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table_clob (secret CLOB)");
                stmt.execute("INSERT INTO test_table_clob VALUES ('very secret data')");

                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM test_table_clob")) {
                    if (rs.next()) {
                        // getString should be masked
                        String masked = rs.getString(1);
                        System.out.println("Masked: " + masked);

                        // getClob might NOT be masked
                        Clob clob = rs.getClob(1);
                        String actual = clob.getSubString(1, (int) clob.length());
                        System.out.println("Clob actual: " + actual);

                        if (actual.equals("very secret data")) {
                            fail("Data leak! getClob() bypassed masking");
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        String url = "jdbc:mask[columns=secret]:jdbc:h2:mem:test_alias_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table_alias (secret VARCHAR(200))");
                stmt.execute("INSERT INTO test_table_alias VALUES ('very secret data')");

                try (ResultSet rs = stmt.executeQuery("SELECT secret AS public_info FROM test_table_alias")) {
                    if (rs.next()) {
                        String leaked = rs.getString("public_info");
                        System.out.println("Leaked via alias: " + leaked);
                        if (leaked.equals("very secret data")) {
                            fail("Data leak! Aliasing bypassed masking");
                        }
                    }
                }
            }
        }
    }
}
