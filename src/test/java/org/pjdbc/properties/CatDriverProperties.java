package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CatDriver.
 *
 * CatDriver is the identity driver - it passes everything through unchanged.
 * These tests verify:
 * - Identity behavior (cat:X ≡ X)
 * - SQL passthrough
 * - Results passthrough
 * - Idempotence (cat:cat:X ≡ cat:X)
 * - Driver composition
 */
class CatDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "cat_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    @BeforeProperty
    void clearMockLogs() {
        MockDriver.clearLogs();
    }

    // ========== IDENTITY BEHAVIOR ==========

    /**
     * Property: cat:X behaves exactly like X (identity).
     */
    @Property(tries = 20)
    void catBehavesLikeUnderlying(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();

        // Direct H2 connection
        String h2Url = "jdbc:h2:mem:" + dbName + "_direct";
        int directResult;
        try (Connection conn = DriverManager.getConnection(h2Url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            directResult = rs.getInt(1);
        }

        // Through cat driver
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName + "_cat";
        int catResult;
        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            catResult = rs.getInt(1);
        }

        assertEquals(directResult, catResult,
            "cat:X should behave exactly like X");
        assertEquals(value, catResult);
    }

    /**
     * Property: cat driver does not modify connection behavior.
     */
    @Property(tries = 10)
    void catDoesNotModifyBehavior(
            @ForAll @IntRange(min = 1, max = 5) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement()) {

            // Create table
            stmt.execute("CREATE TABLE test_cat (id INT, val VARCHAR(50))");

            // Insert rows
            for (int i = 0; i < operationCount; i++) {
                int rows = stmt.executeUpdate("INSERT INTO test_cat VALUES (" + i + ", 'val" + i + "')");
                assertEquals(1, rows, "Insert should affect 1 row");
            }

            // Query count
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_cat")) {
                assertTrue(rs.next());
                assertEquals(operationCount, rs.getInt(1),
                    "All inserts should be visible");
            }
        }
    }

    // ========== SQL PASSTHROUGH ==========

    /**
     * Property: SQL passes through unchanged to underlying driver.
     */
    @Property(tries = 20)
    void sqlPassesThroughUnchanged(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catUrl = "jdbc:cat:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "MockDriver should receive SQL");
        assertTrue(mockLog.contains(sql),
            "SQL should pass through unchanged: " + sql);
    }

    /**
     * Property: Multiple SQL statements all pass through.
     */
    @Property(tries = 10)
    void multipleSqlStatementsPassThrough(
            @ForAll @IntRange(min = 2, max = 5) int statementCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catUrl = "jdbc:cat:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < statementCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog);

        // All statements should be in the log
        for (int i = 0; i < statementCount; i++) {
            assertTrue(mockLog.contains("SELECT " + i),
                "Statement " + i + " should pass through");
        }
    }

    // ========== RESULTS PASSTHROUGH ==========

    /**
     * Property: Query results pass through unchanged.
     */
    @Property(tries = 20)
    void queryResultsPassThrough(
            @ForAll @IntRange(min = 1, max = 1000) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value + " AS num")) {

            assertTrue(rs.next(), "Should have result");
            assertEquals(value, rs.getInt("num"),
                "Query result should pass through unchanged");
            assertFalse(rs.next(), "Should have only one row");
        }
    }

    /**
     * Property: Update counts pass through unchanged.
     */
    @Property(tries = 10)
    void updateCountsPassThrough(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE test_updates (id INT)");

            // Insert rows
            for (int i = 0; i < rowCount; i++) {
                stmt.executeUpdate("INSERT INTO test_updates VALUES (" + i + ")");
            }

            // Update all rows
            int updated = stmt.executeUpdate("UPDATE test_updates SET id = id + 100");
            assertEquals(rowCount, updated,
                "Update count should pass through unchanged");

            // Delete all rows
            int deleted = stmt.executeUpdate("DELETE FROM test_updates");
            assertEquals(rowCount, deleted,
                "Delete count should pass through unchanged");
        }
    }

    /**
     * Property: String results pass through unchanged.
     */
    @Property(tries = 10)
    void stringResultsPassThrough(
            @ForAll("testStrings") String value) throws SQLException {

        String dbName = createUniqueDbName();
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT '" + value + "' AS str")) {

            assertTrue(rs.next());
            assertEquals(value, rs.getString("str"),
                "String result should pass through unchanged");
        }
    }

    // ========== IDEMPOTENCE ==========

    /**
     * Property: cat:cat:X behaves the same as cat:X (idempotence).
     */
    @Property(tries = 10)
    void catCatEquivalentToCat(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();

        // Single cat
        String catUrl = "jdbc:cat:jdbc:h2:mem:" + dbName + "_single";
        int singleCatResult;
        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            singleCatResult = rs.getInt(1);
        }

        // Double cat
        String catCatUrl = "jdbc:cat:jdbc:cat:jdbc:h2:mem:" + dbName + "_double";
        int doubleCatResult;
        try (Connection conn = DriverManager.getConnection(catCatUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            doubleCatResult = rs.getInt(1);
        }

        assertEquals(singleCatResult, doubleCatResult,
            "cat:cat:X should behave the same as cat:X");
        assertEquals(value, doubleCatResult);
    }

    /**
     * Property: Multiple cats are equivalent (cat:cat:cat:X ≡ cat:X).
     */
    @Property(tries = 10)
    void multipleCatsEquivalent(
            @ForAll @IntRange(min = 1, max = 50) int value) throws SQLException {

        String dbName = createUniqueDbName();

        // Triple cat
        String tripleCatUrl = "jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:" + dbName;

        try (Connection conn = DriverManager.getConnection(tripleCatUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Multiple cats should still pass through correctly");
        }
    }

    /**
     * Property: cat:cat produces same MockDriver log as cat.
     */
    @Property(tries = 10)
    void catCatSameBehaviorAsCat(
            @ForAll("sqlStatements") String sql) throws SQLException {

        // Single cat
        String dbName1 = createUniqueDbName();
        String mockUrl1 = "jdbc:mock:" + dbName1;
        String catUrl = "jdbc:cat:" + mockUrl1;

        try (Connection conn = DriverManager.getConnection(catUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }
        String singleCatLog = MockDriver.getLog(mockUrl1);

        // Double cat
        String dbName2 = createUniqueDbName();
        String mockUrl2 = "jdbc:mock:" + dbName2;
        String catCatUrl = "jdbc:cat:jdbc:cat:" + mockUrl2;

        try (Connection conn = DriverManager.getConnection(catCatUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }
        String doubleCatLog = MockDriver.getLog(mockUrl2);

        assertEquals(singleCatLog, doubleCatLog,
            "cat:cat should produce same underlying behavior as cat");
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:log:X works correctly.
     */
    @Property(tries = 10)
    void catLogComposition(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catLogUrl = "jdbc:cat:jdbc:log:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(catLogUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "cat:log should pass through to underlying");

        for (int i = 0; i < queryCount; i++) {
            assertTrue(mockLog.contains("SELECT " + i),
                "Query " + i + " should pass through cat:log");
        }
    }

    /**
     * Property: log:cat:X works correctly.
     */
    @Property(tries = 10)
    void logCatComposition(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String logCatUrl = "jdbc:log:jdbc:cat:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(logCatUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "log:cat should pass through to underlying");

        for (int i = 0; i < queryCount; i++) {
            assertTrue(mockLog.contains("SELECT " + i),
                "Query " + i + " should pass through log:cat");
        }
    }

    /**
     * Property: cat:mock:X works correctly.
     */
    @Property(tries = 10)
    void catMockComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catMockUrl = "jdbc:cat:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(catMockUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertNotNull(mockLog, "cat:mock should work");
        assertTrue(mockLog.contains(sql),
            "SQL should reach MockDriver through cat");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> sqlStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "SELECT id, name FROM test",
            "SELECT COUNT(*) FROM t"
        );
    }

    @Provide
    Arbitrary<String> testStrings() {
        return Arbitraries.of(
            "hello",
            "world",
            "test123",
            "foo bar",
            "UPPER"
        );
    }
}
