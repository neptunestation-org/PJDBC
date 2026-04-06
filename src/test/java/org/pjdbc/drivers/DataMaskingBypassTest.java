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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupTestTable("test_bypass");
        // Mask the 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=FULL]:jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Query with an alias
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This should be masked if the driver is secure
                    String maskedByLabel = rs.getString("alias");
                    String maskedByIndex = rs.getString(1);

                    System.out.println("Value by label 'alias': " + maskedByLabel);
                    System.out.println("Value by index 1: " + maskedByIndex);

                    assertEquals("***********", maskedByIndex);
                    assertEquals("If this fails, there is a bypass", "***********", maskedByLabel);
                }
            }
        }
    }
}
