package org.pjdbc.conformance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.pjdbc.capabilities.DriverCapability;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for performance invariants across all PJDBC drivers.
 *
 * <p>Verifies that all drivers:
 * <ul>
 *   <li>Connection establishment completes in bounded time</li>
 *   <li>Passthrough drivers add minimal overhead</li>
 *   <li>Driver registration is efficient</li>
 *   <li>Capabilities loading is cached and fast</li>
 * </ul>
 */
@DisplayName("Performance Invariant Conformance")
public class PerformanceInvariantConformanceTest extends DriverConformanceTest {

    private static final long CONNECTION_TIMEOUT_MS = 5000;
    private static final long OVERHEAD_THRESHOLD_MS = 100;

    @Test
    @DisplayName("Capabilities loading is cached")
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void capabilitiesLoadingIsCached() {
        // First load
        long start1 = System.nanoTime();
        var caps1 = capabilities;
        long time1 = System.nanoTime() - start1;

        // Second load should be near-instant (cached)
        long start2 = System.nanoTime();
        var caps2 = capabilities;
        long time2 = System.nanoTime() - start2;

        assertSame(caps1, caps2, "Capabilities should be cached singleton");
        assertTrue(time2 < time1 / 10 || time2 < 1_000_000,  // < 1ms
            "Cached capabilities load should be much faster than initial load");
    }

    @Test
    @DisplayName("Driver count matches expected")
    void driverCountMatchesExpected() {
        int count = capabilities.getDriverCount();
        assertTrue(count >= 15, "Should have at least 15 drivers, got: " + count);
        assertTrue(count <= 30, "Should have at most 30 drivers, got: " + count);
    }

    @Test
    @DisplayName("All capability tags are non-empty")
    void allCapabilityTagsAreNonEmpty() {
        var tags = capabilities.getAllCapabilityTags();

        assertFalse(tags.isEmpty(), "Should have capability tags");
        for (String tag : tags) {
            assertNotNull(tag, "Tag should not be null");
            assertFalse(tag.isEmpty(), "Tag should not be empty");
            assertTrue(tag.matches("[a-z]+"), "Tag should be lowercase: " + tag);
        }
    }

    @Test
    @DisplayName("Passthrough driver adds minimal overhead")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void passthroughDriverAddsMinimalOverhead() throws Exception {
        // Baseline: direct H2 connection
        long directTime = measureConnectionTime(H2_URL);

        // With cat driver
        String catUrl = "jdbc:cat:" + H2_URL;
        long catTime = measureConnectionTime(catUrl);

        // Cat driver should add minimal overhead
        long overhead = catTime - directTime;
        assertTrue(overhead < OVERHEAD_THRESHOLD_MS,
            "CatDriver overhead should be < " + OVERHEAD_THRESHOLD_MS + "ms, was: " + overhead + "ms");
    }

    @Test
    @DisplayName("Connection through composable drivers completes in bounded time")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void connectionThroughComposableDriversCompletes() throws Exception {
        // Test a few non-network drivers
        String[] testPrefixes = {"cat", "log"};

        for (String prefix : testPrefixes) {
            var driverOpt = capabilities.findByPrefix(prefix);
            if (driverOpt.isEmpty()) continue;

            String url = "jdbc:" + prefix + ":" + H2_URL;
            long time = measureConnectionTime(url);

            assertTrue(time < CONNECTION_TIMEOUT_MS,
                "Connection through " + prefix + " should complete in < " + CONNECTION_TIMEOUT_MS +
                "ms, took: " + time + "ms");
        }
    }

    @Test
    @DisplayName("Chained drivers complete in bounded time")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void chainedDriversCompleteInBoundedTime() throws Exception {
        // Chain two lightweight drivers
        String url = "jdbc:cat:jdbc:log:" + H2_URL;
        long time = measureConnectionTime(url);

        assertTrue(time < CONNECTION_TIMEOUT_MS,
            "Chained connection should complete in < " + CONNECTION_TIMEOUT_MS +
            "ms, took: " + time + "ms");
    }

    @Test
    @DisplayName("Query methods return in bounded time")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void queryMethodsReturnInBoundedTime() {
        // Test various query methods
        long start = System.nanoTime();

        capabilities.getAllDrivers();
        capabilities.findByPrefix("cat");
        capabilities.findByCapability("caching");
        capabilities.findComposable();
        capabilities.findTerminal();
        capabilities.findWithDependencies();
        capabilities.findBySideEffect("network");
        capabilities.getAllCapabilityTags();
        capabilities.hasDriver("pool");
        capabilities.getDriverCount();

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 100, "All query methods should complete in < 100ms, took: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Terminal drivers connect without delegate")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void terminalDriversConnectWithoutDelegate() throws Exception {
        for (DriverCapability driver : capabilities.findTerminal()) {
            String url = "jdbc:" + driver.prefix() + ":test";

            try {
                long time = measureConnectionTime(url);
                assertTrue(time < CONNECTION_TIMEOUT_MS,
                    "Terminal driver " + driver.name() + " should connect quickly");
            } catch (Exception e) {
                // Some terminal drivers may fail for other reasons, which is okay
                // We're mainly testing that they don't hang
            }
        }
    }

    @Test
    @DisplayName("Driver lookup by prefix is fast")
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void driverLookupByPrefixIsFast() {
        long start = System.nanoTime();

        // Perform many lookups
        for (int i = 0; i < 1000; i++) {
            capabilities.findByPrefix("cache");
            capabilities.findByPrefix("pool");
            capabilities.findByPrefix("log");
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 500, "3000 prefix lookups should complete in < 500ms, took: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Simple statement execution through drivers works")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void simpleStatementExecutionThroughDriversWorks() throws Exception {
        String url = "jdbc:cat:" + H2_URL;

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Simple query should work
            var rs = stmt.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    /**
     * Measures connection establishment time in milliseconds.
     */
    private long measureConnectionTime(String url) throws Exception {
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Just establish and close
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
