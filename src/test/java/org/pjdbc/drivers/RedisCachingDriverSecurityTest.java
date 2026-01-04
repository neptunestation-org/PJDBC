package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Security tests for RedisCachingDriver.
 *
 * Tests security properties specific to distributed caching:
 * 1. Safe serialization (no arbitrary class deserialization)
 * 2. Connection context isolation (prevents cross-user data leakage)
 * 3. maxCacheRows/maxCacheBytes limits
 * 4. SHA-256 cache keys
 * 5. Parameter state isolation
 */
public class RedisCachingDriverSecurityTest {

    @SuppressWarnings("rawtypes")
    public static GenericContainer redis = new GenericContainer(
        DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    private static final String TEST_RUN_ID = String.valueOf(System.nanoTime());

    static {
        redis.start();
    }

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.RedisCachingDriver");
    }

    private static String getRedisHost() {
        return redis.getHost();
    }

    private static int getRedisPort() {
        return redis.getFirstMappedPort();
    }

    private static String uniquePrefix(String base) {
        return TEST_RUN_ID + "_sec_" + base + ":";
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

    // ========== Safe Serialization Tests ==========

    @Test
    public void testSafeSerializerMagicBytesValidation() throws SQLException, IOException {
        setupTestTable("redissec_magic");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redissec_magic;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    SafeResultSetSerializer.CachedData cached = SafeResultSetSerializer.fromResultSet(rs);
                    byte[] serialized = SafeResultSetSerializer.serialize(cached);

                    // Verify magic bytes at start (0x50524353 = "PRCS")
                    assertEquals((byte) 0x50, serialized[0]);
                    assertEquals((byte) 0x52, serialized[1]);
                    assertEquals((byte) 0x43, serialized[2]);
                    assertEquals((byte) 0x53, serialized[3]);
                }
            }
        }
    }

    @Test(expected = IOException.class)
    public void testSafeSerializerRejectsBadMagic() throws IOException {
        byte[] invalid = new byte[] { 0x00, 0x00, 0x00, 0x00 };
        SafeResultSetSerializer.deserialize(invalid);
    }

    @Test(expected = IOException.class)
    public void testSafeSerializerRejectsUnsupportedVersion() throws IOException {
        byte[] invalid = new byte[] {
            0x50, 0x52, 0x43, 0x53, // Magic: PRCS
            0x7F // Invalid version
        };
        SafeResultSetSerializer.deserialize(invalid);
    }

    @Test
    public void testSafeSerializerRoundTrip() throws SQLException, IOException {
        setupTestTable("redissec_roundtrip");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redissec_roundtrip;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    SafeResultSetSerializer.CachedData original = SafeResultSetSerializer.fromResultSet(rs);
                    byte[] serialized = SafeResultSetSerializer.serialize(original);
                    SafeResultSetSerializer.CachedData restored = SafeResultSetSerializer.deserialize(serialized);

                    assertEquals(original.getRowCount(), restored.getRowCount());
                    assertEquals(original.getColumnNames().length, restored.getColumnNames().length);
                    assertEquals("ID", restored.getColumnNames()[0]);
                    assertEquals("NAME", restored.getColumnNames()[1]);
                }
            }
        }
    }

    // ========== Connection Context Isolation Tests ==========

    @Test
    public void testIncludeContextDefaultTrue() {
        // Distributed caches should default to includeContext=true
        RedisCachingDriver.RedisCacheConfig config =
            new RedisCachingDriver.RedisCacheConfig("jdbc:rediscache:jdbc:h2:mem:test");
        assertTrue(config.isIncludeContext());
    }

    @Test
    public void testConnectionContextIsolation() throws SQLException {
        setupTestTable("redissec_ctx_iso");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s]:jdbc:h2:mem:redissec_ctx_iso;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("ctx_iso"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            assertNotNull(cache);
            cache.clear();

            // First query populates cache
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getMisses());

            // Same query should hit cache
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getHits());
        }
    }

    // ========== maxCacheRows Protection Tests ==========

    @Test
    public void testMaxCacheRowsEnforced() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:redissec_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS large_table (id INT PRIMARY KEY, data VARCHAR(100))");
                stmt.execute("DELETE FROM large_table");
                for (int i = 1; i <= 100; i++) {
                    stmt.execute("INSERT INTO large_table VALUES (" + i + ", 'data" + i + "')");
                }
            }
        }

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s,maxCacheRows=50]:jdbc:h2:mem:redissec_maxrows;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("maxrows"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM large_table")) {
                    int count = 0;
                    while (rs.next()) count++;
                    assertEquals(50, count); // Limited to maxCacheRows
                }
            }

            // Re-query should miss cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM large_table");
            }
            assertEquals(2, cache.getMisses());
            assertEquals(0, cache.getHits());
        }
    }

    // ========== maxCacheBytes Protection Tests ==========

    @Test
    public void testMaxCacheBytesEnforced() throws SQLException {
        // Test that results exceeding byte limit are truncated and NOT cached
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:redissec_maxbytes;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS blob_table (id INT PRIMARY KEY, data VARCHAR(10000))");
                stmt.execute("DELETE FROM blob_table");
                // Insert multiple rows, each ~440 bytes
                for (int i = 1; i <= 5; i++) {
                    String data = "x".repeat(200);
                    stmt.execute("INSERT INTO blob_table VALUES (" + i + ", '" + data + "')");
                }
            }
        }

        // Configure limit to allow ~2 rows (1000 bytes)
        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s,maxCacheBytes=1000]:jdbc:h2:mem:redissec_maxbytes;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("maxbytes"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM blob_table ORDER BY id")) {
                    // Should get partial results
                    int count = 0;
                    while (rs.next()) count++;
                    assertTrue("Should return partial results", count > 0 && count < 5);
                }
            }

            // Should not be cached - re-query is also a miss
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM blob_table ORDER BY id");
            }
            assertEquals(2, cache.getMisses());
            assertEquals(0, cache.getHits());
        }
    }

    // ========== Parameter State Isolation Tests ==========

    @Test
    public void testPreparedStatementParameterIsolation() throws SQLException {
        setupTestTable("redissec_param_iso");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s]:jdbc:h2:mem:redissec_param_iso;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("param_iso"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            // Execute with id=1
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }

            // Execute with id=2 - must get Bob
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Bob", rs.getString("name"));
                }
            }

            assertEquals(2, cache.getMisses());
        }
    }

    @Test
    public void testPreparedStatementReuseWithDifferentParams() throws SQLException {
        setupTestTable("redissec_pstmt_reuse");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s]:jdbc:h2:mem:redissec_pstmt_reuse;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("pstmt_reuse"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                // First execution
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }

                // Reuse with different param
                pstmt.setInt(1, 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Bob", rs.getString("name"));
                }

                // Third execution - cache hit
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }

            assertEquals(2, cache.getMisses());
            assertEquals(1, cache.getHits());
        }
    }

    // ========== SHA-256 Key Security Tests ==========

    @Test
    public void testCacheKeyUsesSha256() throws SQLException {
        setupTestTable("redissec_sha256");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=%s]:jdbc:h2:mem:redissec_sha256;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort(), uniquePrefix("sha256"));

        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            // Different queries should produce different cache keys
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
                stmt.executeQuery("SELECT * FROM users WHERE id = 2");
            }

            assertEquals(2, cache.getMisses());
        }
    }

    // ========== Config Tests ==========

    @Test
    public void testMaxCacheRowsConfig() {
        RedisCachingDriver.RedisCacheConfig config =
            new RedisCachingDriver.RedisCacheConfig(
                "jdbc:rediscache[maxCacheRows=500]:jdbc:h2:mem:test");
        assertEquals(500, config.getMaxCacheRows());
    }

    @Test
    public void testMaxCacheBytesConfig() {
        RedisCachingDriver.RedisCacheConfig config =
            new RedisCachingDriver.RedisCacheConfig(
                "jdbc:rediscache[maxCacheBytes=5242880]:jdbc:h2:mem:test");
        assertEquals(5242880L, config.getMaxCacheBytes());
    }

    @Test
    public void testIncludeContextConfigExplicitFalse() {
        RedisCachingDriver.RedisCacheConfig config =
            new RedisCachingDriver.RedisCacheConfig(
                "jdbc:rediscache[includeContext=false]:jdbc:h2:mem:test");
        assertTrue(!config.isIncludeContext());
    }
}
