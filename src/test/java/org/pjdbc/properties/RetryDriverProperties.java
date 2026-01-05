package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for RetryDriver.
 *
 * These tests verify retry properties:
 * - Exponential backoff calculation
 * - maxDelay capping
 * - Jitter bounds
 * - SQL state recognition
 * - Non-retryable errors
 * - Successful queries execute once
 * - Driver composition
 */
class RetryDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "retry_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== EXPONENTIAL BACKOFF CALCULATION ==========

    /**
     * Property: Delay follows exponential backoff formula.
     * delay = initialDelay * backoffMultiplier^attempt (without jitter, before capping)
     */
    @Property(tries = 100)
    void exponentialBackoffCalculation(
            @ForAll @IntRange(min = 50, max = 200) int initialDelay,
            @ForAll @DoubleRange(min = 1.5, max = 2.5) double multiplier,
            @ForAll @IntRange(min = 0, max = 3) int attempt) {

        // Use very large maxDelay to avoid capping
        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",backoffMultiplier=" + multiplier +
                     ",maxDelay=1000000,jitter=false]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        long expectedDelay = (long) (initialDelay * Math.pow(multiplier, attempt));
        long actualDelay = config.calculateDelay(attempt);

        assertEquals(expectedDelay, actualDelay,
            "Delay should be initialDelay * multiplier^attempt: " +
            initialDelay + " * " + multiplier + "^" + attempt + " = " + expectedDelay);
    }

    /**
     * Property: Each successive delay is larger than the previous (monotonic increase).
     */
    @Property(tries = 50)
    void delaysIncreaseMonotonically(
            @ForAll @IntRange(min = 50, max = 200) int initialDelay,
            @ForAll @DoubleRange(min = 1.1, max = 3.0) double multiplier) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",backoffMultiplier=" + multiplier +
                     ",maxDelay=100000,jitter=false]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        long prevDelay = 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            long delay = config.calculateDelay(attempt);
            assertTrue(delay > prevDelay,
                "Delay should increase: attempt " + attempt +
                " delay " + delay + " should be > " + prevDelay);
            prevDelay = delay;
        }
    }

    // ========== MAX DELAY CAPPING ==========

    /**
     * Property: Delay never exceeds maxDelay regardless of attempt number.
     */
    @Property(tries = 100)
    void delayNeverExceedsMaxDelay(
            @ForAll @IntRange(min = 100, max = 500) int initialDelay,
            @ForAll @IntRange(min = 500, max = 2000) int maxDelay,
            @ForAll @DoubleRange(min = 2.0, max = 4.0) double multiplier,
            @ForAll @IntRange(min = 0, max = 20) int attempt) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",maxDelay=" + maxDelay +
                     ",backoffMultiplier=" + multiplier +
                     ",jitter=false]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);
        long delay = config.calculateDelay(attempt);

        assertTrue(delay <= maxDelay,
            "Delay " + delay + " should not exceed maxDelay " + maxDelay +
            " at attempt " + attempt);
    }

    /**
     * Property: After enough attempts, delay stabilizes at maxDelay.
     */
    @Property(tries = 50)
    void delayStabilizesAtMaxDelay(
            @ForAll @IntRange(min = 100, max = 200) int initialDelay,
            @ForAll @IntRange(min = 500, max = 1000) int maxDelay,
            @ForAll @DoubleRange(min = 2.0, max = 3.0) double multiplier) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",maxDelay=" + maxDelay +
                     ",backoffMultiplier=" + multiplier +
                     ",jitter=false]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        // After many attempts, should be at maxDelay
        long delay10 = config.calculateDelay(10);
        long delay15 = config.calculateDelay(15);
        long delay20 = config.calculateDelay(20);

        assertEquals(maxDelay, delay10, "Delay at attempt 10 should be maxDelay");
        assertEquals(maxDelay, delay15, "Delay at attempt 15 should be maxDelay");
        assertEquals(maxDelay, delay20, "Delay at attempt 20 should be maxDelay");
    }

    // ========== JITTER BOUNDS ==========

    /**
     * Property: With jitter=false, delays are exact (deterministic).
     */
    @Property(tries = 50)
    void noJitterProducesExactDelays(
            @ForAll @IntRange(min = 100, max = 500) int initialDelay,
            @ForAll @IntRange(min = 0, max = 5) int attempt) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",jitter=false,maxDelay=100000]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        // Multiple calls should return same value
        long delay1 = config.calculateDelay(attempt);
        long delay2 = config.calculateDelay(attempt);
        long delay3 = config.calculateDelay(attempt);

        assertEquals(delay1, delay2, "Without jitter, delays should be deterministic");
        assertEquals(delay2, delay3, "Without jitter, delays should be deterministic");
    }

    /**
     * Property: With jitter=true, delay is at least base delay.
     */
    @Property(tries = 100)
    void jitterDelayAtLeastBaseDelay(
            @ForAll @IntRange(min = 100, max = 500) int initialDelay,
            @ForAll @IntRange(min = 0, max = 5) int attempt) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",jitter=true,maxDelay=100000]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        long baseDelay = (long) (initialDelay * Math.pow(2.0, attempt));
        long actualDelay = config.calculateDelay(attempt);

        assertTrue(actualDelay >= baseDelay,
            "Jitter delay " + actualDelay + " should be >= base delay " + baseDelay);
    }

    /**
     * Property: With jitter=true, delay is at most base delay + 25%.
     */
    @Property(tries = 100)
    void jitterDelayAtMostBasePlusTwentyFivePercent(
            @ForAll @IntRange(min = 100, max = 500) int initialDelay,
            @ForAll @IntRange(min = 0, max = 5) int attempt) {

        String url = "jdbc:retry[initialDelay=" + initialDelay +
                     ",jitter=true,maxDelay=100000]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        long baseDelay = (long) (initialDelay * Math.pow(2.0, attempt));
        long maxWithJitter = baseDelay + (baseDelay / 4); // base + 25%
        long actualDelay = config.calculateDelay(attempt);

        assertTrue(actualDelay <= maxWithJitter,
            "Jitter delay " + actualDelay + " should be <= " + maxWithJitter +
            " (base " + baseDelay + " + 25%)");
    }

    // ========== SQL STATE RECOGNITION ==========

    /**
     * Property: Default transient SQL states are recognized as retryable.
     */
    @Property(tries = 30)
    void defaultTransientStatesAreRetryable(
            @ForAll("defaultRetryableStates") String sqlState) {

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry:jdbc:h2:mem:test"
        );

        SQLException exception = new SQLException("Test error", sqlState);
        assertTrue(config.isRetryable(exception),
            "Default state " + sqlState + " should be retryable");
    }

    /**
     * Property: Custom SQL states override defaults and are recognized.
     */
    @Property(tries = 30)
    void customStatesAreRecognized(
            @ForAll("customSqlStates") String customState) {

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[retryOnSqlStates=" + customState + "]:jdbc:h2:mem:test"
        );

        SQLException exception = new SQLException("Test error", customState);
        assertTrue(config.isRetryable(exception),
            "Custom state " + customState + " should be retryable");
    }

    /**
     * Property: When custom states are specified, default states are not retryable.
     */
    @Property(tries = 20)
    void customStatesReplaceDefaults(
            @ForAll("defaultRetryableStates") String defaultState) {

        // Use a custom state that's different from defaults
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry[retryOnSqlStates=CUSTOM1;CUSTOM2]:jdbc:h2:mem:test"
        );

        SQLException exception = new SQLException("Test error", defaultState);
        assertFalse(config.isRetryable(exception),
            "Default state " + defaultState + " should NOT be retryable when custom states are specified");
    }

    // ========== NON-RETRYABLE ERRORS ==========

    /**
     * Property: Non-transient SQL states are not retryable.
     */
    @Property(tries = 30)
    void nonTransientStatesNotRetryable(
            @ForAll("nonRetryableStates") String sqlState) {

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry:jdbc:h2:mem:test"
        );

        SQLException exception = new SQLException("Test error", sqlState);
        assertFalse(config.isRetryable(exception),
            "Non-transient state " + sqlState + " should NOT be retryable");
    }

    /**
     * Property: Null SQL state is not retryable.
     */
    @Property(tries = 10)
    void nullSqlStateNotRetryable() {
        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(
            "jdbc:retry:jdbc:h2:mem:test"
        );

        SQLException exception = new SQLException("Test error", (String) null);
        assertFalse(config.isRetryable(exception),
            "Null SQL state should NOT be retryable");
    }

    // ========== SUCCESSFUL QUERIES EXECUTE ONCE ==========

    /**
     * Property: Successful queries return correct results without retry.
     */
    @Property(tries = 50)
    void successfulQueriesReturnCorrectResult(
            @ForAll @IntRange(min = 1, max = 1000) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String retryUrl = "jdbc:retry[maxRetries=5]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(retryUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Successful query should return correct result");
        }
    }

    /**
     * Property: Multiple successful queries in session all succeed.
     */
    @Property(tries = 30)
    void multipleSuccessfulQueriesAllSucceed(
            @ForAll @IntRange(min = 2, max = 10) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String retryUrl = "jdbc:retry:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(retryUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 1; i <= queryCount; i++) {
                try (ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                    assertTrue(rs.next());
                    assertEquals(i, rs.getInt(1),
                        "Query " + i + " should return correct result");
                }
            }
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:retry:X behaves like retry:X (cat is identity).
     */
    @Property(tries = 30)
    void catRetryComposition(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();

        String retryUrl = "jdbc:retry:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String catRetryUrl = "jdbc:cat:jdbc:retry:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int retryResult;
        int catRetryResult;

        try (Connection conn = DriverManager.getConnection(retryUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            retryResult = rs.getInt(1);
        }

        try (Connection conn = DriverManager.getConnection(catRetryUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            catRetryResult = rs.getInt(1);
        }

        assertEquals(retryResult, catRetryResult,
            "cat:retry:X should behave like retry:X");
    }

    /**
     * Property: retry:cat:X behaves like retry:X.
     */
    @Property(tries = 30)
    void retryCatComposition(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();

        String retryUrl = "jdbc:retry:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String retryCatUrl = "jdbc:retry:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        int retryResult;
        int retryCatResult;

        try (Connection conn = DriverManager.getConnection(retryUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            retryResult = rs.getInt(1);
        }

        try (Connection conn = DriverManager.getConnection(retryCatUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {
            assertTrue(rs.next());
            retryCatResult = rs.getInt(1);
        }

        assertEquals(retryResult, retryCatResult,
            "retry:cat:X should behave like retry:X");
    }

    // ========== CONFIG PARSING ==========

    /**
     * Property: Invalid parameter values fall back to defaults.
     */
    @Property(tries = 20)
    void invalidParametersFallBackToDefaults(
            @ForAll("invalidNumbers") String invalidValue) {

        String url = "jdbc:retry[maxRetries=" + invalidValue +
                     ",initialDelay=" + invalidValue + "]:jdbc:h2:mem:test";

        RetryDriver.RetryConfig config = new RetryDriver.RetryConfig(url);

        // Should fall back to defaults
        assertEquals(3, config.getMaxRetries(), "Invalid maxRetries should default to 3");
        assertEquals(100, config.getInitialDelay(), "Invalid initialDelay should default to 100");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> defaultRetryableStates() {
        return Arbitraries.of(
            "08001",  // SQL client unable to establish connection
            "08003",  // Connection does not exist
            "08004",  // Server rejected connection
            "08006",  // Connection failure
            "08007",  // Transaction resolution unknown
            "40001",  // Serialization failure (deadlock)
            "40P01",  // PostgreSQL deadlock detected
            "57P01",  // PostgreSQL admin shutdown
            "HYT00",  // Timeout expired
            "HYT01"   // Connection timeout
        );
    }

    @Provide
    Arbitrary<String> nonRetryableStates() {
        return Arbitraries.of(
            "42000",  // Syntax error
            "42S02",  // Table not found
            "23000",  // Integrity constraint violation
            "23505",  // Unique violation
            "22001",  // String data right truncation
            "42601",  // Syntax error in SQL statement
            "42P01",  // Undefined table
            "28000"   // Invalid authorization
        );
    }

    @Provide
    Arbitrary<String> customSqlStates() {
        return Arbitraries.of(
            "CUST1",
            "CUST2",
            "XX001",
            "MY001",
            "APP99"
        );
    }

    @Provide
    Arbitrary<String> invalidNumbers() {
        return Arbitraries.of(
            "abc",
            "12.34.56",
            "",
            "null",
            "NaN",
            "-",
            "1e99999"
        );
    }
}
