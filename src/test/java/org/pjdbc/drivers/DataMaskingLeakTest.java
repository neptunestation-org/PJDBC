package org.pjdbc.drivers;

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

    @Test
    public void testBlobLeakPrevented() throws SQLException {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1")) {
            setup.createStatement().execute("CREATE TABLE b (s BLOB); INSERT INTO b VALUES (CAST('secret' AS BLOB))");
        }
        try (Connection conn = DriverManager.getConnection("jdbc:mask[columns=s]:jdbc:h2:mem:test_blob")) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT s FROM b");
            rs.next();
            try { rs.getBlob("s"); fail(); } catch (SQLException e) { assertTrue(e.getMessage().contains("masked")); }
        }
    }

    @Test
    public void testClobLeakPrevented() throws SQLException {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1")) {
            setup.createStatement().execute("CREATE TABLE c (s CLOB); INSERT INTO c VALUES (CAST('secret' AS CLOB))");
        }
        try (Connection conn = DriverManager.getConnection("jdbc:mask[columns=s]:jdbc:h2:mem:test_clob")) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT s FROM c");
            rs.next();
            try { rs.getClob("s"); fail(); } catch (SQLException e) { assertTrue(e.getMessage().contains("masked")); }
        }
    }
}
