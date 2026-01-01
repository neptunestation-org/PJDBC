package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisCachingDriverTest {

    @ClassRule
    @SuppressWarnings("rawtypes")
    public static GenericContainer redis = new GenericContainer(
        DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

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
        RedisCachingDriver driver = new RedisCachingDriver();
        assertTrue(driver.acceptsURL("jdbc:rediscache:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:rediscache[host=redis.example.com]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:rediscache[host=localhost,port=6379,ttl=60]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:cache:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testConfigDefaults() {
        RedisCachingDriver.RedisCacheConfig config = new RedisCachingDriver.RedisCacheConfig(
            "jdbc:rediscache:jdbc:h2:mem:test"
        );
        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertNull(config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals("pjdbc:", config.getKeyPrefix());
        assertEquals(60, config.getTtlSeconds());
        assertEquals(8, config.getMaxPoolSize());
        assertTrue(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testCustomConfig() {
        RedisCachingDriver.RedisCacheConfig config = new RedisCachingDriver.RedisCacheConfig(
            "jdbc:rediscache[host=redis.example.com,port=6380,password=secret,database=2,keyPrefix=myapp:,ttl=300,maxPoolSize=16,invalidateOnWrite=false,enabled=true]:jdbc:h2:mem:test"
        );
        assertEquals("redis.example.com", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals("secret", config.getPassword());
        assertEquals(2, config.getDatabase());
        assertEquals("myapp:", config.getKeyPrefix());
        assertEquals(300, config.getTtlSeconds());
        assertEquals(16, config.getMaxPoolSize());
        assertFalse(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testSerializableCachedResultSetSerialization() throws SQLException, IOException, ClassNotFoundException {
        setupTestTable("redis_test_serialize");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_serialize;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    // Serialize
                    byte[] data = cached.serialize();
                    assertNotNull(data);
                    assertTrue(data.length > 0);

                    // Deserialize
                    RedisCachingDriver.SerializableCachedResultSet restored =
                        RedisCachingDriver.SerializableCachedResultSet.deserialize(data);
                    assertNotNull(restored);
                    assertEquals(2, restored.getRowCount());
                    assertEquals(2, restored.getColumnNames().length);
                    assertEquals("ID", restored.getColumnNames()[0]);
                    assertEquals("NAME", restored.getColumnNames()[1]);
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapper() throws SQLException {
        setupTestTable("redis_test_wrapper");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_wrapper;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    // Create wrapper
                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {

                        // Test navigation
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
        setupTestTable("redis_test_nav");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_nav;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {

                        // Test first/last
                        assertTrue(wrapper.first());
                        assertEquals(1, wrapper.getInt("id"));

                        assertTrue(wrapper.last());
                        assertEquals(2, wrapper.getInt("id"));

                        // Test previous
                        assertTrue(wrapper.previous());
                        assertEquals(1, wrapper.getInt("id"));

                        // Test absolute
                        assertTrue(wrapper.absolute(2));
                        assertEquals(2, wrapper.getInt("id"));

                        // Test relative
                        assertTrue(wrapper.relative(-1));
                        assertEquals(1, wrapper.getInt("id"));
                    }
                }
            }
        }
    }

    @Test
    public void testCachedResultSetWrapperTypes() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_types;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE types_test (id INT, val DOUBLE, flag BOOLEAN, txt VARCHAR(50))");
                stmt.execute("INSERT INTO types_test VALUES (42, 3.14, true, 'hello')");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM types_test")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        assertTrue(wrapper.next());
                        assertEquals(42, wrapper.getInt("id"));
                        assertEquals(3.14, wrapper.getDouble("val"), 0.001);
                        assertTrue(wrapper.getBoolean("flag"));
                        assertEquals("hello", wrapper.getString("txt"));

                        // Test by index
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
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_null;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE null_test (id INT, val VARCHAR(50))");
                stmt.execute("INSERT INTO null_test VALUES (1, NULL)");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM null_test")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {
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

    @Test
    public void testCachedResultSetWrapperFindColumn() throws SQLException {
        setupTestTable("redis_test_findcol");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_findcol;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM users")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        assertEquals(1, wrapper.findColumn("id"));
                        assertEquals(1, wrapper.findColumn("ID"));
                        assertEquals(2, wrapper.findColumn("name"));
                        assertEquals(2, wrapper.findColumn("NAME"));
                    }
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testCachedResultSetWrapperInvalidColumn() throws SQLException {
        setupTestTable("redis_test_invalid");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_invalid;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        wrapper.findColumn("nonexistent");
                    }
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testCachedResultSetWrapperInvalidCursor() throws SQLException {
        setupTestTable("redis_test_cursor");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:redis_test_cursor;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    RedisCachingDriver.SerializableCachedResultSet cached =
                        new RedisCachingDriver.SerializableCachedResultSet(rs);

                    try (RedisCachingDriver.CachedResultSetWrapper wrapper =
                            new RedisCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        // Don't call next(), cursor is before first row
                        wrapper.getInt(1);
                    }
                }
            }
        }
    }

    // Integration tests - require Redis running

    @Test
    public void testBasicConnectionWithRedis() throws SQLException {
        setupTestTable("redis_int_basic");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_basic:]:jdbc:h2:mem:redis_int_basic;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            assertNotNull(cache);
        }
    }

    @Test
    public void testQueryCachingWithRedis() throws SQLException {
        setupTestTable("redis_int_query");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_query:]:jdbc:h2:mem:redis_int_query;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear(); // Clear any existing entries

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
    public void testCacheInvalidationWithRedis() throws SQLException {
        setupTestTable("redis_int_invalidate");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_inv:]:jdbc:h2:mem:redis_int_invalidate;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.clear();

            // Populate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.getMisses());

            // Update should invalidate cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }

            // Query again - should be cache miss
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(2, cache.getMisses());
        }
    }

    @Test
    public void testPreparedStatementCachingWithRedis() throws SQLException {
        setupTestTable("redis_int_pstmt");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_pstmt:]:jdbc:h2:mem:redis_int_pstmt;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
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
    public void testCacheStatisticsWithRedis() throws SQLException {
        setupTestTable("redis_int_stats");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_stats:]:jdbc:h2:mem:redis_int_stats;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
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
    public void testDisabledCacheWithRedis() throws SQLException {
        setupTestTable("redis_int_disabled");

        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_disabled:,enabled=false]:jdbc:h2:mem:redis_int_disabled;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
            cache.resetStats();

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM users");
            }

            assertEquals(0, cache.getHits());
            assertEquals(0, cache.getMisses()); // Disabled cache doesn't track
        }
    }

    @Test
    public void testCacheTTLWithRedis() throws SQLException, InterruptedException {
        setupTestTable("redis_int_ttl");

        // TTL of 1 second
        String url = String.format(
            "jdbc:rediscache[host=%s,port=%d,keyPrefix=test_ttl:,ttl=1]:jdbc:h2:mem:redis_int_ttl;DB_CLOSE_DELAY=-1",
            getRedisHost(), getRedisPort());
        try (Connection conn = DriverManager.getConnection(url)) {
            RedisCachingDriver.RedisQueryCache cache = RedisCachingDriver.getCache(conn);
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
            Thread.sleep(1100);

            // Query after expiration - cache miss
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(2, cache.getMisses());
        }
    }
}
