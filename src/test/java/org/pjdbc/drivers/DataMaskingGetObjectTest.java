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

public class DataMaskingGetObjectTest {

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
    public void testGetObjectWithTypeLeaked() throws SQLException {
        setupTestTable("test_getobject_type");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_type;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This is expected to be masked
                    assertEquals("[REDACTED]", rs.getString("ssn"));

                    // VULNERABILITY: This might return the unmasked value!
                    String leaked = rs.getObject("ssn", String.class);
                    if ("123-45-6789".equals(leaked)) {
                        fail("Data leak! getObject(label, Class) returned unmasked value: " + leaked);
                    }
                }
            }
        }
    }
}
