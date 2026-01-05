package org.pjdbc.sql;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PjdbcListeners and PjdbcEventListener.
 */
class PjdbcListenersTest {

    @BeforeEach
    void setUp() {
        PjdbcListeners.clear();
    }

    @AfterEach
    void tearDown() {
        PjdbcListeners.clear();
    }

    @Test
    void testRegisterAndUnregister() {
        PjdbcEventListener listener = new PjdbcEventListener() {};

        assertEquals(0, PjdbcListeners.listenerCount());

        PjdbcListeners.register(listener);
        assertEquals(1, PjdbcListeners.listenerCount());

        // Registering same listener again should not add duplicate
        PjdbcListeners.register(listener);
        assertEquals(1, PjdbcListeners.listenerCount());

        assertTrue(PjdbcListeners.unregister(listener));
        assertEquals(0, PjdbcListeners.listenerCount());

        // Unregistering again should return false
        assertFalse(PjdbcListeners.unregister(listener));
    }

    @Test
    void testRetryEvent() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<String> messages = new ArrayList<>();

        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onRetry(String sql, SQLException cause, int attempt, long delayMs) {
                callCount.incrementAndGet();
                messages.add(String.format("attempt=%d, delay=%d, sqlState=%s",
                    attempt, delayMs, cause.getSQLState()));
            }
        });

        PjdbcListeners.fireRetry("SELECT 1", new SQLException("Test", "08001"), 1, 100);
        PjdbcListeners.fireRetry("SELECT 2", new SQLException("Test", "40001"), 2, 200);

        assertEquals(2, callCount.get());
        assertEquals("attempt=1, delay=100, sqlState=08001", messages.get(0));
        assertEquals("attempt=2, delay=200, sqlState=40001", messages.get(1));
    }

    @Test
    void testCircuitBreakerStateChangeEvent() {
        List<String> transitions = new ArrayList<>();

        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onCircuitBreakerStateChange(String name, String oldState, String newState) {
                transitions.add(String.format("%s: %s -> %s", name, oldState, newState));
            }
        });

        PjdbcListeners.fireCircuitBreakerStateChange("db-circuit", "CLOSED", "OPEN");
        PjdbcListeners.fireCircuitBreakerStateChange("db-circuit", "OPEN", "HALF_OPEN");
        PjdbcListeners.fireCircuitBreakerStateChange("db-circuit", "HALF_OPEN", "CLOSED");

        assertEquals(3, transitions.size());
        assertEquals("db-circuit: CLOSED -> OPEN", transitions.get(0));
        assertEquals("db-circuit: OPEN -> HALF_OPEN", transitions.get(1));
        assertEquals("db-circuit: HALF_OPEN -> CLOSED", transitions.get(2));
    }

    @Test
    void testCircuitBreakerRejectionEvent() {
        AtomicInteger rejectionCount = new AtomicInteger(0);

        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onCircuitBreakerRejection(String name, String state) {
                rejectionCount.incrementAndGet();
            }
        });

        PjdbcListeners.fireCircuitBreakerRejection("db-circuit", "OPEN");
        PjdbcListeners.fireCircuitBreakerRejection("db-circuit", "OPEN");

        assertEquals(2, rejectionCount.get());
    }

    @Test
    void testSqlTransformedEvent() {
        List<String> transformations = new ArrayList<>();

        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onSqlTransformed(String original, String transformed) {
                transformations.add(original + " -> " + transformed);
            }
        });

        PjdbcListeners.fireSqlTransformed("SELECT * FROM users", "SELECT * FROM users_v2");

        assertEquals(1, transformations.size());
        assertEquals("SELECT * FROM users -> SELECT * FROM users_v2", transformations.get(0));
    }

    @Test
    void testFederatedQueryEvent() {
        List<String> queries = new ArrayList<>();

        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onFederatedQuery(String sql, List<String> targetUrls) {
                queries.add(sql + " -> " + targetUrls.size() + " targets");
            }
        });

        PjdbcListeners.fireFederatedQuery("SELECT * FROM users",
            List.of("jdbc:h2:mem:db1", "jdbc:h2:mem:db2", "jdbc:h2:mem:db3"));

        assertEquals(1, queries.size());
        assertEquals("SELECT * FROM users -> 3 targets", queries.get(0));
    }

    @Test
    void testListenerExceptionDoesNotStopOthers() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        // First listener throws exception
        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onRetry(String sql, SQLException cause, int attempt, long delayMs) {
                listener1Count.incrementAndGet();
                throw new RuntimeException("Listener 1 failed");
            }
        });

        // Second listener should still be called
        PjdbcListeners.register(new PjdbcEventListener() {
            @Override
            public void onRetry(String sql, SQLException cause, int attempt, long delayMs) {
                listener2Count.incrementAndGet();
            }
        });

        // Should not throw despite listener exception
        assertDoesNotThrow(() ->
            PjdbcListeners.fireRetry("SELECT 1", new SQLException("Test"), 1, 100));

        assertEquals(1, listener1Count.get());
        assertEquals(1, listener2Count.get());
    }

    @Test
    void testNullListenerIgnored() {
        PjdbcListeners.register(null);
        assertEquals(0, PjdbcListeners.listenerCount());
    }

    @Test
    void testClear() {
        PjdbcListeners.register(new PjdbcEventListener() {});
        PjdbcListeners.register(new PjdbcEventListener() {});
        assertEquals(2, PjdbcListeners.listenerCount());

        PjdbcListeners.clear();
        assertEquals(0, PjdbcListeners.listenerCount());
    }
}
