package org.pjdbc.conformance;

import org.pjdbc.capabilities.DriverCapability;
import org.pjdbc.capabilities.PjdbcCapabilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Base class for PJDBC driver conformance tests.
 *
 * <p>Conformance tests verify that drivers behave according to their
 * declared capabilities in the capabilities manifest. These tests ensure:
 * <ul>
 *   <li>URL parsing follows the documented format</li>
 *   <li>Parameters have correct default values</li>
 *   <li>Side effects match declarations</li>
 *   <li>Performance characteristics are within bounds</li>
 * </ul>
 *
 * <p>Subclasses implement specific conformance test categories.
 */
public abstract class DriverConformanceTest {

    protected static final String MOCK_URL = "jdbc:mock:conformance";
    protected static final String H2_URL = "jdbc:h2:mem:conformance;DB_CLOSE_DELAY=-1";

    protected final PjdbcCapabilities capabilities;

    protected DriverConformanceTest() {
        this.capabilities = PjdbcCapabilities.load();
    }

    /**
     * Returns all drivers that should be tested.
     */
    protected List<DriverCapability> getTestableDrivers() {
        return capabilities.getAllDrivers().stream()
            .filter(d -> !d.terminal())  // Skip terminal drivers that need special handling
            .toList();
    }

    /**
     * Returns drivers with the specified capability.
     */
    protected List<DriverCapability> getDriversWithCapability(String capability) {
        return capabilities.findByCapability(capability);
    }

    /**
     * Constructs a PJDBC URL wrapping the given delegate URL.
     */
    protected String buildUrl(DriverCapability driver, String delegateUrl) {
        return "jdbc:" + driver.prefix() + ":" + delegateUrl;
    }

    /**
     * Constructs a PJDBC URL with parameters.
     */
    protected String buildUrl(DriverCapability driver, String params, String delegateUrl) {
        return "jdbc:" + driver.prefix() + "[" + params + "]:" + delegateUrl;
    }

    /**
     * Attempts to get a connection through the driver.
     */
    protected Connection getConnection(String url) throws SQLException {
        return DriverManager.getConnection(url, new Properties());
    }

    /**
     * Attempts to get a connection with properties.
     */
    protected Connection getConnection(String url, Properties props) throws SQLException {
        return DriverManager.getConnection(url, props);
    }

    /**
     * Safely closes a connection, ignoring exceptions.
     */
    protected void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Asserts that a condition is true, throwing AssertionError if not.
     */
    protected void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Asserts that two objects are equal.
     */
    protected void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
    }

    /**
     * Asserts that an exception is thrown.
     */
    protected <T extends Throwable> T assertThrows(Class<T> expectedType, ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected " + expectedType.getName() + " but no exception was thrown");
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return expectedType.cast(t);
            }
            throw new AssertionError("Expected " + expectedType.getName() + " but got " + t.getClass().getName(), t);
        }
    }

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
