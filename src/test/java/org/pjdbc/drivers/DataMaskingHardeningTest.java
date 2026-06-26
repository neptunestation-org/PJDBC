package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingHardeningTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testAliasBypassFixed() throws SQLException {
        // Mask columns matching "ssn"
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_mask_alias_fixed";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(20))");
                stmt.execute("INSERT INTO users VALUES (1, '123-456-7890')");

                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_info FROM users")) {
                    assertTrue(rs.next());

                    // index-based access
                    assertEquals("[REDACTED]", rs.getString(1));

                    // label-based access (the alias) should now also be masked
                    assertEquals("[REDACTED]", rs.getString("public_info"));
                }
            }
        }
    }

    @Test
    public void testLobBypassBlocked() throws SQLException {
        String url = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:test_mask_lob_blocked";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE lobs (id INT, secret CLOB)");
                stmt.execute("INSERT INTO lobs VALUES (1, 'very secret content')");

                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM lobs")) {
                    assertTrue(rs.next());

                    // getClob should throw SQLException
                    try {
                        rs.getClob(1);
                        fail("getClob(int) should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }

                    try {
                        rs.getClob("secret");
                        fail("getClob(String) should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectClassBypassBlocked() throws SQLException {
        String url = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:test_mask_getobject_blocked";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users_go (id INT, secret VARCHAR(20))");
                stmt.execute("INSERT INTO users_go VALUES (1, 'password123')");

                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM users_go")) {
                    assertTrue(rs.next());

                    // getObject(index, String.class) should return masked string
                    assertEquals("[REDACTED]", rs.getObject(1, String.class));
                    assertEquals("[REDACTED]", rs.getObject("secret", String.class));

                    // getObject(index, Integer.class) should throw
                    try {
                        rs.getObject(1, Integer.class);
                        fail("getObject(int, Class) should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testSpecializedTypeBypassBlocked() throws SQLException {
        String url = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:test_mask_specialized_blocked";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE specialized (id INT, secret VARCHAR(255))");
                stmt.execute("INSERT INTO specialized VALUES (1, 'http://secret.com')");

                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM specialized")) {
                    assertTrue(rs.next());

                    try {
                        rs.getURL(1);
                        fail("getURL(int) should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }

                    try {
                        rs.getURL("secret");
                        fail("getURL(String) should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }
}
