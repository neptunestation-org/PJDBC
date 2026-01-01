
package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

import com.zaxxer.hikari.pool.HikariProxyConnection;

public class HikariPoolDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.HikariPoolDriver");
    }

    @Test
    public void testDriverRegistration() throws SQLException {
        assertNotNull(DriverManager.getDriver("jdbc:hikaricp:jdbc:h2:mem:test"));
    }

    @Test
    public void testConnect() throws SQLException {
        String url = "jdbc:hikaricp:jdbc:h2:mem:test";
        Properties info = new Properties();
        info.setProperty("user", "sa");
        info.setProperty("password", "");

        try (Connection conn = DriverManager.getConnection(url, info)) {
            assertNotNull(conn);
            assertTrue(conn.isWrapperFor(Connection.class));

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    public void testConnectWithProperties() throws SQLException {
        String url = "jdbc:hikaricp:jdbc:h2:mem:test2?maximumPoolSize=5";
        Properties info = new Properties();
        info.setProperty("user", "sa");
        info.setProperty("password", "");

        try (Connection conn = DriverManager.getConnection(url, info)) {
            assertNotNull(conn);
            assertTrue(conn.isWrapperFor(Connection.class));
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
