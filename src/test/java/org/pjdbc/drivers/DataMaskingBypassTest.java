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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES ('123-45-6789')");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_bypass");
        // Mask the column 'ssn'
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Use an alias that does NOT match the mask pattern
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_id FROM users")) {
                    assertTrue(rs.next());

                    // This SHOULD be masked because it's the 'ssn' column
                    String value = rs.getString("public_id");
                    if ("123-45-6789".equals(value)) {
                        fail("Bypass detected! Alias 'public_id' allowed access to unmasked 'ssn': " + value);
                    }
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }
}
