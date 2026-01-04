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

import org.junit.BeforeClass;
import org.junit.Test;

public class CachingDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CachingDriver");
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
        CachingDriver driver = new CachingDriver();
        assertTrue(driver.acceptsURL("jdbc:cache:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:cache[ttl=60]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testQueryCaching() throws SQLException {
        setupTestTable("cache_test_caching");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_caching;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
        setupTestTable("cache_test_invalidate");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_invalidate;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
        setupTestTable("cache_test_insert");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_insert;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
        setupTestTable("cache_test_delete");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_delete;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
        setupTestTable("cache_test_no_invalidate");
        String url = "jdbc:cache[invalidateOnWrite=false]:jdbc:h2:mem:cache_test_no_invalidate;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
        setupTestTable("cache_test_pstmt");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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
    public void testCacheExpiration() throws SQLException, InterruptedException {
        setupTestTable("cache_test_ttl");
        // TTL of 1 second
        String url = "jdbc:cache[ttl=1]:jdbc:h2:mem:cache_test_ttl;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

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

    @Test
    public void testMaxSizeEviction() throws SQLException {
        setupTestTable("cache_test_maxsize");
        String url = "jdbc:cache[maxSize=2]:jdbc:h2:mem:cache_test_maxsize;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Fill cache
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1");
                stmt.executeQuery("SELECT * FROM users WHERE id = 2");
            }
            assertEquals(2, cache.size());
            assertEquals(0, cache.getEvictions());

            // Third query should evict oldest
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT name FROM users WHERE id = 1");
            }
            assertEquals(2, cache.size());
            assertEquals(1, cache.getEvictions());
        }
    }

    @Test
    public void testDisabledCache() throws SQLException {
        setupTestTable("cache_test_disabled");
        String url = "jdbc:cache[enabled=false]:jdbc:h2:mem:cache_test_disabled;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Queries should not be cached
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(0, cache.size());
            assertEquals(0, cache.getHits());
        }
    }

    @Test
    public void testCacheStatistics() throws SQLException {
        setupTestTable("cache_test_stats");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_stats;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Generate some activity
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // miss
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // hit
                stmt.executeQuery("SELECT * FROM users WHERE id = 2"); // miss
                stmt.executeQuery("SELECT * FROM users WHERE id = 1"); // hit
            }

            assertEquals(2, cache.getMisses());
            assertEquals(2, cache.getHits());
            assertEquals(0.5, cache.getHitRatio(), 0.001);

            cache.resetStats();
            assertEquals(0, cache.getMisses());
            assertEquals(0, cache.getHits());
        }
    }

    @Test
    public void testCachedResultSetNavigation() throws SQLException {
        setupTestTable("cache_test_navigation");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_navigation;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    // Test next()
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("id"));
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("id"));
                    assertFalse(rs.next());
                }

                // Get from cache and test navigation
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
                    assertTrue(rs.first());
                    assertEquals(1, rs.getInt("id"));
                    assertTrue(rs.last());
                    assertEquals(2, rs.getInt("id"));
                    assertTrue(rs.previous());
                    assertEquals(1, rs.getInt("id"));
                }
            }
        }
    }

    @Test
    public void testCachedResultSetTypes() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:cache_test_types;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS types_test (id INT, name VARCHAR(50), score DOUBLE, active BOOLEAN)");
                stmt.execute("DELETE FROM types_test");
                stmt.execute("INSERT INTO types_test VALUES (1, 'Test', 99.5, true)");
            }
        }

        String url = "jdbc:cache:jdbc:h2:mem:cache_test_types;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM types_test")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("id"));
                    assertEquals("Test", rs.getString("name"));
                    assertEquals(99.5, rs.getDouble("score"), 0.001);
                    assertTrue(rs.getBoolean("active"));
                }
            }
        }
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.LogDriver");
        setupTestTable("cache_test_chain");
        String url = "jdbc:cache:jdbc:log:jdbc:h2:mem:cache_test_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString("name"));
                }
            }
        }
    }

    @Test
    public void testConfigDefaults() {
        CachingDriver.CacheConfig config = new CachingDriver.CacheConfig("jdbc:cache:jdbc:h2:mem:test");
        assertEquals(60000, config.getTtlMs());
        assertEquals(1000, config.getMaxSize());
        assertTrue(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testCustomConfig() {
        CachingDriver.CacheConfig config = new CachingDriver.CacheConfig(
            "jdbc:cache[ttl=300,maxSize=5000,invalidateOnWrite=false,enabled=true]:jdbc:h2:mem:test"
        );
        assertEquals(300000, config.getTtlMs());
        assertEquals(5000, config.getMaxSize());
        assertFalse(config.isInvalidateOnWrite());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testNonSelectNotCached() throws SQLException {
        setupTestTable("cache_test_nonselect");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_nonselect;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE users SET name = 'Test' WHERE 1=0");
            }
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testCacheClear() throws SQLException {
        setupTestTable("cache_test_clear");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_clear;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(1, cache.size());

            cache.clear();
            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testColumnByIndex() throws SQLException {
        setupTestTable("cache_test_index");
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_index;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                    assertEquals("Alice", rs.getString(2));
                }
            }
        }
    }

    @Test
    public void testWasNull() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:cache_test_wasnull;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS nulltest (id INT, val VARCHAR(50))");
                stmt.execute("INSERT INTO nulltest VALUES (1, NULL)");
            }
        }

        String url = "jdbc:cache:jdbc:h2:mem:cache_test_wasnull;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM nulltest")) {
                    assertTrue(rs.next());
                    rs.getString("val");
                    assertTrue(rs.wasNull());
                }
            }
        }
    }

    @Test
    public void testTableAwareInvalidationConfig() {
        CachingDriver.CacheConfig config = new CachingDriver.CacheConfig(
            "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:test"
        );
        assertTrue(config.isTableAwareInvalidation());

        CachingDriver.CacheConfig configDefault = new CachingDriver.CacheConfig(
            "jdbc:cache:jdbc:h2:mem:test"
        );
        assertFalse(configDefault.isTableAwareInvalidation());
    }

    private void setupMultipleTables(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, user_id INT, total DECIMAL(10,2))");
                stmt.execute("DELETE FROM users");
                stmt.execute("DELETE FROM orders");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
                stmt.execute("INSERT INTO orders VALUES (1, 1, 100.00)");
                stmt.execute("INSERT INTO orders VALUES (2, 2, 200.00)");
            }
        }
    }

    @Test
    public void testTableAwareInvalidationOnlyAffectedTable() throws SQLException {
        setupMultipleTables("cache_test_table_aware");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries on both tables
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(2, cache.size());

            // Update users table - should only invalidate users query
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }

            // Users query should be invalidated, orders should remain
            assertEquals(1, cache.size());

            // Verify orders query is still cached (cache hit)
            long hitsBefore = cache.getHits();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(hitsBefore + 1, cache.getHits());
        }
    }

    @Test
    public void testTableAwareInvalidationInsert() throws SQLException {
        setupMultipleTables("cache_test_table_aware_insert");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware_insert;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries on both tables
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(2, cache.size());

            // Insert into orders - should only invalidate orders query
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO orders VALUES (3, 1, 50.00)");
            }

            assertEquals(1, cache.size());

            // Verify users query is still cached
            long hitsBefore = cache.getHits();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(hitsBefore + 1, cache.getHits());
        }
    }

    @Test
    public void testTableAwareInvalidationDelete() throws SQLException {
        setupMultipleTables("cache_test_table_aware_delete");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware_delete;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(2, cache.size());

            // Delete from users
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = 2");
            }

            assertEquals(1, cache.size());
        }
    }

    @Test
    public void testTableAwareInvalidationWithJoin() throws SQLException {
        setupMultipleTables("cache_test_table_aware_join");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware_join;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache a join query (depends on both tables)
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id");
            }
            assertEquals(1, cache.size());

            // Update users - should invalidate the join query
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }

            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testTableAwareInvalidationWithPreparedStatement() throws SQLException {
        setupMultipleTables("cache_test_table_aware_pstmt");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries using PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                pstmt.executeQuery();
            }
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM orders WHERE id = ?")) {
                pstmt.setInt(1, 1);
                pstmt.executeQuery();
            }
            assertEquals(2, cache.size());

            // Update users using PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
                pstmt.setString(1, "Updated");
                pstmt.setInt(2, 1);
                pstmt.executeUpdate();
            }

            // Only users query should be invalidated
            assertEquals(1, cache.size());
        }
    }

    @Test
    public void testTableAwareDisabledClearsAll() throws SQLException {
        setupMultipleTables("cache_test_table_aware_disabled");
        // Default: tableAwareInvalidation=false
        String url = "jdbc:cache:jdbc:h2:mem:cache_test_table_aware_disabled;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries on both tables
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(2, cache.size());

            // Update users - should clear ALL cache (not table-aware)
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice2' WHERE id = 1");
            }

            assertEquals(0, cache.size());
        }
    }

    @Test
    public void testTableAwareInvalidationTruncate() throws SQLException {
        setupMultipleTables("cache_test_table_aware_truncate");
        String url = "jdbc:cache[tableAwareInvalidation=true]:jdbc:h2:mem:cache_test_table_aware_truncate;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CachingDriver.QueryCache cache = CachingDriver.getCache(conn);

            // Cache queries
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
                stmt.executeQuery("SELECT * FROM orders");
            }
            assertEquals(2, cache.size());

            // Truncate orders
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("TRUNCATE TABLE orders");
            }

            // Only orders should be invalidated
            assertEquals(1, cache.size());

            // Verify users is still cached
            long hitsBefore = cache.getHits();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM users");
            }
            assertEquals(hitsBefore + 1, cache.getHits());
        }
    }
}
