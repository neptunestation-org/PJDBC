package org.pjdbc.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.pjdbc.capabilities.DriverCapability;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for URL parsing across all PJDBC drivers.
 *
 * <p>Verifies that all drivers:
 * <ul>
 *   <li>Accept URLs with their declared prefix</li>
 *   <li>Reject URLs with other prefixes</li>
 *   <li>Handle parameter syntax correctly</li>
 *   <li>Support nested/chained URLs</li>
 * </ul>
 */
@DisplayName("URL Parsing Conformance")
public class UrlParsingConformanceTest extends DriverConformanceTest {

    @BeforeAll
    static void loadDrivers() throws Exception {
        // Force load all PJDBC drivers
        Class.forName("org.pjdbc.drivers.CatDriver");
        Class.forName("org.pjdbc.drivers.LogDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.PoolDriver");
        Class.forName("org.pjdbc.drivers.HikariPoolDriver");
        Class.forName("org.pjdbc.drivers.TeeDriver");
        Class.forName("org.pjdbc.drivers.UserMapDriver");
        Class.forName("org.pjdbc.drivers.SinkDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.ChaosDriver");
        Class.forName("org.pjdbc.drivers.CachingDriver");
        Class.forName("org.pjdbc.drivers.RedisCachingDriver");
        Class.forName("org.pjdbc.drivers.MemcachedCachingDriver");
        Class.forName("org.pjdbc.drivers.HazelcastCachingDriver");
        Class.forName("org.pjdbc.drivers.TracingDriver");
        Class.forName("org.pjdbc.drivers.MetricsDriver");
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    @DisplayName("All drivers accept their declared URL prefix")
    void allDriversAcceptDeclaredPrefix() throws SQLException {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            // Skip drivers with special URL formats (multi-URL with semicolons)
            if (SPECIAL_FORMAT_DRIVERS.contains(driver.prefix())) continue;

            String url = buildUrl(driver, MOCK_URL);

            // Find the driver by iterating registered drivers
            boolean found = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver d = drivers.nextElement();
                if (d.getClass().getName().equals(driver.driverClass())) {
                    found = true;
                    assertTrue(d.acceptsURL(url),
                        "Driver " + driver.name() + " should accept URL: " + url);
                    break;
                }
            }
            assertTrue(found,
                "Driver for prefix '" + driver.prefix() + "' should be registered");
        }
    }

    @Test
    @DisplayName("Drivers reject URLs with wrong prefix")
    void driversRejectWrongPrefix() throws SQLException {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            String wrongUrl = "jdbc:nonexistent_prefix_xyz:" + MOCK_URL;

            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver d = drivers.nextElement();
                if (d.getClass().getName().equals(driver.driverClass())) {
                    assertFalse(d.acceptsURL(wrongUrl),
                        "Driver " + driver.name() + " should reject wrong prefix URL");
                }
            }
        }
    }

    @Test
    @DisplayName("Drivers accept URLs with bracket parameter syntax")
    void driversAcceptBracketParameterSyntax() throws SQLException {
        for (DriverCapability driver : getTestableDrivers()) {
            if (driver.parameters() == null || driver.parameters().isEmpty()) {
                continue;
            }

            // Build a URL with a parameter
            DriverCapability.Parameter param = driver.parameters().get(0);
            String paramValue = getTestValueForType(param.type());
            String url = buildUrl(driver, param.name() + "=" + paramValue, MOCK_URL);

            Driver jdbcDriver = DriverManager.getDriver(url);
            assertTrue(jdbcDriver.acceptsURL(url),
                "Driver " + driver.name() + " should accept bracket params: " + url);
        }
    }

    @Test
    @DisplayName("Drivers accept URLs with multiple parameters")
    void driversAcceptMultipleParameters() throws SQLException {
        for (DriverCapability driver : getTestableDrivers()) {
            if (driver.parameters() == null || driver.parameters().size() < 2) {
                continue;
            }

            // Build URL with multiple parameters
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < Math.min(3, driver.parameters().size()); i++) {
                if (i > 0) params.append(",");
                DriverCapability.Parameter p = driver.parameters().get(i);
                params.append(p.name()).append("=").append(getTestValueForType(p.type()));
            }

            String url = buildUrl(driver, params.toString(), MOCK_URL);
            Driver jdbcDriver = DriverManager.getDriver(url);
            assertTrue(jdbcDriver.acceptsURL(url),
                "Driver " + driver.name() + " should accept multiple params: " + url);
        }
    }

    @Test
    @DisplayName("Composable drivers accept chained URLs")
    void composableDriversAcceptChainedUrls() throws SQLException {
        // Test a few known-good chaining combinations
        String[] chainedUrls = {
            "jdbc:cat:jdbc:log:" + MOCK_URL,
            "jdbc:log:jdbc:cat:" + MOCK_URL,
            "jdbc:cat:jdbc:cat:" + MOCK_URL
        };

        for (String chainedUrl : chainedUrls) {
            // Find driver that accepts this URL
            boolean accepted = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver d = drivers.nextElement();
                try {
                    if (d.acceptsURL(chainedUrl)) {
                        accepted = true;
                        break;
                    }
                } catch (SQLException ignored) {
                }
            }
            assertTrue(accepted, "Chained URL should be accepted: " + chainedUrl);
        }
    }

    @Test
    @DisplayName("URL prefix matches manifest declaration")
    void urlPrefixMatchesManifest() throws SQLException {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            String expectedPrefix = "jdbc:" + driver.prefix() + ":";
            assertEquals(expectedPrefix, driver.getUrlPrefix(),
                "getUrlPrefix() should return correct prefix for " + driver.name());
        }
    }

    @Test
    @DisplayName("Terminal drivers are registered")
    void terminalDriversAreRegistered() {
        for (DriverCapability driver : capabilities.findTerminal()) {
            // Terminal drivers should be registered and findable
            boolean found = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver d = drivers.nextElement();
                if (d.getClass().getName().equals(driver.driverClass())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Terminal driver " + driver.name() + " should be registered");
        }
    }

    /**
     * Returns a test value appropriate for the parameter type.
     */
    private String getTestValueForType(String type) {
        return switch (type) {
            case "integer" -> "10";
            case "boolean" -> "true";
            case "float" -> "1.5";
            case "duration" -> "1000";
            case "string" -> "test";
            default -> "value";
        };
    }
}
