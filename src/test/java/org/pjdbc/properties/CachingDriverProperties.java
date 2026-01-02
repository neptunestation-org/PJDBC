package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CachingDriver.
 *
 * These tests verify caching properties:
 * - Cache hits return same results as original query
 * - Cache invalidation on write operations
 * - LRU eviction when maxSize exceeded
 * - Different queries have independent cache entries
 * - Statistics accuracy
 * - Driver composition
 */
class CachingDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "cache_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private void setupTestTable(String dbName, int rowCount) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100), score INT)");
                stmt.execute("DELETE FROM users");
                for (int i = 1; i <= rowCount; i++) {
                    stmt.execute("INSERT INTO users VALUES (" + i + ", 'User" + i + "', " + (i * 10) + ")");
                }
            }
        }
    }

    // ========== CACHE HIT RETURNS SAME RESULT ==========

    /**
     * Property: Executing same SELECT query twice returns identical results.
     * Second call should be a cache hit.
     */
    @Property(tries = 30)
    void cacheHitReturnsSameResult(
            @ForAll @IntRange(min = 1, max = 10) int rowCount,
            @ForAll @IntRange(min = 1, max = 10) int queryId) throws SQLException {

        Assume.that(queryId <= rowCount);

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String sql = "SELECT * FROM users WHERE id = " + queryId;

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // First query - cache miss
            String firstResult;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                firstResult = rs.getString("name");
            }

            long missesAfterFirst = cache.getMisses();
            long hitsAfterFirst = cache.getHits();

            // Second query - should be cache hit with same result
            String secondResult;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                secondResult = rs.getString("name");
            }

            assertEquals(firstResult, secondResult,
                "Cache hit should return same result as original query");
            assertEquals(missesAfterFirst, cache.getMisses(),
                "Second query should not increase misses");
            assertEquals(hitsAfterFirst + 1, cache.getHits(),
                "Second query should increase hits");
        }
    }

    // ========== CACHE INVALIDATION ON WRITE ==========

    /**
     * Property: INSERT/UPDATE/DELETE operations invalidate cached SELECT results
     * when invalidateOnWrite=true.
     */
    @Property(tries = 30)
    void writeOperationsInvalidateCache(
            @ForAll @IntRange(min = 2, max = 10) int rowCount,
            @ForAll("writeOperations") String writeOp) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Populate cache with a SELECT
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) { /* consume */ }
            }

            assertTrue(cache.size() > 0, "Cache should have entries after SELECT");

            // Execute write operation
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(writeOp);
            }

            assertEquals(0, cache.size(),
                "Cache should be cleared after write operation: " + writeOp);
        }
    }

    /**
     * Property: With invalidateOnWrite=false, writes don't clear cache.
     */
    @Property(tries = 20)
    void noInvalidationWhenDisabled(
            @ForAll @IntRange(min = 2, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache[invalidateOnWrite=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Populate cache
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) { /* consume */ }
            }

            int sizeBeforeWrite = cache.size();
            assertTrue(sizeBeforeWrite > 0, "Cache should have entries");

            // Execute write - should NOT invalidate
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = 1");
            }

            assertEquals(sizeBeforeWrite, cache.size(),
                "Cache should NOT be cleared when invalidateOnWrite=false");
        }
    }

    // ========== LRU EVICTION ==========

    /**
     * Property: When cache exceeds maxSize, entries are evicted.
     */
    @Property(tries = 20)
    void lruEvictionWhenMaxSizeExceeded(
            @ForAll @IntRange(min = 3, max = 5) int maxSize) throws SQLException {

        String dbName = createUniqueDbName();
        int rowCount = maxSize + 5; // More rows than cache can hold
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache[maxSize=" + maxSize + "]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Execute more unique queries than maxSize
            for (int i = 1; i <= rowCount; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + i)) {
                    while (rs.next()) { /* consume */ }
                }
            }

            assertTrue(cache.size() <= maxSize,
                "Cache size (" + cache.size() + ") should not exceed maxSize (" + maxSize + ")");
            assertTrue(cache.getEvictions() > 0,
                "Should have evictions when exceeding maxSize");
        }
    }

    // ========== DIFFERENT QUERIES DON'T INTERFERE ==========

    /**
     * Property: Different SELECT queries have independent cache entries
     * and return their own results.
     */
    @Property(tries = 30)
    void differentQueriesAreIndependent(
            @ForAll @IntRange(min = 2, max = 10) int rowCount,
            @ForAll @IntRange(min = 1, max = 10) int id1,
            @ForAll @IntRange(min = 1, max = 10) int id2) throws SQLException {

        Assume.that(id1 <= rowCount && id2 <= rowCount);
        Assume.that(id1 != id2);

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            // Query for id1
            String name1;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + id1)) {
                assertTrue(rs.next());
                name1 = rs.getString("name");
            }

            // Query for id2
            String name2;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + id2)) {
                assertTrue(rs.next());
                name2 = rs.getString("name");
            }

            // Verify they're different (since ids are different)
            assertNotEquals(name1, name2,
                "Different queries should return different results");

            // Re-query id1 - should get cached result
            String name1Again;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + id1)) {
                assertTrue(rs.next());
                name1Again = rs.getString("name");
            }

            assertEquals(name1, name1Again,
                "Re-querying id1 should return same cached result");
        }
    }

    // ========== STATISTICS ACCURACY ==========

    /**
     * Property: Cache statistics (hits, misses) accurately reflect operations.
     */
    @Property(tries = 30)
    void statisticsAreAccurate(
            @ForAll @IntRange(min = 1, max = 5) int uniqueQueries,
            @ForAll @IntRange(min = 1, max = 3) int repeatsPerQuery) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, uniqueQueries);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            long expectedMisses = 0;
            long expectedHits = 0;

            for (int q = 1; q <= uniqueQueries; q++) {
                String sql = "SELECT * FROM users WHERE id = " + q;

                for (int r = 0; r < repeatsPerQuery; r++) {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) { /* consume */ }
                    }

                    if (r == 0) {
                        expectedMisses++; // First time is a miss
                    } else {
                        expectedHits++; // Subsequent times are hits
                    }
                }
            }

            assertEquals(expectedMisses, cache.getMisses(),
                "Misses should equal number of unique queries");
            assertEquals(expectedHits, cache.getHits(),
                "Hits should equal number of repeat queries");

            // Verify hit ratio
            double expectedRatio = (double) expectedHits / (expectedHits + expectedMisses);
            assertEquals(expectedRatio, cache.getHitRatio(), 0.001,
                "Hit ratio should be accurate");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:cache:X behaves like cache:X (cat is identity).
     */
    @Property(tries = 20)
    void catCacheComposition(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String catCacheUrl = "jdbc:cat:jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String sql = "SELECT * FROM users WHERE id = 1";

        // Query through cache
        String cacheResult;
        try (Connection conn = DriverManager.getConnection(cacheUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            cacheResult = rs.getString("name");
        }

        // Query through cat:cache
        String catCacheResult;
        try (Connection conn = DriverManager.getConnection(catCacheUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            catCacheResult = rs.getString("name");
        }

        assertEquals(cacheResult, catCacheResult,
            "cat:cache:X should return same result as cache:X");
    }

    /**
     * Property: cache:cat:X behaves like cache:X.
     */
    @Property(tries = 20)
    void cacheCatComposition(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String cacheCatUrl = "jdbc:cache:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String sql = "SELECT * FROM users WHERE id = 1";

        // Query through cache
        String cacheResult;
        try (Connection conn = DriverManager.getConnection(cacheUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            cacheResult = rs.getString("name");
        }

        // Query through cache:cat
        String cacheCatResult;
        try (Connection conn = DriverManager.getConnection(cacheCatUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            cacheCatResult = rs.getString("name");
        }

        assertEquals(cacheResult, cacheCatResult,
            "cache:cat:X should return same result as cache:X");
    }

    /**
     * Property: log:cache:X preserves caching behavior.
     */
    @Property(tries = 20)
    void logCacheComposition(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String logCacheUrl = "jdbc:log:jdbc:cache:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String sql = "SELECT * FROM users WHERE id = 1";

        try (Connection conn = DriverManager.getConnection(logCacheUrl)) {
            // First query
            String firstResult;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                firstResult = rs.getString("name");
            }

            // Second query - should still work (cache is inside log)
            String secondResult;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                secondResult = rs.getString("name");
            }

            assertEquals(firstResult, secondResult,
                "log:cache should preserve query results");
        }
    }

    // ========== DISABLED CACHE ==========

    /**
     * Property: With enabled=false, caching is bypassed.
     */
    @Property(tries = 20)
    void disabledCacheBypasses(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String cacheUrl = "jdbc:cache[enabled=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String sql = "SELECT * FROM users WHERE id = 1";

        try (Connection conn = DriverManager.getConnection(cacheUrl)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Execute query twice
            for (int i = 0; i < 2; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) { /* consume */ }
                }
            }

            assertEquals(0, cache.size(), "Disabled cache should not store entries");
            assertEquals(0, cache.getHits(), "Disabled cache should have no hits");
        }
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> writeOperations() {
        return Arbitraries.of(
            "INSERT INTO users VALUES (99, 'New', 990)",
            "UPDATE users SET name = 'Updated' WHERE id = 1",
            "DELETE FROM users WHERE id = 1"
        );
    }
}
