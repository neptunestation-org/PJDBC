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

public class DataMaskingAliasBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testAliasBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        // Masking 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Query with alias
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS secret_ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // After fix, it SHOULD be masked because the underlying column name is 'ssn'
                    String ssn = rs.getString("secret_ssn");
                    assertEquals("[REDACTED]", ssn);
                }
            }
        }
    }

    @Test
    public void testIntAliasBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_int_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, age INT)");
                stmt.execute("INSERT INTO users VALUES (1, 30)");
            }
        }

        String url = "jdbc:mask[columns=age,strategy=REDACT]:jdbc:h2:mem:test_int_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT age AS secret_age FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getInt("secret_age");
                        fail("Expected SQLException for masked column alias");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
