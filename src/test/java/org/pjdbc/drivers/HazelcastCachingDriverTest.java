package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

public class HazelcastCachingDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.HazelcastCachingDriver");
    }

    @AfterClass
    public static void cleanup() {
        // Shutdown all Hazelcast instances
        HazelcastCachingDriver.shutdownAll();
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
            }
        }
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        HazelcastCachingDriver driver = new HazelcastCachingDriver();
        assertTrue(driver.acceptsURL("jdbc:hazelcast:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:hazelcast[mode=embedded]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:hazelcast[mode=client,members=hz1:5701;hz2:5701]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:cache:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:rediscache:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testConfigDefaults() {
        HazelcastCachingDriver.HazelcastCacheConfig config = new HazelcastCachingDriver.HazelcastCacheConfig(
            "jdbc:hazelcast:jdbc:h2:mem:test"
        );
        assertEquals("embedded", config.getMode());
        assertEquals("pjdbc-cache", config.getClusterName());
        assertEquals("localhost:5701", config.getMembers());
        assertEquals("pjdbc-query-cache", config.getMapName());
        assertEquals(60, config.getTtlSeconds());
        assertEquals(0, config.getMaxIdleSeconds());
        assertTrue(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
        assertTrue(config.isEmbedded());
    }

    @Test
    public void testCustomConfig() {
        HazelcastCachingDriver.HazelcastCacheConfig config = new HazelcastCachingDriver.HazelcastCacheConfig(
            "jdbc:hazelcast[mode=client,clusterName=myCluster,members=hz1:5701;hz2:5701,mapName=myCache,ttl=300,maxIdle=120,invalidateOnWrite=false,enabled=true]:jdbc:h2:mem:test"
        );
        assertEquals("client", config.getMode());
        assertEquals("myCluster", config.getClusterName());
        assertEquals("hz1:5701;hz2:5701", config.getMembers());
        assertEquals("myCache", config.getMapName());
        assertEquals(300, config.getTtlSeconds());
        assertEquals(120, config.getMaxIdleSeconds());
        assertFalse(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
        assertFalse(config.isEmbedded());
    }

    @Test
    public void testSerializableCachedResultSet() throws SQLException {
        setupTestTable("hz_test_serialize");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_serialize;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    assertNotNull(cached);
                    assertEquals(2, cached.getRowCount());
                    assertEquals(2, cached.getColumnNames().length);
                    assertEquals("ID", cached.getColumnNames()[0]);
                    assertEquals("NAME", cached.getColumnNames()[1]);
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapper() throws SQLException {
        setupTestTable("hz_test_wrapper");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_wrapper;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {

                        assertTrue(wrapper.next());
                        assertEquals(1, wrapper.getInt("id"));
                        assertEquals("Alice", wrapper.getString("name"));

                        assertTrue(wrapper.next());
                        assertEquals(2, wrapper.getInt("id"));
                        assertEquals("Bob", wrapper.getString("name"));

                        assertFalse(wrapper.next());
                    }
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapperNavigation() throws SQLException {
        setupTestTable("hz_test_nav");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_nav;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {

                        assertTrue(wrapper.first());
                        assertEquals(1, wrapper.getInt("id"));

                        assertTrue(wrapper.last());
                        assertEquals(2, wrapper.getInt("id"));

                        assertTrue(wrapper.previous());
                        assertEquals(1, wrapper.getInt("id"));

                        assertTrue(wrapper.absolute(2));
                        assertEquals(2, wrapper.getInt("id"));

                        assertTrue(wrapper.relative(-1));
                        assertEquals(1, wrapper.getInt("id"));
                    }
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapperTypes() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_types;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE types_test (id INT, val DOUBLE, flag BOOLEAN, txt VARCHAR(50))");
                stmt.execute("INSERT INTO types_test VALUES (42, 3.14, true, 'hello')");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM types_test")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        assertTrue(wrapper.next());
                        assertEquals(42, wrapper.getInt("id"));
                        assertEquals(3.14, wrapper.getDouble("val"), 0.001);
                        assertTrue(wrapper.getBoolean("flag"));
                        assertEquals("hello", wrapper.getString("txt"));

                        assertEquals(42, wrapper.getInt(1));
                        assertEquals(3.14, wrapper.getDouble(2), 0.001);
                        assertTrue(wrapper.getBoolean(3));
                        assertEquals("hello", wrapper.getString(4));
                    }
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapperWasNull() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_null;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE null_test (id INT, val VARCHAR(50))");
                stmt.execute("INSERT INTO null_test VALUES (1, NULL)");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM null_test")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        assertTrue(wrapper.next());
                        assertNull(wrapper.getString("val"));
                        assertTrue(wrapper.wasNull());

                        assertEquals(1, wrapper.getInt("id"));
                        assertFalse(wrapper.wasNull());
                    }
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testCachedResultSetWrapperInvalidColumn() throws SQLException {
        setupTestTable("hz_test_invalid");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_invalid;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        wrapper.findColumn("nonexistent");
                    }
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testCachedResultSetWrapperInvalidCursor() throws SQLException {
        setupTestTable("hz_test_cursor");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:hz_test_cursor;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    HazelcastCachingDriver.SerializableCachedResultSet cached =
                        new HazelcastCachingDriver.SerializableCachedResultSet(rs);

                    try (HazelcastCachingDriver.CachedResultSetWrapper wrapper =
                            new HazelcastCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        wrapper.getInt(1);
                    }
                }
            }
        }
    }

    // Integration tests with embedded Hazelcast

    @Test
    public void testBasicConnection() throws SQLException {
        setupTestTable("hz_int_basic");

        String url = "jdbc:hazelcast[clusterName=test_basic]:jdbc:h2:mem:hz_int_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            assertNotNull(cache);
            assertNotNull(cache.getHazelcastInstance());
        }
    }

    @Test
    public void testQueryCaching() throws SQLException {
        setupTestTable("hz_int_query");

        String url = "jdbc:hazelcast[clusterName=test_query,mapName=query_cache]:jdbc:h2:mem:hz_int_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // First query - cache miss
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getMisses());
            assertEquals(0, cache.getHits());

            // Second query - cache hit
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getMisses());
            assertEquals(1, cache.getHits());
        }
    }

    @Test
    public void testCacheInvalidationOnUpdate() throws SQLException {
        setupTestTable("hz_int_update");

        String url = "jdbc:hazelcast[clusterName=test_update,mapName=update_cache]:jdbc:h2:mem:hz_int_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // Populate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.size());

            // Update should invalidate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testCacheInvalidationOnInsert() throws SQLException {
        setupTestTable("hz_int_insert");

        String url = "jdbc:hazelcast[clusterName=test_insert,mapName=insert_cache]:jdbc:h2:mem:hz_int_insert;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // Populate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.size());

            // Insert should invalidate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie')");
            }
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testCacheInvalidationOnDelete() throws SQLException {
        setupTestTable("hz_int_delete");

        String url = "jdbc:hazelcast[clusterName=test_delete,mapName=delete_cache]:jdbc:h2:mem:hz_int_delete;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // Populate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.size());

            // Delete should invalidate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = 2");
            }
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testDisableInvalidation() throws SQLException {
        setupTestTable("hz_int_noinvalidate");

        String url = "jdbc:hazelcast[clusterName=test_noinv,mapName=noinv_cache,invalidateOnWrite=false]:jdbc:h2:mem:hz_int_noinvalidate;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // Populate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.size());

            // Update should NOT invalidate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }
            assertEquals(1, cache.size());
        }
    }

    @Test
    public void testPreparedStatementCaching() throws SQLException {
        setupTestTable("hz_int_pstmt");

        String url = "jdbc:hazelcast[clusterName=test_pstmt,mapName=pstmt_cache]:jdbc:h2:mem:hz_int_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // First query with parameter
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getMisses());

            // Same query same parameter - cache hit
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
            assertEquals(1, cache.getHits());

            // Same query different parameter - cache miss
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
    public void testCacheStatistics() throws SQLException {
        setupTestTable("hz_int_stats");

        String url = "jdbc:hazelcast[clusterName=test_stats,mapName=stats_cache]:jdbc:h2:mem:hz_int_stats;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();
            cache.resetStats();

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // miss
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // hit
                stmt.executeQuery("SELECT * FROM users WHERE id = 2"); // miss
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // hit
            }

            assertEquals(2, cache.getMisses());
            assertEquals(2, cache.getHits());
            assertEquals(0.5, cache.getHitRatio(), 0.001);
        }
    }

    @Test
    public void testDisabledCache() throws SQLException {
        setupTestTable("hz_int_disabled");

        String url = "jdbc:hazelcast[clusterName=test_disabled,mapName=disabled_cache,enabled=false]:jdbc:h2:mem:hz_int_disabled;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.resetStats();

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM users");
            }

            assertEquals(0, cache.getHits());
            assertEquals(0, cache.getMisses());
        }
    }

    @Test
    public void testCacheTTL() throws SQLException, InterruptedException {
        setupTestTable("hz_int_ttl");

        // TTL of 1 second
        String url = "jdbc:hazelcast[clusterName=test_ttl,mapName=ttl_cache,ttl=1]:jdbc:h2:mem:hz_int_ttl;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);
            cache.clear();

            // First query
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.getMisses());

            // Immediate re-query - cache hit
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.getHits());

            // Wait for expiration
            Thread.sleep(1500);

            // Query after expiration - cache miss
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(2, cache.getMisses());
        }
    }

    @Test
    public void testCacheClear() throws SQLException {
        setupTestTable("hz_int_clear");

        String url = "jdbc:hazelcast[clusterName=test_clear,mapName=clear_cache]:jdbc:h2:mem:hz_int_clear;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            HazelcastCachingDriver.HazelcastQueryCache cache = HazelcastCachingDriver.getCache(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertTrue(cache.size() > 0);

            cache.clear();
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testInstanceReuse() throws SQLException {
        setupTestTable("hz_int_reuse");

        // Two connections with same cluster name should share the Hazelcast instance
        String url1 = "jdbc:hazelcast[clusterName=test_reuse,mapName=reuse_cache1]:jdbc:h2:mem:hz_int_reuse;DB_CLOSE_DELAY=-1";
        String url2 = "jdbc:hazelcast[clusterName=test_reuse,mapName=reuse_cache2]:jdbc:h2:mem:hz_int_reuse;DB_CLOSE_DELAY=-1";

        try (Connection conn1 = DriverManager.getConnection(url1);
             Connection conn2 = DriverManager.getConnection(url2)) {

            HazelcastCachingDriver.HazelcastQueryCache cache1 = HazelcastCachingDriver.getCache(conn1);
            HazelcastCachingDriver.HazelcastQueryCache cache2 = HazelcastCachingDriver.getCache(conn2);

            // Should be the same Hazelcast instance
            assertTrue(cache1.getHazelcastInstance() == cache2.getHazelcastInstance());
        }
    }
}
