package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Security tests for CachingDriver.
 *
 * Tests the following security properties:
 * 1. SHA-256 cache key generation (prevents hash collision attacks)
 * 2. Parameter state isolation (prevents stale parameter leakage)
 * 3. maxCacheRows limit (prevents DoS via memory exhaustion)
 * 4. maxCacheBytes limit (prevents DoS via large BLOBs/CLOBs)
 * 5. Connection context isolation (prevents cross-user data leakage)
 */
public class CachingDriverSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CachingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100), secret VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice_secret')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob_secret')");
            }
        }
    }

    // ========== SHA-256 Cache Key Security Tests ==========

    @Test
    public void testCacheKeyUsesSha256Format() {
        // Verify cache keys use SHA-256 (64 hex chars) instead of hashCode (max 10 chars)
        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users");
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT id FROM users");

        // SHA-256 produces 64 hex characters
        assertEquals(64 + 7, key1.length()); // "prefix:" + 64 chars
        assertEquals(64 + 7, key2.length());

        // Keys should be different for different queries
        assertNotEquals(key1, key2);

        // Keys should be hex only (after prefix)
        String hash1 = key1.substring(7);
        assertTrue("Hash should be hex", hash1.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testSha256ProducesDeterministicKeys() {
        // Same SQL should always produce same key
        String key1 = CacheKeyBuilder.sha256("SELECT * FROM users WHERE id = 1");
        String key2 = CacheKeyBuilder.sha256("SELECT * FROM users WHERE id = 1");
        assertEquals(key1, key2);
    }

    @Test
    public void testSha256DifferentiatesSimilarQueries() {
        // Similar but different queries should produce different keys
        String key1 = CacheKeyBuilder.sha256("SELECT * FROM users WHERE id = 1");
        String key2 = CacheKeyBuilder.sha256("SELECT * FROM users WHERE id = 2");
        String key3 = CacheKeyBuilder.sha256("SELECT * FROM users WHERE id = 11");

        assertNotEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertNotEquals(key2, key3);
    }

    @Test
    public void testCacheKeyWithParametersIsolated() {
        // Different parameters should produce different cache keys
        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users WHERE id = ?", 1);
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users WHERE id = ?", 2);

        assertNotEquals(key1, key2);
    }

    @Test
    public void testCacheKeyWithNullParameters() {
        // Null parameters should be handled safely
        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users WHERE name = ?", (Object) null);
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users WHERE name = ?", "Alice");

        assertNotEquals(key1, key2);
        assertTrue(key1.length() > 7); // Has valid hash
    }

    // ========== Parameter State Leakage Tests ==========

    @Test
    public void testParametersClearedAfterExecution() throws SQLException {
        setupTestTable("sec_param_clear");
        String url = "jdbc:cache:jdbc:h2:mem:sec_param_clear;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            // Execute with id=1
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }

            // Execute with id=2 - should get Bob, not cached Alice
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Bob", rs.getString("name"));
                }
            }
        }
    }

    @Test
    public void testPreparedStatementReuseWithDifferentParams() throws SQLException {
        setupTestTable("sec_pstmt_reuse");
        String url = "jdbc:cache:jdbc:h2:mem:sec_pstmt_reuse;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                // First execution with id=1
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }

                // Second execution with id=2 - must get different result
                pstmt.setInt(1, 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Bob", rs.getString("name"));
                }
            }
        }
    }

    // ========== maxCacheRows DoS Protection Tests ==========

    @Test
    public void testMaxCacheRowsLimitEnforced() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:sec_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS large_table (id INT PRIMARY KEY, data VARCHAR(100))");
                stmt.execute("DELETE FROM large_table");
                // Insert 100 rows
                for (int i = 1; i <= 100; i++) {
                    stmt.execute("INSERT INTO large_table VALUES (" + i + ", 'data" + i + "')");
                }
            }
        }

        // Configure maxCacheRows=50
        String url = "jdbc:cache[maxCacheRows=50]:jdbc:h2:mem:sec_maxrows;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            // Query all 100 rows - should exceed limit and NOT be cached
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM large_table")) {
                    int count = 0;
                    while (rs.next()) count++;
                    assertEquals(50, count); // Only maxCacheRows returned
                }
            }

            // Cache should be empty because result exceeded limit
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testResultsBelowMaxRowsAreCached() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:sec_below_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS small_table (id INT PRIMARY KEY)");
                stmt.execute("DELETE FROM small_table");
                for (int i = 1; i <= 10; i++) {
                    stmt.execute("INSERT INTO small_table VALUES (" + i + ")");
                }
            }
        }

        String url = "jdbc:cache[maxCacheRows=100]:jdbc:h2:mem:sec_below_maxrows;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM small_table");
            }

            // Should be cached since 10 < 100
            assertEquals(1, cache.size());
        }
    }

    // ========== maxCacheBytes DoS Protection Tests ==========

    @Test
    public void testMaxCacheBytesLimitEnforced() throws SQLException {
        // Test that results exceeding byte limit are truncated and NOT cached
        // This is DoS protection - we don't read/store data beyond the limit
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:sec_maxbytes;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS blob_table (id INT PRIMARY KEY, data VARCHAR(10000))");
                stmt.execute("DELETE FROM blob_table");
                // Insert multiple rows, each 200 chars (~440 bytes each with overhead)
                for (int i = 1; i <= 5; i++) {
                    String data = "x".repeat(200);
                    stmt.execute("INSERT INTO blob_table VALUES (" + i + ", '" + data + "')");
                }
            }
        }

        // Configure maxCacheBytes to allow ~2 rows worth (1000 bytes)
        String url = "jdbc:cache[maxCacheBytes=1000]:jdbc:h2:mem:sec_maxbytes;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM blob_table ORDER BY id")) {
                    // Should get partial results (first ~2 rows before hitting limit)
                    int count = 0;
                    while (rs.next()) count++;
                    assertTrue("Should return partial results", count > 0 && count < 5);
                }
            }

            // Should NOT be cached because total exceeded byte limit
            assertEquals(0, cache.size());

            // Re-query should also not hit cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM blob_table ORDER BY id");
            }
            assertEquals(0, cache.getHits());
            assertEquals(2, cache.getMisses());
        }
    }

    @Test
    public void testSmallResultsCachedWithByteLimit() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:sec_small_bytes;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS tiny_table (id INT PRIMARY KEY, name VARCHAR(10))");
                stmt.execute("DELETE FROM tiny_table");
                stmt.execute("INSERT INTO tiny_table VALUES (1, 'Alice')");
            }
        }

        // 10MB limit should easily accommodate tiny result
        String url = "jdbc:cache[maxCacheBytes=10485760]:jdbc:h2:mem:sec_small_bytes;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            cache.clear();

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM tiny_table");
            }

            assertEquals(1, cache.size());
        }
    }

    // ========== Connection Context Isolation Tests ==========

    @Test
    public void testConnectionContextIncludedInKey() {
        CacheKeyBuilder.ConnectionContext ctx1 = new CacheKeyBuilder.ConnectionContext("db1", "schema1", "user1");
        CacheKeyBuilder.ConnectionContext ctx2 = new CacheKeyBuilder.ConnectionContext("db1", "schema1", "user2");

        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx1);
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx2);

        // Different users should produce different cache keys
        assertNotEquals(key1, key2);
    }

    @Test
    public void testSameContextProducesSameKey() {
        CacheKeyBuilder.ConnectionContext ctx1 = new CacheKeyBuilder.ConnectionContext("db", "schema", "user");
        CacheKeyBuilder.ConnectionContext ctx2 = new CacheKeyBuilder.ConnectionContext("db", "schema", "user");

        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx1);
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx2);

        assertEquals(key1, key2);
    }

    @Test
    public void testContextWithNullsHandled() {
        CacheKeyBuilder.ConnectionContext ctx1 = new CacheKeyBuilder.ConnectionContext(null, null, "user1");
        CacheKeyBuilder.ConnectionContext ctx2 = new CacheKeyBuilder.ConnectionContext(null, null, "user2");

        String key1 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx1);
        String key2 = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", ctx2);

        assertNotEquals(key1, key2);
        assertTrue(key1.length() > 7);
        assertTrue(key2.length() > 7);
    }

    @Test
    public void testNullContextFallsBackToBasicKey() {
        String keyWithNull = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users", (CacheKeyBuilder.ConnectionContext) null);
        String keyWithoutContext = CacheKeyBuilder.buildKey("prefix:", "SELECT * FROM users");

        assertEquals(keyWithNull, keyWithoutContext);
    }

    @Test
    public void testIncludeContextConfigDefault() {
        // Default for in-memory cache is false (single JVM, no shared cache)
        CachingDriver.CacheConfig config = new CachingDriver.CacheConfig("jdbc:cache:jdbc:h2:mem:test");
        assertFalse(config.isIncludeContext());
    }

    @Test
    public void testIncludeContextConfigExplicit() {
        CachingDriver.CacheConfig config = new CachingDriver.CacheConfig(
            "jdbc:cache[includeContext=true]:jdbc:h2:mem:test");
        assertTrue(config.isIncludeContext());
    }

    // ========== Cache Key with Parameters and Context ==========

    @Test
    public void testBuildKeyWithContextAndParams() {
        CacheKeyBuilder.ConnectionContext ctx = new CacheKeyBuilder.ConnectionContext("db", "schema", "user");

        String key1 = CacheKeyBuilder.buildKeyWithContext("prefix:", "SELECT * FROM users WHERE id = ?", ctx, 1);
        String key2 = CacheKeyBuilder.buildKeyWithContext("prefix:", "SELECT * FROM users WHERE id = ?", ctx, 2);
        String key3 = CacheKeyBuilder.buildKeyWithContext("prefix:", "SELECT * FROM users WHERE id = ?", null, 1);

        // Different params produce different keys
        assertNotEquals(key1, key2);

        // Different context produces different keys
        assertNotEquals(key1, key3);
    }

    // ========== Size Estimation Tests ==========

    @Test
    public void testEstimateSizeForStrings() {
        // String size: 40 (object overhead) + length * 2 (UTF-16)
        long size = SafeResultSetSerializer.estimateSize("hello");
        assertEquals(40 + 5 * 2, size); // 50 bytes
    }

    @Test
    public void testEstimateSizeForBytes() {
        byte[] bytes = new byte[100];
        long size = SafeResultSetSerializer.estimateSize(bytes);
        assertEquals(16 + 100, size); // 116 bytes
    }

    @Test
    public void testEstimateSizeForNull() {
        long size = SafeResultSetSerializer.estimateSize(null);
        assertEquals(0, size);
    }

    @Test
    public void testEstimateRowSize() {
        Object[] row = new Object[] {"hello", 42, null, new byte[10]};
        long size = SafeResultSetSerializer.estimateRowSize(row);

        // Array overhead (16) + 4 refs (32) + string (50) + int (16) + null (0) + bytes (26)
        long expected = 16 + 4 * 8 + 50 + 16 + 0 + 26;
        assertEquals(expected, size);
    }

    // ========== Integration: Cache Isolation with Context ==========

    @Test
    public void testCacheIsolationWithIncludeContext() throws SQLException {
        setupTestTable("sec_isolation");

        // With includeContext=true, different schemas should have isolated caches
        String url = "jdbc:cache[includeContext=true]:jdbc:h2:mem:sec_isolation;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);
            assertTrue(cache.getConfig().isIncludeContext());

            // Queries should work normally
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }

            // Same query should hit cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
            }
            assertEquals(1, cache.getHits());
        }
    }
}
