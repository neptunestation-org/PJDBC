package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ChaosDriver.
 *
 * These tests verify chaos injection properties:
 * - Failure rate probability
 * - Latency injection
 * - Latency variance
 * - Connection drop behavior
 * - ResultSet iteration latency
 * - Custom exception messages
 * - Zero/default parameter behavior
 * - Driver composition
 */
class ChaosDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "chaos_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private void setupTestTable(String dbName) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
                stmt.execute("INSERT INTO users VALUES (3, 'Charlie')");
            }
        }
    }

    // ========== FAILURE RATE PROBABILITY ==========

    /**
     * Property: failureRate=1.0 always throws SQLException.
     */
    @Property(tries = 20)
    void failureRateOneAlwaysFails(
            @ForAll @IntRange(min = 1, max = 5) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int failures = 0;
        for (int i = 0; i < attempts; i++) {
            try (Connection conn = DriverManager.getConnection(chaosUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            } catch (SQLException e) {
                if (e.getMessage().contains("ChaosDriver")) {
                    failures++;
                }
            }
        }

        assertEquals(attempts, failures,
            "failureRate=1.0 should always fail");
    }

    /**
     * Property: failureRate=0.0 never throws SQLException.
     */
    @Property(tries = 20)
    void failureRateZeroNeverFails(
            @ForAll @IntRange(min = 5, max = 20) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[failureRate=0.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int successes = 0;
        try (Connection conn = DriverManager.getConnection(chaosUrl)) {
            for (int i = 0; i < attempts; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    successes++;
                }
            }
        }

        assertEquals(attempts, successes,
            "failureRate=0.0 should never fail");
    }

    /**
     * Property: Failure rate applies to all operation types (query, update, execute).
     */
    @Property(tries = 10)
    void failureRateAffectsAllOperations() throws SQLException {
        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        // Test executeQuery
        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeQuery("SELECT * FROM users"),
                "executeQuery should fail with failureRate=1.0");
        }

        // Test executeUpdate
        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeUpdate("UPDATE users SET name='X' WHERE id=1"),
                "executeUpdate should fail with failureRate=1.0");
        }

        // Test execute
        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.execute("SELECT 1"),
                "execute should fail with failureRate=1.0");
        }
    }

    // ========== LATENCY INJECTION ==========

    /**
     * Property: latency parameter adds at least the configured delay.
     */
    @Property(tries = 10)
    void latencyAddsFixedDelay(
            @ForAll @IntRange(min = 50, max = 150) int latencyMs) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[latency=" + latencyMs + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            long start = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                // consume
            }
            long duration = System.currentTimeMillis() - start;

            assertTrue(duration >= latencyMs - 10, // 10ms tolerance for timing
                "Duration " + duration + "ms should be at least " + latencyMs + "ms");
        }
    }

    /**
     * Property: Each operation incurs the latency independently.
     */
    @Property(tries = 10)
    void latencyAppliesPerOperation(
            @ForAll @IntRange(min = 2, max = 4) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        int latencyMs = 50;
        String chaosUrl = "jdbc:chaos[latency=" + latencyMs + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            long start = System.currentTimeMillis();
            for (int i = 0; i < operationCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    // consume
                }
            }
            long duration = System.currentTimeMillis() - start;

            int expectedMinLatency = latencyMs * operationCount - 20; // tolerance
            assertTrue(duration >= expectedMinLatency,
                "Duration " + duration + "ms should be at least " + expectedMinLatency + "ms for " + operationCount + " operations");
        }
    }

    // ========== LATENCY VARIANCE ==========

    /**
     * Property: Total delay is at least base latency when variance is added.
     */
    @Property(tries = 10)
    void latencyVarianceAtLeastBase(
            @ForAll @IntRange(min = 50, max = 100) int baseLatency,
            @ForAll @IntRange(min = 10, max = 50) int variance) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[latency=" + baseLatency + ",latencyVariance=" + variance + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            long start = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                // consume
            }
            long duration = System.currentTimeMillis() - start;

            assertTrue(duration >= baseLatency - 10, // 10ms tolerance
                "Duration " + duration + "ms should be at least base latency " + baseLatency + "ms");
        }
    }

    /**
     * Property: Total delay is at most base + variance.
     */
    @Property(tries = 10)
    void latencyVarianceWithinBounds(
            @ForAll @IntRange(min = 50, max = 100) int baseLatency,
            @ForAll @IntRange(min = 20, max = 50) int variance) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[latency=" + baseLatency + ",latencyVariance=" + variance + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            long start = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                // consume
            }
            long duration = System.currentTimeMillis() - start;

            int maxExpected = baseLatency + variance + 50; // 50ms overhead tolerance
            assertTrue(duration <= maxExpected,
                "Duration " + duration + "ms should be at most " + maxExpected + "ms");
        }
    }

    // ========== CONNECTION DROP ==========

    /**
     * Property: connectionDropRate=1.0 always drops connection.
     */
    @Property(tries = 10)
    void connectionDropRateOneAlwaysDrops(
            @ForAll @IntRange(min = 1, max = 3) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[connectionDropRate=1.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int drops = 0;
        for (int i = 0; i < attempts; i++) {
            try (Connection conn = DriverManager.getConnection(chaosUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            } catch (SQLException e) {
                if (e.getMessage().contains("Connection dropped")) {
                    drops++;
                }
            }
        }

        assertEquals(attempts, drops,
            "connectionDropRate=1.0 should always drop");
    }

    /**
     * Property: connectionDropRate=0.0 never drops connection.
     */
    @Property(tries = 10)
    void connectionDropRateZeroNeverDrops(
            @ForAll @IntRange(min = 5, max = 10) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[connectionDropRate=0.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int successes = 0;
        try (Connection conn = DriverManager.getConnection(chaosUrl)) {
            for (int i = 0; i < attempts; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    successes++;
                }
            }
        }

        assertEquals(attempts, successes,
            "connectionDropRate=0.0 should never drop");
    }

    // ========== RESULTSET ITERATION LATENCY ==========

    /**
     * Property: resultSetLatency adds delay per next() call.
     */
    @Property(tries = 10)
    void resultSetLatencyAddsDelayPerNext(
            @ForAll @IntRange(min = 30, max = 60) int rsLatency) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[resultSetLatency=" + rsLatency + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            long start = System.currentTimeMillis();
            int rowCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    rowCount++;
                }
            }
            long duration = System.currentTimeMillis() - start;

            // rowCount rows + 1 final next() that returns false = rowCount + 1 calls
            int expectedCalls = rowCount + 1;
            int expectedMinDelay = expectedCalls * rsLatency - 20; // tolerance

            assertTrue(duration >= expectedMinDelay,
                "Duration " + duration + "ms should be at least " + expectedMinDelay + "ms for " + expectedCalls + " next() calls");
        }
    }

    /**
     * Property: More rows means more total delay from resultSetLatency.
     */
    @Property(tries = 10)
    void resultSetLatencyScalesWithRowCount() throws SQLException {
        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        int rsLatency = 40;
        String chaosUrl = "jdbc:chaos[resultSetLatency=" + rsLatency + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {

            // Query for 1 row
            long start1 = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                while (rs.next()) {}
            }
            long duration1 = System.currentTimeMillis() - start1;

            // Query for 3 rows
            long start3 = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {}
            }
            long duration3 = System.currentTimeMillis() - start3;

            // 3-row query should take longer than 1-row query
            assertTrue(duration3 > duration1,
                "3-row query (" + duration3 + "ms) should take longer than 1-row query (" + duration1 + "ms)");
        }
    }

    // ========== CUSTOM EXCEPTION MESSAGE ==========

    /**
     * Property: Custom exceptionMessage is used in SQLException.
     */
    @Property(tries = 10)
    void customExceptionMessageUsed(
            @ForAll("exceptionMessages") String message) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[failureRate=1.0,exceptionMessage=" + message + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM users");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertEquals(message, e.getMessage(),
                "Exception message should match configured message");
        }
    }

    /**
     * Property: Default exception message is used when not specified.
     */
    @Property(tries = 10)
    void defaultExceptionMessageUsed() throws SQLException {
        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosUrl = "jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM users");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertEquals("ChaosDriver: Induced failure", e.getMessage(),
                "Default exception message should be 'ChaosDriver: Induced failure'");
        }
    }

    // ========== ZERO/DEFAULT PARAMETERS ==========

    /**
     * Property: Default configuration (no params) means no chaos.
     */
    @Property(tries = 20)
    void defaultConfigNoFailures(
            @ForAll @IntRange(min = 5, max = 15) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        // No chaos parameters at all
        String chaosUrl = "jdbc:chaos:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int successes = 0;
        try (Connection conn = DriverManager.getConnection(chaosUrl)) {
            for (int i = 0; i < attempts; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    while (rs.next()) {
                        // consume
                    }
                    successes++;
                }
            }
        }

        assertEquals(attempts, successes,
            "Default config should have no failures");
    }

    /**
     * Property: Invalid parameter values fall back to defaults (no chaos).
     */
    @Property(tries = 10)
    void invalidParametersFallBackToDefaults(
            @ForAll @IntRange(min = 5, max = 10) int attempts) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        // Invalid values should parse as 0
        String chaosUrl = "jdbc:chaos[failureRate=invalid,latency=abc]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int successes = 0;
        try (Connection conn = DriverManager.getConnection(chaosUrl)) {
            for (int i = 0; i < attempts; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                    successes++;
                }
            }
        }

        assertEquals(attempts, successes,
            "Invalid params should fall back to defaults (no chaos)");
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:chaos:X preserves chaos behavior.
     */
    @Property(tries = 10)
    void catChaosComposition() throws SQLException {
        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String catChaosUrl = "jdbc:cat:jdbc:chaos[failureRate=1.0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(catChaosUrl);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.executeQuery("SELECT * FROM users"),
                "cat:chaos should preserve failure behavior");
            assertTrue(ex.getMessage().contains("ChaosDriver"),
                "Exception should be from ChaosDriver");
        }
    }

    /**
     * Property: chaos:cat:X preserves chaos behavior.
     */
    @Property(tries = 10)
    void chaosCatComposition() throws SQLException {
        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String chaosCatUrl = "jdbc:chaos[failureRate=1.0]:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(chaosCatUrl);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.executeQuery("SELECT * FROM users"),
                "chaos:cat should preserve failure behavior");
            assertTrue(ex.getMessage().contains("ChaosDriver"),
                "Exception should be from ChaosDriver");
        }
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> exceptionMessages() {
        return Arbitraries.of(
            "Custom error",
            "Test failure",
            "Simulated outage",
            "Database unavailable",
            "Connection timeout"
        );
    }
}
