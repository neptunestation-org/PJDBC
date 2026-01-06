package org.pjdbc.jmx;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pjdbc.drivers.CircuitBreakerDriver;

/**
 * Tests for CircuitBreaker JMX MBean functionality.
 */
public class CircuitBreakerJmxTest {

    @Before
    public void setUp() {
        // Ensure clean state before each test
        CircuitBreakerRegistry.disableJmx();
        CircuitBreakerRegistry.getInstance().clear();
    }

    @After
    public void tearDown() {
        // Clean up after each test
        CircuitBreakerRegistry.disableJmx();
        CircuitBreakerRegistry.getInstance().clear();
    }

    @Test
    public void registryTracksCircuitBreakers() throws SQLException {
        String url = "jdbc:circuitbreaker[name=test-cb]:jdbc:h2:mem:jmxtest1;DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            assertEquals(1, CircuitBreakerRegistry.getInstance().getCount());
            assertNotNull(CircuitBreakerRegistry.getInstance().get("test-cb"));
        }
    }

    @Test
    public void mbeanNotRegisteredByDefault() throws Exception {
        String url = "jdbc:circuitbreaker[name=test-no-jmx]:jdbc:h2:mem:jmxtest2;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=test-no-jmx");

        try (Connection conn = DriverManager.getConnection(url)) {
            assertFalse(mbs.isRegistered(objectName));
        }
    }

    @Test
    public void mbeanRegisteredWhenJmxEnabled() throws Exception {
        CircuitBreakerRegistry.enableJmx();

        String url = "jdbc:circuitbreaker[name=test-with-jmx]:jdbc:h2:mem:jmxtest3;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=test-with-jmx");

        try (Connection conn = DriverManager.getConnection(url)) {
            assertTrue(mbs.isRegistered(objectName));

            // Verify attributes are accessible
            assertEquals("CLOSED", mbs.getAttribute(objectName, "State"));
            assertEquals("test-with-jmx", mbs.getAttribute(objectName, "Name"));
            assertEquals(5, mbs.getAttribute(objectName, "FailureThreshold"));
            assertEquals(0L, mbs.getAttribute(objectName, "TotalRequests"));
        }
    }

    @Test
    public void mbeanReflectsCircuitBreakerState() throws Exception {
        CircuitBreakerRegistry.enableJmx();

        String url = "jdbc:circuitbreaker[name=state-test,failureThreshold=2]:jdbc:h2:mem:jmxtest4;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=state-test");

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Execute a successful query
            stmt.executeQuery("SELECT 1");
            assertEquals(1L, mbs.getAttribute(objectName, "TotalRequests"));
            assertEquals(0L, mbs.getAttribute(objectName, "TotalFailures"));

            // Force failures to open circuit
            CircuitBreakerDriver.CircuitBreaker cb = CircuitBreakerRegistry.getInstance().get("state-test");
            cb.recordFailure();
            cb.recordFailure();

            assertEquals("OPEN", mbs.getAttribute(objectName, "State"));
            assertEquals(2L, mbs.getAttribute(objectName, "TotalFailures"));
        }
    }

    @Test
    public void mbeanOperationsWork() throws Exception {
        CircuitBreakerRegistry.enableJmx();

        String url = "jdbc:circuitbreaker[name=ops-test]:jdbc:h2:mem:jmxtest5;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=ops-test");

        try (Connection conn = DriverManager.getConnection(url)) {
            // Force open
            mbs.invoke(objectName, "forceOpen", null, null);
            assertEquals("OPEN", mbs.getAttribute(objectName, "State"));

            // Force closed
            mbs.invoke(objectName, "forceClosed", null, null);
            assertEquals("CLOSED", mbs.getAttribute(objectName, "State"));

            // Reset
            CircuitBreakerDriver.CircuitBreaker cb = CircuitBreakerRegistry.getInstance().get("ops-test");
            cb.recordFailure();
            assertTrue((Long) mbs.getAttribute(objectName, "TotalFailures") > 0);

            mbs.invoke(objectName, "reset", null, null);
            assertEquals(0L, mbs.getAttribute(objectName, "TotalFailures"));
            assertEquals("CLOSED", mbs.getAttribute(objectName, "State"));
        }
    }

    @Test
    public void enableJmxRegistersExistingCircuitBreakers() throws Exception {
        String url = "jdbc:circuitbreaker[name=existing-cb]:jdbc:h2:mem:jmxtest6;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=existing-cb");

        // Create connection before enabling JMX
        try (Connection conn = DriverManager.getConnection(url)) {
            assertFalse(mbs.isRegistered(objectName));

            // Enable JMX - should register existing circuit breaker
            CircuitBreakerRegistry.enableJmx();
            assertTrue(mbs.isRegistered(objectName));
        }
    }

    @Test
    public void disableJmxUnregistersAllMBeans() throws Exception {
        CircuitBreakerRegistry.enableJmx();

        String url = "jdbc:circuitbreaker[name=unregister-test]:jdbc:h2:mem:jmxtest7;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=unregister-test");

        try (Connection conn = DriverManager.getConnection(url)) {
            assertTrue(mbs.isRegistered(objectName));

            CircuitBreakerRegistry.disableJmx();
            assertFalse(mbs.isRegistered(objectName));
        }
    }

    @Test
    public void failureRatePercentCalculation() throws Exception {
        CircuitBreakerRegistry.enableJmx();

        String url = "jdbc:circuitbreaker[name=rate-test]:jdbc:h2:mem:jmxtest8;DB_CLOSE_DELAY=-1";
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.pjdbc:type=CircuitBreaker,name=rate-test");

        try (Connection conn = DriverManager.getConnection(url)) {
            CircuitBreakerDriver.CircuitBreaker cb = CircuitBreakerRegistry.getInstance().get("rate-test");

            // No requests yet
            assertEquals(0.0, (Double) mbs.getAttribute(objectName, "FailureRatePercent"), 0.01);

            // 1 failure, 3 successes = 25% failure rate
            cb.recordFailure();
            cb.recordSuccess();
            cb.recordSuccess();
            cb.recordSuccess();

            assertEquals(25.0, (Double) mbs.getAttribute(objectName, "FailureRatePercent"), 0.01);
        }
    }
}
