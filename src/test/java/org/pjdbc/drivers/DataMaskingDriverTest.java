package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT PRIMARY KEY, " +
                    "name VARCHAR(100), " +
                    "email VARCHAR(100), " +
                    "ssn VARCHAR(11), " +
                    "credit_card VARCHAR(16), " +
                    "password VARCHAR(100))");
                stmt.execute("INSERT INTO users VALUES (1, 'John Doe', 'john@example.com', '123-45-6789', '4111111111111234', 'secret123')");
                stmt.execute("INSERT INTO users VALUES (2, 'Jane Smith', 'jane@test.org', '987-65-4321', '5500000000005678', 'password456')");
            }
        }
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        DataMaskingDriver driver = new DataMaskingDriver();
        assertTrue(driver.acceptsURL("jdbc:mask:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:mask[columns=ssn]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:mask:jdbc:h2:mem:test_basic";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testNoMaskingWithoutColumns() throws SQLException {
        setupTestTable("test_no_mask");
        String url = "jdbc:mask:jdbc:h2:mem:test_no_mask;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    // No columns configured, so no masking
                    assertEquals("123-45-6789", rs.getString("ssn"));
                }
            }
        }
    }

    @Test
    public void testPartialMaskingDefault() throws SQLException {
        setupTestTable("test_partial");
        String url = "jdbc:mask[columns=ssn]:jdbc:h2:mem:test_partial;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("ssn");
                    // Default: show last 4 chars
                    assertTrue(masked.endsWith("6789"));
                    assertTrue(masked.startsWith("*"));
                }
            }
        }
    }

    @Test
    public void testFullMasking() throws SQLException {
        setupTestTable("test_full");
        String url = "jdbc:mask[columns=ssn,strategy=FULL]:jdbc:h2:mem:test_full;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("ssn");
                    assertEquals("***********", masked); // 11 asterisks
                }
            }
        }
    }

    @Test
    public void testEmailMasking() throws SQLException {
        setupTestTable("test_email");
        String url = "jdbc:mask[columns=email,strategy=EMAIL]:jdbc:h2:mem:test_email;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT email FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("email");
                    assertTrue(masked.startsWith("j"));
                    assertTrue(masked.contains("@example.com"));
                    assertTrue(masked.contains("***"));
                }
            }
        }
    }

    @Test
    public void testRedactMasking() throws SQLException {
        setupTestTable("test_redact");
        String url = "jdbc:mask[columns=password,strategy=REDACT]:jdbc:h2:mem:test_redact;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT password FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("password"));
                }
            }
        }
    }

    @Test
    public void testHashMasking() throws SQLException {
        setupTestTable("test_hash");
        String url = "jdbc:mask[columns=password,strategy=HASH]:jdbc:h2:mem:test_hash;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT password FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("password");
                    assertTrue(masked.endsWith("..."));
                    assertTrue(masked.length() == 11); // 8 hex chars + "..."
                }
            }
        }
    }

    @Test
    public void testMultipleColumns() throws SQLException {
        setupTestTable("test_multi");
        String url = "jdbc:mask[columns=ssn;credit_card;password,strategy=REDACT]:jdbc:h2:mem:test_multi;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT name, ssn, credit_card, password FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("John Doe", rs.getString("name")); // Not masked
                    assertEquals("[REDACTED]", rs.getString("ssn"));
                    assertEquals("[REDACTED]", rs.getString("credit_card"));
                    assertEquals("[REDACTED]", rs.getString("password"));
                }
            }
        }
    }

    @Test
    public void testRegexColumnPattern() throws SQLException {
        setupTestTable("test_regex");
        String url = "jdbc:mask[columns=.*card.*,strategy=PARTIAL,showLast=4]:jdbc:h2:mem:test_regex;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT credit_card FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("credit_card");
                    assertTrue(masked.endsWith("1234"));
                    assertTrue(masked.startsWith("*"));
                }
            }
        }
    }

    @Test
    public void testCustomMaskChar() throws SQLException {
        setupTestTable("test_char");
        String url = "jdbc:mask[columns=ssn,strategy=FULL,mask=X]:jdbc:h2:mem:test_char;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("XXXXXXXXXXX", rs.getString("ssn"));
                }
            }
        }
    }

    @Test
    public void testShowFirstAndLast() throws SQLException {
        setupTestTable("test_first_last");
        String url = "jdbc:mask[columns=credit_card,strategy=PARTIAL,showFirst=4,showLast=4]:jdbc:h2:mem:test_first_last;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT credit_card FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String masked = rs.getString("credit_card");
                    assertTrue(masked.startsWith("4111"));
                    assertTrue(masked.endsWith("1234"));
                    assertEquals(16, masked.length());
                }
            }
        }
    }

    @Test
    public void testNullValues() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_null;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE nulltest (id INT, data VARCHAR(100))");
                stmt.execute("INSERT INTO nulltest VALUES (1, NULL)");
            }
        }

        String url = "jdbc:mask[columns=data,strategy=FULL]:jdbc:h2:mem:test_null;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT data FROM nulltest WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertNull(rs.getString("data"));
                }
            }
        }
    }

    @Test
    public void testEmptyValues() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_empty;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE emptytest (id INT, data VARCHAR(100))");
                stmt.execute("INSERT INTO emptytest VALUES (1, '')");
            }
        }

        String url = "jdbc:mask[columns=data,strategy=FULL]:jdbc:h2:mem:test_empty;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT data FROM emptytest WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("", rs.getString("data"));
                }
            }
        }
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        setupTestTable("test_prepared");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_prepared;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT ssn FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("ssn"));
                }
            }
        }
    }

    @Test
    public void testGetObject() throws SQLException {
        setupTestTable("test_getobject");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getObject("ssn"));
                    assertEquals("[REDACTED]", rs.getObject(1));
                }
            }
        }
    }

    @Test
    public void testCaseInsensitiveColumnMatch() throws SQLException {
        setupTestTable("test_case");
        String url = "jdbc:mask[columns=SSN,strategy=REDACT]:jdbc:h2:mem:test_case;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("ssn"));
                }
            }
        }
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CatDriver");
        setupTestTable("test_chain");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:cat:jdbc:h2:mem:test_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("ssn"));
                }
            }
        }
    }

    @Test
    public void testConfigDefaults() {
        DataMaskingDriver.MaskingConfig config = new DataMaskingDriver.MaskingConfig("jdbc:mask:jdbc:h2:mem:test");
        assertEquals(DataMaskingDriver.MaskingStrategy.PARTIAL, config.getStrategy());
        assertEquals('*', config.getMaskChar());
        assertEquals(4, config.getShowLast());
        assertEquals(0, config.getShowFirst());
        assertTrue(config.getColumnPatterns().isEmpty());
    }

    @Test
    public void testMaskingStrategies() {
        // Test PARTIAL
        DataMaskingDriver.MaskingConfig partial = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=PARTIAL,showLast=4]:jdbc:h2:mem:test"
        );
        assertEquals("********1234", partial.maskValue("123456781234"));

        // Test FULL
        DataMaskingDriver.MaskingConfig full = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=FULL]:jdbc:h2:mem:test"
        );
        assertEquals("*****", full.maskValue("hello"));

        // Test EMAIL
        DataMaskingDriver.MaskingConfig email = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=EMAIL]:jdbc:h2:mem:test"
        );
        String maskedEmail = email.maskValue("john@example.com");
        assertTrue(maskedEmail.startsWith("j"));
        assertTrue(maskedEmail.endsWith("@example.com"));

        // Test REDACT
        DataMaskingDriver.MaskingConfig redact = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=REDACT]:jdbc:h2:mem:test"
        );
        assertEquals("[REDACTED]", redact.maskValue("anything"));

        // Test HASH
        DataMaskingDriver.MaskingConfig hash = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=HASH]:jdbc:h2:mem:test"
        );
        String hashed = hash.maskValue("secret");
        assertTrue(hashed.endsWith("..."));
    }

    @Test
    public void testShortValuePartialMasking() {
        DataMaskingDriver.MaskingConfig config = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=PARTIAL,showLast=4]:jdbc:h2:mem:test"
        );
        // Value shorter than showLast, should mask entirely
        assertEquals("**", config.maskValue("ab"));
    }

    @Test
    public void testMultipleRowsMasked() throws SQLException {
        setupTestTable("test_rows");
        String url = "jdbc:mask[columns=ssn,strategy=PARTIAL,showLast=4]:jdbc:h2:mem:test_rows;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users ORDER BY id")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getString("ssn").endsWith("6789"));
                    assertTrue(rs.next());
                    assertTrue(rs.getString("ssn").endsWith("4321"));
                }
            }
        }
    }
}
