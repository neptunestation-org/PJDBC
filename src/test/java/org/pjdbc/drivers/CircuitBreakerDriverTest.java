package org.pjdbc.drivers;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.drivers.CircuitBreakerDriver.CircuitBreaker;
import org.pjdbc.drivers.CircuitBreakerDriver.State;

public class CircuitBreakerDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CircuitBreakerDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
        Class.forName("org.pjdbc.drivers.ChaosDriver");
    }

    // ========== URL Acceptance Tests ==========

    @Test
    public void testAcceptsURL() throws SQLException {
        CircuitBreakerDriver driver = new CircuitBreakerDriver();
        assertTrue(driver.acceptsURL("jdbc:circuitbreaker:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:circuitbreaker[failureThreshold=3]:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:circuitbreaker[name=mydb,resetTimeout=60000]:jdbc:postgresql://localhost/db"));
        assertFalse(driver.acceptsURL("jdbc:retry:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:h2:mem:test"));
    }

    // ========== Configuration Tests ==========

    @Test
    public void testDefaultConfig() {
        CircuitBreaker cb = new CircuitBreaker("jdbc:circuitbreaker:jdbc:h2:mem:test");
        assertEquals("default", cb.getName());
        assertEquals(5, cb.getFailureThreshold());
        assertEquals(1, cb.getSuccessThreshold());
        assertEquals(30000, cb.getResetTimeout());
    }

    @Test
    public void testCustomConfig() {
        CircuitBreaker cb = new CircuitBreaker(
            "jdbc:circuitbreaker[name=mydb,failureThreshold=3,successThreshold=2,resetTimeout=60000]:jdbc:h2:mem:test"
        );
        assertEquals("mydb", cb.getName());
        assertEquals(3, cb.getFailureThreshold());
        assertEquals(2, cb.getSuccessThreshold());
        assertEquals(60000, cb.getResetTimeout());
    }

    // ========== Circuit Breaker State Machine Tests ==========

    @Test
    public void testInitialState() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1, 1000);
        assertEquals(State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    public void testClosedToOpen() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1, 1000);

        // Record failures up to threshold
        cb.recordFailure();
        assertEquals(State.CLOSED, cb.getState());
        cb.recordFailure();
        assertEquals(State.CLOSED, cb.getState());
        cb.recordFailure();

        // Should now be OPEN
        assertEquals(State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    public void testOpenToHalfOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 2, 1, 100); // 100ms timeout

        // Open the circuit
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());

        // Wait for reset timeout
        Thread.sleep(150);

        // Should transition to HALF_OPEN on next allowRequest
        assertTrue(cb.allowRequest());
        assertEquals(State.HALF_OPEN, cb.getState());
    }

    @Test
    public void testHalfOpenToClosed() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 2, 2, 100); // Need 2 successes

        // Open the circuit
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(State.OPEN, cb.getState());

        // Wait for reset timeout
        Thread.sleep(150);
        cb.allowRequest(); // Transition to HALF_OPEN
        assertEquals(State.HALF_OPEN, cb.getState());

        // Record successes
        cb.recordSuccess();
        assertEquals(State.HALF_OPEN, cb.getState()); // Still half-open
        cb.recordSuccess();
        assertEquals(State.CLOSED, cb.getState()); // Now closed
    }

    @Test
    public void testHalfOpenToOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 2, 2, 100);

        // Open the circuit
        cb.recordFailure();
        cb.recordFailure();

        // Wait and transition to HALF_OPEN
        Thread.sleep(150);
        cb.allowRequest();
        assertEquals(State.HALF_OPEN, cb.getState());

        // One success
        cb.recordSuccess();
        assertEquals(State.HALF_OPEN, cb.getState());

        // Failure in half-open immediately opens
        cb.recordFailure();
        assertEquals(State.OPEN, cb.getState());
    }

    @Test
    public void testSuccessResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1, 1000);

        cb.recordFailure();
        cb.recordFailure();
        assertEquals(2, cb.getFailureCount());

        cb.recordSuccess();
        assertEquals(0, cb.getFailureCount());
        assertEquals(State.CLOSED, cb.getState());
    }

    // ========== Statistics Tests ==========

    @Test
    public void testStatistics() {
        CircuitBreaker cb = new CircuitBreaker("test", 5, 1, 1000);

        cb.recordSuccess();
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordRejection();

        assertEquals(4, cb.getTotalRequests());
        assertEquals(1, cb.getTotalFailures());
        assertEquals(1, cb.getTotalRejections());
    }

    @Test
    public void testReset() {
        CircuitBreaker cb = new CircuitBreaker("test", 2, 1, 1000);

        cb.recordFailure();
        cb.recordFailure();
        assertEquals(State.OPEN, cb.getState());

        cb.reset();
        assertEquals(State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
        assertEquals(0, cb.getTotalRequests());
    }

    // ========== Integration Tests ==========

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:circuitbreaker:jdbc:h2:mem:cb_basic;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);

            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);
            assertNotNull(cb);
            assertEquals(State.CLOSED, cb.getState());
        }
    }

    @Test
    public void testQueryExecution() throws SQLException {
        String url = "jdbc:circuitbreaker:jdbc:h2:mem:cb_query;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42 AS answer")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("answer"));
            }

            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);
            assertEquals(1, cb.getTotalRequests());
            assertEquals(0, cb.getTotalFailures());
        }
    }

    @Test
    public void testFailureTracking() throws SQLException {
        String url = "jdbc:circuitbreaker[failureThreshold=3]:jdbc:h2:mem:cb_fail;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);

            // Execute failing queries
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < 2; i++) {
                    try {
                        stmt.executeQuery("SELECT * FROM nonexistent_table");
                        fail("Should have thrown");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }

            assertEquals(2, cb.getFailureCount());
            assertEquals(State.CLOSED, cb.getState()); // Still closed (threshold is 3)
        }
    }

    @Test
    public void testCircuitOpens() throws SQLException {
        String url = "jdbc:circuitbreaker[failureThreshold=2]:jdbc:h2:mem:cb_open;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);

            // Cause failures to open circuit
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < 2; i++) {
                    try {
                        stmt.executeQuery("SELECT * FROM nonexistent_table");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }

            assertEquals(State.OPEN, cb.getState());

            // Next request should fail fast
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
                fail("Should have failed fast");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("OPEN"));
                assertTrue(e.getMessage().contains("failing fast"));
            }

            assertEquals(1, cb.getTotalRejections());
        }
    }

    @Test
    public void testCircuitRecovery() throws SQLException, InterruptedException {
        String url = "jdbc:circuitbreaker[failureThreshold=2,resetTimeout=100]:jdbc:h2:mem:cb_recover;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);

            // Open the circuit
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < 2; i++) {
                    try {
                        stmt.executeQuery("SELECT * FROM nonexistent_table");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }
            assertEquals(State.OPEN, cb.getState());

            // Wait for reset timeout
            Thread.sleep(150);

            // Next successful request should transition to HALF_OPEN then CLOSED
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }

            assertEquals(State.CLOSED, cb.getState());
        }
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        String url = "jdbc:circuitbreaker:jdbc:h2:mem:cb_pstmt;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
                stmt.execute("INSERT INTO test VALUES (1, 'Alice')");
            }

            try (var pstmt = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
                pstmt.setInt(1, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Alice", rs.getString(1));
                }
            }

            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);
            assertTrue(cb.getTotalRequests() >= 1);
            assertEquals(State.CLOSED, cb.getState());
        }
    }

    @Test
    public void testDriverChaining() throws SQLException {
        // Chain: circuitbreaker -> cat -> h2
        String url = "jdbc:circuitbreaker:jdbc:cat:jdbc:h2:mem:cb_chain;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 123")) {
                assertTrue(rs.next());
                assertEquals(123, rs.getInt(1));
            }
        }
    }

    @Test
    public void testForceState() {
        CircuitBreaker cb = new CircuitBreaker("test", 5, 1, 1000);

        cb.forceState(State.OPEN);
        assertEquals(State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());

        cb.forceState(State.CLOSED);
        assertEquals(State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    public void testToString() {
        CircuitBreaker cb = new CircuitBreaker("mydb", 3, 2, 1000);
        String str = cb.toString();
        assertTrue(str.contains("mydb"));
        assertTrue(str.contains("CLOSED"));
        assertTrue(str.contains("failures=0/3"));
    }

    @Test
    public void testGetCircuitBreakerFromNonCircuitBreakerConnection() throws SQLException {
        String url = "jdbc:h2:mem:cb_other;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            CircuitBreaker cb = CircuitBreakerDriver.getCircuitBreaker(conn);
            assertNull(cb);
        }
    }
}
