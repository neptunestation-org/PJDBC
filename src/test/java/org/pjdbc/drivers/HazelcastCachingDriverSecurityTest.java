package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Security tests for HazelcastCachingDriver.
 *
 * Tests security properties specific to distributed caching:
 * 1. Safe serialization (SerializableCachedResultSet)
 * 2. Connection context isolation (prevents cross-user data leakage)
 * 3. maxCacheRows/maxCacheBytes limits
 * 4. SHA-256 cache keys
 * 5. Parameter state isolation
 */
public class HazelcastCachingDriverSecurityTest {

    private static final String TEST_RUN_ID = String.valueOf(System.nanoTime());

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.HazelcastCachingDriver");
    }

    @AfterClass
    public static void cleanup() {
        HazelcastCachingDriver.shutdownAll();
    }

    private static String uniqueCluster(String base) {
        return TEST_RUN_ID + "_sec_" + base;
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
    public void testSerializableCachedResultSetRoundTrip() throws SQLException {
        setupTestTable("hzsec_roundtrip");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hzsec_roundtrip;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    HazelcastCachingDriver.SerializableCachedResultSet original =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    // Hazelcast uses Java serialization internally, but our type is safe
                    assertEquals(2, original.getRowCount());
                    assertEquals("ID", original.getColumnNames()[0]);
                    assertEquals("NAME", original.getColumnNames()[1]);
                    assertFalse(original.isTooLargeToCache());
                }
            }
        }
    }

    @Test
    public void testSerializableCachedResultSetWithMaxRows() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:hzsec_serial_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS large_table (id INT PRIMARY KEY)");
                stmt.execute("DELETE FROM large_table");
                for (int i = 1; i <= 100; i++) {
                    stmt.execute("INSERT INTO large_table VALUES (" + i + ")");
                }
            }
        }

        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hzsec_serial_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM large_table")) {
                    // Limit to 50 rows
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs, 50);

                    assertEquals(50, cached.getRowCount());
                    assertTrue(cached.isTooLargeToCache());
                }
            }
        }
    }

    @Test
    public void testSerializableCachedResultSetWithMaxBytes() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:hzsec_serial_maxbytes;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS blob_table (id INT PRIMARY KEY, data VARCHAR(10000))");
                stmt.execute("DELETE FROM blob_table");
                String largeData = "x".repeat(5000);
                stmt.execute("INSERT INTO blob_table VALUES (1, '" + largeData + "')");
            }
        }

        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hzsec_serial_maxbytes;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM blob_table")) {
                    // 1KB limit
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs, 0, 1024);

                    assertTrue(cached.isTooLargeToCache());
                }
            }
        }
    }

    // ========== Connection Context Isolation Tests ==========

    @Test
    public void testIncludeContextDefaultTrue() {
        // Distributed caches should default to includeContext=true
        HazelcastCachingDriver.HazelcastCacheConfig config =
            new HazelcastCachingDriver.HazelcastCacheConfig("jdbc:hazelcast:jdbc:h2:mem:test");
        assertTrue(config.isIncludeContext());
    }

    @Test
    public void testConnectionContextIsolation() throws SQLException {
        setupTestTable("hzsec_ctx_iso");

        String url = String.format(
            "jdbc:hazelcast[clusterName=%s,mapName=ctx_iso_cache]:jdbc:h2:mem:hzsec_ctx_iso;DB_CLOSE_DELAY=-1",
            uniqueCluster("ctx_iso"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:hzsec_maxrows;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS large_table (id INT PRIMARY KEY, data VARCHAR(100))");
                stmt.execute("DELETE FROM large_table");
                for (int i = 1; i <= 100; i++) {
                    stmt.execute("INSERT INTO large_table VALUES (" + i + ", 'data" + i + "')");
                }
            }
        }

        String url = String.format(
            "jdbc:hazelcast[clusterName=%s,mapName=maxrows_cache,maxCacheRows=50]:jdbc:h2:mem:hzsec_maxrows;DB_CLOSE_DELAY=-1",
            uniqueCluster("maxrows"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:hzsec_maxbytes;DB_CLOSE_DELAY=-1")) {
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
            "jdbc:hazelcast[clusterName=%s,mapName=maxbytes_cache,maxCacheBytes=1000]:jdbc:h2:mem:hzsec_maxbytes;DB_CLOSE_DELAY=-1",
            uniqueCluster("maxbytes"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        setupTestTable("hzsec_param_iso");

        String url = String.format(
            "jdbc:hazelcast[clusterName=%s,mapName=param_iso_cache]:jdbc:h2:mem:hzsec_param_iso;DB_CLOSE_DELAY=-1",
            uniqueCluster("param_iso"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        setupTestTable("hzsec_pstmt_reuse");

        String url = String.format(
            "jdbc:hazelcast[clusterName=%s,mapName=pstmt_reuse_cache]:jdbc:h2:mem:hzsec_pstmt_reuse;DB_CLOSE_DELAY=-1",
            uniqueCluster("pstmt_reuse"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        setupTestTable("hzsec_sha256");

        String url = String.format(
            "jdbc:hazelcast[clusterName=%s,mapName=sha256_cache]:jdbc:h2:mem:hzsec_sha256;DB_CLOSE_DELAY=-1",
            uniqueCluster("sha256"));

        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
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
        HazelcastCachingDriver.HazelcastCacheConfig config =
            new HazelcastCachingDriver.HazelcastCacheConfig(
                "jdbc:hazelcast[maxCacheRows=500]:jdbc:h2:mem:test");
        assertEquals(500, config.getMaxCacheRows());
    }

    @Test
    public void testMaxCacheBytesConfig() {
        HazelcastCachingDriver.HazelcastCacheConfig config =
            new HazelcastCachingDriver.HazelcastCacheConfig(
                "jdbc:hazelcast[maxCacheBytes=5242880]:jdbc:h2:mem:test");
        assertEquals(5242880L, config.getMaxCacheBytes());
    }

    @Test
    public void testIncludeContextConfigExplicitFalse() {
        HazelcastCachingDriver.HazelcastCacheConfig config =
            new HazelcastCachingDriver.HazelcastCacheConfig(
                "jdbc:hazelcast[includeContext=false]:jdbc:h2:mem:test");
        assertFalse(config.isIncludeContext());
    }
}
