package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class MemcachedCachingDriverTest {

    @SuppressWarnings("rawtypes")
    public static GenericContainer memcached = new GenericContainer(
        DockerImageName.parse("memcached:1.6-alpine"))
        .withExposedPorts(11211)
        .withReuse(true);

    // Unique prefix per test run to avoid collisions with parallel tests
    private static final String TEST_RUN_ID = String.valueOf(System.nanoTime());

    static {
        memcached.start();
    }

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.MemcachedCachingDriver");
    }

    private static String getMemcachedServer() {
        return memcached.getHost() + ":" + memcached.getFirstMappedPort();
    }

    private static String uniquePrefix(String base) {
        return TEST_RUN_ID + "_" + base;
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
        MemcachedCachingDriver driver = new MemcachedCachingDriver();
        assertTrue(driver.acceptsURL("jdbc:memcache:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:memcache[servers=localhost:11211]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:memcache[servers=cache1:11211;cache2:11211,ttl=60]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:cache:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:rediscache:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testConfigDefaults() {
        MemcachedCachingDriver.MemcachedCacheConfig config = new MemcachedCachingDriver.MemcachedCacheConfig(
            "jdbc:memcache:jdbc:h2:mem:test"
        );
        assertEquals("localhost:11211", config.getServers());
        assertEquals("pjdbc:", config.getKeyPrefix());
        assertEquals(60, config.getTtlSeconds());
        assertTrue(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testCustomConfig() {
        MemcachedCachingDriver.MemcachedCacheConfig config = new MemcachedCachingDriver.MemcachedCacheConfig(
            "jdbc:memcache[servers=cache1:11211;cache2:11212,keyPrefix=myapp:,ttl=300,invalidateOnWrite=false,enabled=true]:jdbc:h2:mem:test"
        );
        assertEquals("cache1:11211;cache2:11212", config.getServers());
        assertEquals("myapp:", config.getKeyPrefix());
        assertEquals(300, config.getTtlSeconds());
        assertFalse(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testServerAddressParsing() {
        MemcachedCachingDriver.MemcachedCacheConfig config = new MemcachedCachingDriver.MemcachedCacheConfig(
            "jdbc:memcache[servers=cache1:11211;cache2:11212;cache3:11213]:jdbc:h2:mem:test"
        );
        List<InetSocketAddress> addresses = config.getServerAddresses();
        assertEquals(3, addresses.size());
        assertEquals("cache1", addresses.get(0).getHostName());
        assertEquals(11211, addresses.get(0).getPort());
        assertEquals("cache2", addresses.get(1).getHostName());
        assertEquals(11212, addresses.get(1).getPort());
        assertEquals("cache3", addresses.get(2).getHostName());
        assertEquals(11213, addresses.get(2).getPort());
    }

    @Test
    public void testServerAddressDefaultPort() {
        MemcachedCachingDriver.MemcachedCacheConfig config = new MemcachedCachingDriver.MemcachedCacheConfig(
            "jdbc:memcache[servers=cache1]:jdbc:h2:mem:test"
        );
        List<InetSocketAddress> addresses = config.getServerAddresses();
        assertEquals(1, addresses.size());
        assertEquals("cache1", addresses.get(0).getHostName());
        assertEquals(11211, addresses.get(0).getPort()); // Default port
    }

    @Test
    public void testSafeResultSetSerializerSerialization() throws SQLException, IOException {
        setupTestTable("memcache_test_serialize");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_serialize;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    // Serialize
                    byte[] data = SafeResultSetSerializer.serialize(cached);
                    assertNotNull(data);
                    assertTrue(data.length > 0);

                    // Deserialize
                    SafeResultSetSerializer.CachedData restored =
                        SafeResultSetSerializer.deserialize(data);
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
        setupTestTable("memcache_test_wrapper");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_wrapper;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    // Create wrapper
                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {

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
        setupTestTable("memcache_test_nav");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_nav;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {

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
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_types;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE types_test (id INT, val DOUBLE, flag BOOLEAN, txt VARCHAR(50))");
                stmt.execute("INSERT INTO types_test VALUES (42, 3.14, true, 'hello')");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM types_test")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {
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
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_null;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE null_test (id INT, val VARCHAR(50))");
                stmt.execute("INSERT INTO null_test VALUES (1, NULL)");

                try (ResultSet rs = stmt.executeQuery("SELECT * FROM null_test")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {
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
        setupTestTable("memcache_test_findcol");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_findcol;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM users")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {
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
        setupTestTable("memcache_test_invalid");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_invalid;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        wrapper.findColumn("nonexistent");
                    }
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testCachedResultSetWrapperInvalidCursor() throws SQLException {
        setupTestTable("memcache_test_cursor");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:memcache_test_cursor;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM users")) {
                    SafeResultSetSerializer.CachedData cached =
                        SafeResultSetSerializer.fromResultSet(rs);

                    try (MemcachedCachingDriver.CachedResultSetWrapper wrapper =
                            new MemcachedCachingDriver.CachedResultSetWrapper(stmt, cached)) {
                        // Don't call next(), cursor is before first row
                        wrapper.getInt(1);
                    }
                }
            }
        }
    }

    // Integration tests - require Memcached running

    @Test
    public void testBasicConnectionWithMemcached() throws SQLException {
        setupTestTable("memcache_int_basic");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s]:jdbc:h2:mem:memcache_int_basic;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_basic:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
            assertNotNull(cache);
        }
    }

    @Test
    public void testQueryCachingWithMemcached() throws SQLException {
        setupTestTable("memcache_int_query");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s]:jdbc:h2:mem:memcache_int_query;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_query:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
    public void testCacheInvalidationWithMemcached() throws SQLException {
        setupTestTable("memcache_int_invalidate");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s]:jdbc:h2:mem:memcache_int_invalidate;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_inv:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
    public void testPreparedStatementCachingWithMemcached() throws SQLException {
        setupTestTable("memcache_int_pstmt");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s]:jdbc:h2:mem:memcache_int_pstmt;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_pstmt:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
    public void testCacheStatisticsWithMemcached() throws SQLException {
        setupTestTable("memcache_int_stats");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s]:jdbc:h2:mem:memcache_int_stats;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_stats:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
    public void testDisabledCacheWithMemcached() throws SQLException {
        setupTestTable("memcache_int_disabled");

        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s,enabled=false]:jdbc:h2:mem:memcache_int_disabled;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_disabled:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
    public void testCacheTTLWithMemcached() throws SQLException, InterruptedException {
        setupTestTable("memcache_int_ttl");

        // TTL of 1 second
        String url = String.format(
            "jdbc:memcache[servers=%s,keyPrefix=%s,ttl=1]:jdbc:h2:mem:memcache_int_ttl;DB_CLOSE_DELAY=-1",
            getMemcachedServer(), uniquePrefix("test_ttl:"));
        try (Connection conn = DriverManager.getConnection(url)) {
            MemcachedCachingDriver.MemcachedQueryCache cache = MemcachedCachingDriver.getCache(conn);
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
