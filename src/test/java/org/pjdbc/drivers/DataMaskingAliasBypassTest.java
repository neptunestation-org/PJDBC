package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        // Mask the 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Try to bypass by aliasing the column
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS ssn_alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("ssn_alias");
                    // If it's NOT [REDACTED], then we have a bypass
                    if (!"[REDACTED]".equals(value)) {
                        System.out.println("VULNERABILITY CONFIRMED: Got unmasked value via alias: " + value);
                    } else {
                        System.out.println("No bypass detected.");
                    }
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }
}
