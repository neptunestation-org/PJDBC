package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for DataMaskingDriver.
 *
 * These tests verify masking properties:
 * - FULL strategy replaces all characters
 * - PARTIAL strategy preserves first/last characters
 * - EMAIL strategy preserves structure
 * - REDACT returns constant value
 * - HASH is deterministic
 * - Null/empty values pass through
 * - Non-matching columns unchanged
 * - Driver composition
 */
class DataMaskingDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "mask_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private void setupTestTable(String dbName, String value) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE data (id INT PRIMARY KEY, secret VARCHAR(200), public_col VARCHAR(200))");
                // Use PreparedStatement to safely insert value
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO data VALUES (1, ?, ?)")) {
                    pstmt.setString(1, value);
                    pstmt.setString(2, value);
                    pstmt.executeUpdate();
                }
            }
        }
    }

    // ========== FULL STRATEGY ==========

    /**
     * Property: FULL strategy replaces all characters with mask char.
     * Output length equals input length.
     */
    @Property(tries = 50)
    void fullStrategyReplacesAllChars(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=FULL]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            assertEquals(value.length(), masked.length(),
                "FULL masking should preserve length");
            assertTrue(masked.chars().allMatch(c -> c == '*'),
                "FULL masking should replace all chars with mask char");
        }
    }

    /**
     * Property: FULL strategy with custom mask char uses that char.
     */
    @Property(tries = 30)
    void fullStrategyUsesCustomMaskChar(
            @ForAll("maskableStrings") String value,
            @ForAll("maskChars") char maskChar) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=FULL,mask=" + maskChar + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            assertEquals(value.length(), masked.length());
            assertTrue(masked.chars().allMatch(c -> c == maskChar),
                "Should use custom mask char: " + maskChar);
        }
    }

    // ========== PARTIAL STRATEGY ==========

    /**
     * Property: PARTIAL strategy preserves last N characters.
     */
    @Property(tries = 50)
    void partialStrategyPreservesLastChars(
            @ForAll("longStrings") String value,
            @ForAll @IntRange(min = 1, max = 4) int showLast) throws SQLException {

        Assume.that(value.length() > showLast);

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=PARTIAL,showLast=" + showLast + ",showFirst=0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            String expectedEnd = value.substring(value.length() - showLast);
            assertTrue(masked.endsWith(expectedEnd),
                "Should preserve last " + showLast + " chars: expected '" + expectedEnd + "' but got '" + masked + "'");
        }
    }

    /**
     * Property: PARTIAL strategy preserves first N characters.
     */
    @Property(tries = 50)
    void partialStrategyPreservesFirstChars(
            @ForAll("longStrings") String value,
            @ForAll @IntRange(min = 1, max = 4) int showFirst) throws SQLException {

        Assume.that(value.length() > showFirst);

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=PARTIAL,showFirst=" + showFirst + ",showLast=0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            String expectedStart = value.substring(0, showFirst);
            assertTrue(masked.startsWith(expectedStart),
                "Should preserve first " + showFirst + " chars: expected '" + expectedStart + "' but got '" + masked + "'");
        }
    }

    /**
     * Property: PARTIAL with both showFirst and showLast preserves both ends.
     */
    @Property(tries = 30)
    void partialPreservesBothEnds(
            @ForAll("longStrings") String value,
            @ForAll @IntRange(min = 1, max = 3) int showFirst,
            @ForAll @IntRange(min = 1, max = 3) int showLast) throws SQLException {

        Assume.that(value.length() > showFirst + showLast);

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=PARTIAL,showFirst=" + showFirst + ",showLast=" + showLast + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            assertTrue(masked.startsWith(value.substring(0, showFirst)),
                "Should preserve first " + showFirst + " chars");
            assertTrue(masked.endsWith(value.substring(value.length() - showLast)),
                "Should preserve last " + showLast + " chars");
            assertEquals(value.length(), masked.length(),
                "Total length should be preserved");
        }
    }

    // ========== EMAIL STRATEGY ==========

    /**
     * Property: EMAIL strategy preserves first character and domain.
     */
    @Property(tries = 50)
    void emailStrategyPreservesDomain(
            @ForAll("emails") String email) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, email);

        String maskUrl = "jdbc:mask[columns=secret,strategy=EMAIL]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            String masked = rs.getString("secret");

            int atIndex = email.indexOf('@');
            String domain = email.substring(atIndex);
            char firstChar = email.charAt(0);

            assertTrue(masked.startsWith(String.valueOf(firstChar)),
                "Should preserve first character: " + firstChar);
            assertTrue(masked.endsWith(domain),
                "Should preserve domain: " + domain);
            assertTrue(masked.contains("*"),
                "Should contain mask characters");
        }
    }

    // ========== REDACT STRATEGY ==========

    /**
     * Property: REDACT strategy always returns "[REDACTED]" regardless of input.
     */
    @Property(tries = 50)
    void redactReturnsConstant(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            assertEquals("[REDACTED]", rs.getString("secret"),
                "REDACT should always return [REDACTED]");
        }
    }

    // ========== HASH STRATEGY ==========

    /**
     * Property: HASH strategy is deterministic - same input produces same output.
     */
    @Property(tries = 50)
    void hashIsDeterministic(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=HASH]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        String firstHash;
        String secondHash;

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
                assertTrue(rs.next());
                firstHash = rs.getString("secret");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
                assertTrue(rs.next());
                secondHash = rs.getString("secret");
            }
        }

        assertEquals(firstHash, secondHash,
            "HASH should be deterministic - same input should produce same output");
        assertTrue(firstHash.endsWith("..."),
            "HASH output should end with ellipsis");
    }

    /**
     * Property: Different inputs produce different hashes (with high probability).
     */
    @Property(tries = 30)
    void hashDifferentInputsDifferentOutput(
            @ForAll("maskableStrings") String value1,
            @ForAll("maskableStrings") String value2) throws SQLException {

        Assume.that(!value1.equals(value2));

        // Use MaskingConfig directly to test hash
        DataMaskingDriver.MaskingConfig config = new DataMaskingDriver.MaskingConfig(
            "jdbc:mask[columns=test,strategy=HASH]:jdbc:h2:mem:test"
        );

        String hash1 = config.maskValue(value1);
        String hash2 = config.maskValue(value2);

        // Different inputs should produce different hashes (with very high probability)
        assertNotEquals(hash1, hash2,
            "Different inputs should produce different hashes");
    }

    // ========== NULL/EMPTY PRESERVATION ==========

    /**
     * Property: Null values remain null after masking.
     */
    @Property(tries = 20)
    void nullValuesStayNull(
            @ForAll("strategies") String strategy) throws SQLException {

        String dbName = createUniqueDbName();
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE nulltest (id INT, secret VARCHAR(100))");
                stmt.execute("INSERT INTO nulltest VALUES (1, NULL)");
            }
        }

        String maskUrl = "jdbc:mask[columns=secret,strategy=" + strategy + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM nulltest WHERE id = 1")) {

            assertTrue(rs.next());
            assertNull(rs.getString("secret"),
                "Null values should remain null with strategy " + strategy);
        }
    }

    /**
     * Property: Empty strings remain empty after masking.
     */
    @Property(tries = 20)
    void emptyStringsStayEmpty(
            @ForAll("strategies") String strategy) throws SQLException {

        String dbName = createUniqueDbName();
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE emptytest (id INT, secret VARCHAR(100))");
                stmt.execute("INSERT INTO emptytest VALUES (1, '')");
            }
        }

        String maskUrl = "jdbc:mask[columns=secret,strategy=" + strategy + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM emptytest WHERE id = 1")) {

            assertTrue(rs.next());
            assertEquals("", rs.getString("secret"),
                "Empty strings should remain empty with strategy " + strategy);
        }
    }

    // ========== NON-MATCHING COLUMNS ==========

    /**
     * Property: Columns not matching pattern pass through unchanged.
     */
    @Property(tries = 50)
    void nonMatchingColumnsUnchanged(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        // Mask 'secret' column but query 'public_col'
        String maskUrl = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT public_col FROM data WHERE id = 1")) {

            assertTrue(rs.next());
            assertEquals(value, rs.getString("public_col"),
                "Non-matching columns should pass through unchanged");
        }
    }

    /**
     * Property: Regex patterns correctly match column names.
     */
    @Property(tries = 30)
    void regexPatternMatching(
            @ForAll("shortStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE regextest (user_ssn VARCHAR(100), user_email VARCHAR(100), username VARCHAR(100))");
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO regextest VALUES (?, ?, ?)")) {
                    pstmt.setString(1, value);
                    pstmt.setString(2, value);
                    pstmt.setString(3, value);
                    pstmt.executeUpdate();
                }
            }
        }

        // Pattern matches user_ssn and user_email but NOT username
        String maskUrl = "jdbc:mask[columns=user_.*,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT user_ssn, user_email, username FROM regextest")) {

            assertTrue(rs.next());
            assertEquals("[REDACTED]", rs.getString("user_ssn"), "user_ssn should be masked");
            assertEquals("[REDACTED]", rs.getString("user_email"), "user_email should be masked");
            assertEquals(value, rs.getString("username"), "username should NOT be masked (pattern is user_.*)");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:mask:X behaves like mask:X (cat is identity).
     */
    @Property(tries = 30)
    void catMaskComposition(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String catMaskUrl = "jdbc:cat:jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        String maskResult;
        String catMaskResult;

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
            assertTrue(rs.next());
            maskResult = rs.getString("secret");
        }

        try (Connection conn = DriverManager.getConnection(catMaskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
            assertTrue(rs.next());
            catMaskResult = rs.getString("secret");
        }

        assertEquals(maskResult, catMaskResult,
            "cat:mask:X should behave like mask:X");
    }

    /**
     * Property: mask:cat:X behaves like mask:X.
     */
    @Property(tries = 30)
    void maskCatComposition(
            @ForAll("maskableStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, value);

        String maskUrl = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String maskCatUrl = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        String maskResult;
        String maskCatResult;

        try (Connection conn = DriverManager.getConnection(maskUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
            assertTrue(rs.next());
            maskResult = rs.getString("secret");
        }

        try (Connection conn = DriverManager.getConnection(maskCatUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secret FROM data WHERE id = 1")) {
            assertTrue(rs.next());
            maskCatResult = rs.getString("secret");
        }

        assertEquals(maskResult, maskCatResult,
            "mask:cat:X should behave like mask:X");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> maskableStrings() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> longStrings() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(8)
            .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> emails() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(2)
            .ofMaxLength(10)
            .map(local -> local + "@example.com");
    }

    @Provide
    Arbitrary<Character> maskChars() {
        return Arbitraries.of('*', '#', 'X', '-', '.');
    }

    @Provide
    Arbitrary<String> strategies() {
        return Arbitraries.of("FULL", "PARTIAL", "EMAIL", "REDACT", "HASH");
    }

    @Provide
    Arbitrary<String> shortStrings() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(15);
    }
}
