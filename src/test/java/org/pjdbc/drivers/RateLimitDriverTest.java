package org.pjdbc.drivers;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.drivers.RateLimitDriver.Mode;
import org.pjdbc.drivers.RateLimitDriver.RateLimiter;

public class RateLimitDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.RateLimitDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
    }

    // ========== URL Acceptance Tests ==========

    @Test
    public void testAcceptsURL() throws SQLException {
        RateLimitDriver driver = new RateLimitDriver();
        assertTrue(driver.acceptsURL("jdbc:ratelimit:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:ratelimit[maxRequests=50]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:ratelimit[maxRequests=10,windowMs=500,mode=wait]:jdbc:postgresql://localhost/db"));
        assertFalse(driver.acceptsURL("jdbc:timeout:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:h2:mem:test"));
    }

    // ========== Configuration Tests ==========

    @Test
    public void testDefaultConfig() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit:jdbc:h2:mem:test");
        assertEquals(100, rl.getMaxRequests());
        assertEquals(1000, rl.getWindowMs());
        assertEquals(Mode.REJECT, rl.getMode());
        assertEquals(100, rl.getBurstSize());
    }

    @Test
    public void testCustomMaxRequests() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=50]:jdbc:h2:mem:test");
        assertEquals(50, rl.getMaxRequests());
    }

    @Test
    public void testCustomWindowMs() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[windowMs=500]:jdbc:h2:mem:test");
        assertEquals(500, rl.getWindowMs());
    }

    @Test
    public void testWaitMode() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[mode=wait]:jdbc:h2:mem:test");
        assertEquals(Mode.WAIT, rl.getMode());
    }

    @Test
    public void testRejectMode() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[mode=reject]:jdbc:h2:mem:test");
        assertEquals(Mode.REJECT, rl.getMode());
    }

    @Test
    public void testCustomBurstSize() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=10,burstSize=50]:jdbc:h2:mem:test");
        assertEquals(10, rl.getMaxRequests());
        assertEquals(50, rl.getBurstSize());
    }

    @Test
    public void testBurstSizeLowerThanMaxRequestsIsUpgraded() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=50,burstSize=10]:jdbc:h2:mem:test");
        assertEquals(50, rl.getMaxRequests());
        assertEquals(50, rl.getBurstSize()); // Burst should be at least maxRequests
    }

    @Test
    public void testMultipleParameters() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=25,windowMs=2000,mode=wait,burstSize=100]:jdbc:h2:mem:test");
        assertEquals(25, rl.getMaxRequests());
        assertEquals(2000, rl.getWindowMs());
        assertEquals(Mode.WAIT, rl.getMode());
        assertEquals(100, rl.getBurstSize());
    }

    @Test
    public void testInvalidValuesUseDefaults() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=invalid,windowMs=bad]:jdbc:h2:mem:test");
        assertEquals(100, rl.getMaxRequests());
        assertEquals(1000, rl.getWindowMs());
    }

    @Test
    public void testNegativeValuesUseDefaults() {
        RateLimiter rl = new RateLimiter("jdbc:ratelimit[maxRequests=-5,windowMs=-1000]:jdbc:h2:mem:test");
        assertEquals(100, rl.getMaxRequests());
        assertEquals(1000, rl.getWindowMs());
    }

    @Test
    public void testRateLimiterToString() {
        RateLimiter rl = new RateLimiter(10, 1000, Mode.REJECT, 20);
        String str = rl.toString();
        assertTrue(str.contains("10"));
        assertTrue(str.contains("1000"));
        assertTrue(str.contains("REJECT"));
    }

    // ========== Token Bucket Tests ==========

    @Test
    public void testTryAcquireConsumesToken() {
        RateLimiter rl = new RateLimiter(10, 1000, Mode.REJECT, 10);
        assertEquals(10, rl.getAvailableTokens());

        assertTrue(rl.tryAcquire());
        assertEquals(9, rl.getAvailableTokens());

        assertTrue(rl.tryAcquire());
        assertEquals(8, rl.getAvailableTokens());
    }

    @Test
    public void testTryAcquireFailsWhenNoTokens() {
        RateLimiter rl = new RateLimiter(2, 10000, Mode.REJECT, 2);

        assertTrue(rl.tryAcquire());
        assertTrue(rl.tryAcquire());
        assertFalse(rl.tryAcquire()); // No more tokens
    }

    @Test
    public void testTokensRefill() throws InterruptedException {
        RateLimiter rl = new RateLimiter(5, 100, Mode.REJECT, 5);

        // Consume all tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rl.tryAcquire());
        }
        assertEquals(0, rl.getAvailableTokens());

        // Wait for refill
        Thread.sleep(150);

        // Should have tokens again
        assertTrue(rl.getAvailableTokens() > 0);
    }

    @Test
    public void testBurstCapacity() {
        RateLimiter rl = new RateLimiter(5, 1000, Mode.REJECT, 10);
        assertEquals(10, rl.getAvailableTokens()); // Starts with burst size
    }

    @Test
    public void testStatistics() throws SQLException {
        RateLimiter rl = new RateLimiter(5, 10000, Mode.REJECT, 5);

        // Acquire some tokens
        rl.acquire();
        rl.acquire();
        rl.acquire();

        assertEquals(3, rl.getTotalRequests());
        assertEquals(0, rl.getRejectedRequests());
    }

    @Test
    public void testRejectedRequestsTracked() {
        RateLimiter rl = new RateLimiter(2, 10000, Mode.REJECT, 2);

        // Consume all tokens
        try { rl.acquire(); } catch (SQLException e) { fail(); }
        try { rl.acquire(); } catch (SQLException e) { fail(); }

        // This should be rejected
        try {
            rl.acquire();
            fail("Should have thrown SQLException");
        } catch (SQLException e) {
            // Expected
        }

        assertEquals(3, rl.getTotalRequests());
        assertEquals(1, rl.getRejectedRequests());
    }

    @Test
    public void testReset() throws SQLException {
        RateLimiter rl = new RateLimiter(5, 10000, Mode.REJECT, 5);

        rl.acquire();
        rl.acquire();
        assertEquals(3, rl.getAvailableTokens());
        assertEquals(2, rl.getTotalRequests());

        rl.reset();
        assertEquals(5, rl.getAvailableTokens());
        assertEquals(0, rl.getTotalRequests());
    }

    // ========== Integration Tests ==========

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:ratelimit:jdbc:h2:mem:rl_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertNotNull(rl);
            assertEquals(100, rl.getMaxRequests());
        }
    }

    @Test
    public void testQueryExecution() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=50]:jdbc:h2:mem:rl_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42 AS answer")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("answer"));
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(1, rl.getTotalRequests());
        }
    }

    @Test
    public void testRateLimitExceeded() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=3,windowMs=10000]:jdbc:h2:mem:rl_exceed;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // First 3 should succeed
                stmt.executeQuery("SELECT 1");
                stmt.executeQuery("SELECT 2");
                stmt.executeQuery("SELECT 3");

                // 4th should fail
                try {
                    stmt.executeQuery("SELECT 4");
                    fail("Should have thrown rate limit exception");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("Rate limit exceeded"));
                }
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(4, rl.getTotalRequests());
            assertEquals(1, rl.getRejectedRequests());
        }
    }

    @Test
    public void testWaitModeBlocks() throws SQLException, InterruptedException {
        String url = "jdbc:ratelimit[maxRequests=2,windowMs=100,mode=wait]:jdbc:h2:mem:rl_wait;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                long start = System.currentTimeMillis();

                // First 2 should be immediate
                stmt.executeQuery("SELECT 1");
                stmt.executeQuery("SELECT 2");

                // 3rd should block until tokens refill
                stmt.executeQuery("SELECT 3");

                long elapsed = System.currentTimeMillis() - start;
                // Should have waited at least one window period
                assertTrue("Should have waited for rate limit, elapsed: " + elapsed, elapsed >= 50);

                RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
                assertTrue(rl.getWaitedRequests() > 0);
            }
        }
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=10]:jdbc:h2:mem:rl_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
                stmt.execute("INSERT INTO test VALUES (1, 'Alice')");
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString(1));
                }
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertTrue(rl.getTotalRequests() >= 3); // At least CREATE, INSERT, SELECT
        }
    }

    @Test
    public void testUpdateExecution() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=10]:jdbc:h2:mem:rl_update;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, val VARCHAR(100))");

                int rows = stmt.executeUpdate("INSERT INTO test VALUES (1, 'test')");
                assertEquals(1, rows);
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(2, rl.getTotalRequests());
        }
    }

    @Test
    public void testDriverChaining() throws SQLException {
        // Chain: ratelimit -> log -> h2
        String url = "jdbc:ratelimit[maxRequests=50]:jdbc:log:jdbc:h2:mem:rl_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 123")) {
                assertTrue(rs.next());
                assertEquals(123, rs.getInt(1));
            }
        }
    }

    @Test
    public void testBatchExecution() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=10]:jdbc:h2:mem:rl_batch;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");

                stmt.addBatch("INSERT INTO test VALUES (1)");
                stmt.addBatch("INSERT INTO test VALUES (2)");
                stmt.addBatch("INSERT INTO test VALUES (3)");

                int[] results = stmt.executeBatch();
                assertEquals(3, results.length);
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(2, rl.getTotalRequests()); // CREATE + batch (counts as 1)
        }
    }

    @Test
    public void testGetRateLimiterFromNonRateLimitConnection() throws SQLException {
        String url = "jdbc:h2:mem:rl_other;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertNull(rl);
        }
    }

    @Test
    public void testMultipleStatements() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=10]:jdbc:h2:mem:rl_multi;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Each statement execution should count against the rate limit
            try (Statement stmt1 = conn.createStatement()) {
                stmt1.executeQuery("SELECT 1");
            }

            try (Statement stmt2 = conn.createStatement()) {
                stmt2.executeQuery("SELECT 2");
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(2, rl.getTotalRequests());
        }
    }

    @Test
    public void testHighVolumeWithinLimit() throws SQLException {
        String url = "jdbc:ratelimit[maxRequests=100,burstSize=100]:jdbc:h2:mem:rl_volume;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Execute 50 queries - should all succeed
                for (int i = 0; i < 50; i++) {
                    stmt.executeQuery("SELECT " + i);
                }
            }

            RateLimiter rl = RateLimitDriver.getRateLimiter(conn);
            assertEquals(50, rl.getTotalRequests());
            assertEquals(0, rl.getRejectedRequests());
        }
    }
}
