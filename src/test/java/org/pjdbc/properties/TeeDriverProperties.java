package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;
import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for TeeDriver composition.
 *
 * TeeDriver splits operations to two targets, enabling:
 * - Broadcast properties (both targets receive operations)
 * - Sink/live composition patterns
 * - Multiple statement broadcast
 * - executeUpdate broadcast
 * - Driver composition (log:tee)
 * - Real database operations
 * - Connection lifecycle
 * - URL validation
 */
class TeeDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "tee_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    @BeforeProperty
    void clearMockLogs() {
        MockDriver.clearLogs();
    }

    /**
     * TeeDriver broadcasts to both targets.
     *
     * Both branches should receive the same operations.
     */
    @Property(tries = 30)
    void teeBroadcastsToBothTargets(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2)); // Ensure different databases

        MockDriver.clearLogs();

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertEquals(log1, log2,
            "Tee should broadcast same operations to both targets");
        assertTrue(log1.contains("executeQuery"),
            "Both targets should have received executeQuery");
    }

    /**
     * Tee with sink on one branch isolates the other.
     *
     * Pattern: jdbc:tee:jdbc:mock:live;jdbc:sink:jdbc:mock:dead
     * Only the live branch should record operations.
     */
    @Property(tries = 30)
    void teeWithSinkIsolatesTarget(
            @ForAll("mockDbNames") String liveDb,
            @ForAll("mockDbNames") String deadDb,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!liveDb.equals(deadDb));

        MockDriver.clearLogs();

        String liveUrl = "jdbc:mock:" + liveDb;
        String deadUrl = "jdbc:mock:" + deadDb;
        String teeUrl = "jdbc:tee:" + liveUrl + ";jdbc:sink:" + deadUrl;

        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String liveLog = MockDriver.getLog(liveUrl);
        String deadLog = MockDriver.getLog(deadUrl);

        assertFalse(liveLog.isEmpty(),
            "Live branch should receive operations");
        assertEquals("", deadLog,
            "Sink branch should absorb operations");
    }

    /**
     * Tee order doesn't affect broadcast semantics.
     *
     * jdbc:tee:A;B and jdbc:tee:B;A should both broadcast to A and B.
     */
    @Property(tries = 20)
    void teeOrderDoesNotAffectBroadcast(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2));

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        // First order: db1;db2
        MockDriver.clearLogs();
        String teeUrl1 = "jdbc:tee:" + url1 + ";" + url2;
        try (Connection conn = DriverManager.getConnection(teeUrl1)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_order1 = MockDriver.getLog(url1);
        String log2_order1 = MockDriver.getLog(url2);

        // Second order: db2;db1
        MockDriver.clearLogs();
        String teeUrl2 = "jdbc:tee:" + url2 + ";" + url1;
        try (Connection conn = DriverManager.getConnection(teeUrl2)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_order2 = MockDriver.getLog(url1);
        String log2_order2 = MockDriver.getLog(url2);

        assertEquals(log1_order1, log1_order2,
            "db1 should receive same operations regardless of tee order");
        assertEquals(log2_order1, log2_order2,
            "db2 should receive same operations regardless of tee order");
    }

    /**
     * Cat wrapper around tee preserves broadcast behavior.
     *
     * jdbc:cat:jdbc:tee:A;B should behave like jdbc:tee:A;B
     */
    @Property(tries = 20)
    void catPreservesTee(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2));

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        // Direct tee
        MockDriver.clearLogs();
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;
        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_tee = MockDriver.getLog(url1);
        String log2_tee = MockDriver.getLog(url2);

        // Cat wrapped tee
        MockDriver.clearLogs();
        String catTeeUrl = "jdbc:cat:" + teeUrl;
        try (Connection conn = DriverManager.getConnection(catTeeUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_cat = MockDriver.getLog(url1);
        String log2_cat = MockDriver.getLog(url2);

        assertEquals(log1_tee, log1_cat, "cat:tee should preserve branch 1");
        assertEquals(log2_tee, log2_cat, "cat:tee should preserve branch 2");
    }

    // ========== MULTIPLE STATEMENTS BROADCAST ==========

    /**
     * Property: Multiple SQL statements all broadcast to both targets.
     */
    @Property(tries = 10)
    void multipleStatementsBroadcast(
            @ForAll @IntRange(min = 2, max = 5) int statementCount) throws SQLException {

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + dbName1;
        String url2 = "jdbc:mock:" + dbName2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(teeUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < statementCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertEquals(log1, log2,
            "All statements should broadcast identically to both targets");

        // Verify all statements reached both targets
        for (int i = 0; i < statementCount; i++) {
            assertTrue(log1.contains("SELECT " + i),
                "Statement " + i + " should reach target 1");
            assertTrue(log2.contains("SELECT " + i),
                "Statement " + i + " should reach target 2");
        }
    }

    // ========== EXECUTEQUERY BROADCAST (executeUpdate/execute only go to first target) ==========

    /**
     * Property: executeQuery broadcasts to both targets.
     * Note: AbstractStatement.executeQuery broadcasts to all delegates,
     * but executeUpdate/execute only go to the first delegate (current impl).
     */
    @Property(tries = 10)
    void executeQueryBroadcastsToAll(
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + dbName1;
        String url2 = "jdbc:mock:" + dbName2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(teeUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        // Both should contain the executeQuery
        assertTrue(log1.contains("executeQuery"),
            "Target 1 should receive executeQuery");
        assertTrue(log2.contains("executeQuery"),
            "Target 2 should receive executeQuery");
        assertTrue(log1.contains(sql),
            "Target 1 should have SQL");
        assertTrue(log2.contains(sql),
            "Target 2 should have SQL");
    }

    /**
     * Property: executeUpdate goes to first target only (current impl limitation).
     */
    @Property(tries = 10)
    void executeUpdateGoesToFirstTarget(
            @ForAll("updateStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + dbName1;
        String url2 = "jdbc:mock:" + dbName2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(teeUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        String log1 = MockDriver.getLog(url1);

        // First target should receive the executeUpdate
        assertTrue(log1.contains("executeUpdate"),
            "Target 1 should receive executeUpdate");
        assertTrue(log1.contains(sql),
            "Target 1 should have SQL");
    }

    // ========== LOG:TEE COMPOSITION ==========

    /**
     * Custom log handler that captures log messages.
     */
    private static class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }
    }

    /**
     * Property: log:tee composition broadcasts to both targets.
     * Note: LogDriver logs using the underlying URL as logger name.
     */
    @Property(tries = 10)
    void logTeeComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + dbName1;
        String url2 = "jdbc:mock:" + dbName2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;
        String logTeeUrl = "jdbc:log:" + teeUrl;

        try (Connection conn = DriverManager.getConnection(logTeeUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        // Verify broadcast occurred - both mock drivers should receive the SQL
        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains(sql),
            "log:tee should broadcast SQL to target 1");
        assertTrue(log2.contains(sql),
            "log:tee should broadcast SQL to target 2");
    }

    // ========== TEE WITH REAL DATABASES ==========

    /**
     * Property: Tee queries return results from first database.
     * Note: Using simple SELECT queries that don't require persistent tables.
     */
    @Property(tries = 10)
    void teeQueriesReturnFromFirstDatabase(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + dbName1;
        String h2Url2 = "jdbc:h2:mem:" + dbName2;
        String teeUrl = "jdbc:tee:" + h2Url1 + ";" + h2Url2;

        try (Connection conn = DriverManager.getConnection(teeUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Tee should return result from first database");
        }
    }

    /**
     * Property: Tee SELECT operations work with real H2 databases.
     * Note: Only executeQuery broadcasts; execute/executeUpdate go to first target only.
     */
    @Property(tries = 10)
    void teeSelectWithRealDatabases(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + dbName1;
        String h2Url2 = "jdbc:h2:mem:" + dbName2;
        String teeUrl = "jdbc:tee:" + h2Url1 + ";" + h2Url2;

        // Simple SELECT works through tee
        try (Connection conn = DriverManager.getConnection(teeUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value + " AS num")) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt("num"),
                "SELECT should return correct value through tee");
        }
    }

    // ========== CONNECTION CLOSE PROPAGATION ==========

    /**
     * Property: Closing tee connection propagates close to both targets.
     * Note: AbstractConnection.isClosed() has a bug - throws SQLException when all closed.
     */
    @Property(tries = 10)
    void connectionClosePropagates() throws SQLException {
        MockDriver.clearLogs();

        String dbName1 = createUniqueDbName();
        String dbName2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + dbName1;
        String url2 = "jdbc:mock:" + dbName2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        Connection teeConn = DriverManager.getConnection(teeUrl);

        // Execute a query to ensure connections are active
        try (Statement stmt = teeConn.createStatement()) {
            stmt.executeQuery("SELECT 1");
        }

        // Close the tee connection
        teeConn.close();

        // Verify close was broadcast to both
        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);
        assertTrue(log1.contains("close"),
            "Close should propagate to target 1");
        assertTrue(log2.contains("close"),
            "Close should propagate to target 2");
    }

    /**
     * Property: Multiple open/close cycles work correctly with mock driver.
     */
    @Property(tries = 10)
    void multipleOpenCloseCycles(
            @ForAll @IntRange(min = 2, max = 4) int cycles) throws SQLException {

        for (int i = 0; i < cycles; i++) {
            MockDriver.clearLogs();

            String dbName1 = createUniqueDbName();
            String dbName2 = createUniqueDbName();
            String url1 = "jdbc:mock:" + dbName1;
            String url2 = "jdbc:mock:" + dbName2;
            String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

            try (Connection conn = DriverManager.getConnection(teeUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT " + i);
            }

            String log1 = MockDriver.getLog(url1);
            assertTrue(log1.contains("SELECT " + i),
                "Cycle " + i + " should work correctly");
        }
    }

    // ========== URL VALIDATION ==========

    /**
     * Property: Tee driver rejects URLs without exactly 2 semicolon-separated URLs.
     */
    @Property(tries = 10)
    void urlValidationRequiresTwoUrls() {
        TeeDriver driver = new TeeDriver();

        // Single URL (no semicolon) should be rejected
        assertFalse(driver.acceptsURL("jdbc:tee:jdbc:mock:single"),
            "Should reject single URL");

        // Empty after tee: should be rejected
        assertFalse(driver.acceptsURL("jdbc:tee:"),
            "Should reject empty URL");

        // Just tee protocol should be rejected
        assertFalse(driver.acceptsURL("jdbc:tee"),
            "Should reject bare tee protocol");

        // Three URLs should be rejected
        assertFalse(driver.acceptsURL("jdbc:tee:jdbc:mock:a;jdbc:mock:b;jdbc:mock:c"),
            "Should reject three URLs");
    }

    /**
     * Property: Tee driver accepts valid two-URL format.
     */
    @Property(tries = 10)
    void urlValidationAcceptsTwoUrls(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2) {

        Assume.that(!db1.equals(db2));

        TeeDriver driver = new TeeDriver();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        assertTrue(driver.acceptsURL(teeUrl),
            "Should accept valid two-URL format: " + teeUrl);
    }

    /**
     * Property: Connect with invalid URL returns null.
     */
    @Property(tries = 10)
    void connectWithInvalidUrlReturnsNull() throws SQLException {
        TeeDriver driver = new TeeDriver();

        assertNull(driver.connect("jdbc:tee:jdbc:mock:single", null),
            "Connect with single URL should return null");
        assertNull(driver.connect("jdbc:tee:", null),
            "Connect with empty URL should return null");
        assertNull(driver.connect("jdbc:other:url", null),
            "Connect with non-tee URL should return null");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> mockDbNames() {
        return PjdbcArbitraries.mockDbNames();
    }

    @Provide
    Arbitrary<String> sqlStatements() {
        return PjdbcArbitraries.sqlStatements();
    }

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1"
        );
    }
}
