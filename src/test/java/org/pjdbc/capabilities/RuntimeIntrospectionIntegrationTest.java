package org.pjdbc.capabilities;

import static org.junit.Assert.*;

import java.io.File;
import java.sql.Driver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests for the Runtime Introspection API.
 *
 * These tests verify that the capabilities manifest is valid, consistent,
 * and in sync with the actual driver implementations in the codebase.
 */
public class RuntimeIntrospectionIntegrationTest {

    private static PjdbcCapabilities capabilities;

    @BeforeClass
    public static void loadCapabilities() {
        capabilities = PjdbcCapabilities.reload();
    }

    // ========== Manifest Loading Tests ==========

    @Test
    public void testManifestLoadsSuccessfully() {
        assertNotNull("Capabilities should load", capabilities);
        assertNotNull("Version should be present", capabilities.getVersion());
    }

    @Test
    public void testManifestHasValidVersion() {
        String version = capabilities.getVersion();
        assertFalse("Version should not be empty", version.isEmpty());
        // Version should be a valid semver-like string
        assertTrue("Version should match pattern", version.matches("\\d+\\.\\d+.*"));
    }

    @Test
    public void testManifestHasDrivers() {
        assertTrue("Should have at least one driver", capabilities.getDriverCount() > 0);
    }

    @Test
    public void testExpectedDriverCount() {
        // The manifest should have a reasonable number of drivers (at least 10 after rescope)
        assertTrue("Should have at least 10 drivers", capabilities.getDriverCount() >= 10);
        // And not more than 30 (sanity check after rescope)
        assertTrue("Should have fewer than 30 drivers", capabilities.getDriverCount() < 30);
    }

    // ========== Driver Class Verification Tests ==========

    @Test
    public void testAllDeclaredDriverClassesExist() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();

        for (DriverCapability driver : drivers) {
            String className = driver.driverClass();
            assertNotNull("Driver class should not be null for " + driver.prefix(), className);

            try {
                Class<?> clazz = Class.forName(className);
                assertNotNull("Should load class " + className, clazz);
            } catch (ClassNotFoundException e) {
                fail("Driver class not found: " + className + " for driver " + driver.prefix());
            }
        }
    }

    @Test
    public void testAllDeclaredDriversImplementJdbcDriver() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();

        for (DriverCapability driver : drivers) {
            String className = driver.driverClass();

            try {
                Class<?> clazz = Class.forName(className);
                assertTrue(
                    "Class " + className + " should implement java.sql.Driver",
                    Driver.class.isAssignableFrom(clazz)
                );
            } catch (ClassNotFoundException e) {
                fail("Driver class not found: " + className);
            }
        }
    }

    @Test
    public void testAllDeclaredDriversAreInstantiable() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();

        for (DriverCapability driver : drivers) {
            String className = driver.driverClass();

            try {
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                assertNotNull("Should instantiate " + className, instance);
                assertTrue("Instance should be a Driver", instance instanceof Driver);
            } catch (Exception e) {
                fail("Failed to instantiate driver " + className + ": " + e.getMessage());
            }
        }
    }

    // ========== Manifest Consistency Tests ==========

    @Test
    public void testAllDriversHaveRequiredFields() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertNotNull("Name should not be null", driver.name());
            assertFalse("Name should not be empty", driver.name().isEmpty());

            assertNotNull("Prefix should not be null", driver.prefix());
            assertFalse("Prefix should not be empty", driver.prefix().isEmpty());

            assertNotNull("Driver class should not be null", driver.driverClass());
            assertFalse("Driver class should not be empty", driver.driverClass().isEmpty());
        }
    }

    @Test
    public void testNoDuplicatePrefixes() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();
        Set<String> prefixes = new HashSet<>();

        for (DriverCapability driver : drivers) {
            String prefix = driver.prefix();
            assertFalse(
                "Duplicate prefix found: " + prefix,
                prefixes.contains(prefix)
            );
            prefixes.add(prefix);
        }
    }

    @Test
    public void testNoDuplicateClassNames() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();
        Set<String> classNames = new HashSet<>();

        for (DriverCapability driver : drivers) {
            String className = driver.driverClass();
            assertFalse(
                "Duplicate class name found: " + className,
                classNames.contains(className)
            );
            classNames.add(className);
        }
    }

    @Test
    public void testDriverClassNamesFollowConvention() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            String className = driver.driverClass();
            assertTrue(
                "Driver class should be in org.pjdbc.drivers package: " + className,
                className.startsWith("org.pjdbc.drivers.")
            );
            assertTrue(
                "Driver class should end with 'Driver': " + className,
                className.endsWith("Driver")
            );
        }
    }

    @Test
    public void testUrlPrefixesAreValid() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            String urlPrefix = driver.getUrlPrefix();
            assertTrue(
                "URL prefix should start with 'jdbc:': " + urlPrefix,
                urlPrefix.startsWith("jdbc:")
            );
            assertTrue(
                "URL prefix should end with ':': " + urlPrefix,
                urlPrefix.endsWith(":")
            );
        }
    }

    // ========== Capability Tag Consistency Tests ==========

    @Test
    public void testExpectedCapabilityTagsExist() {
        List<String> tags = capabilities.getAllCapabilityTags();

        // These are the expected capability tags after rescope
        assertTrue("Should have 'testing' tag", tags.contains("testing"));
        assertTrue("Should have 'security' tag", tags.contains("security"));
        assertTrue("Should have 'resilience' tag", tags.contains("resilience"));
    }

    @Test
    public void testResilienceDriversHaveResilienceCapability() {
        List<DriverCapability> resilienceDrivers = capabilities.findByCapability("resilience");
        assertFalse("Should have resilience drivers", resilienceDrivers.isEmpty());

        for (DriverCapability driver : resilienceDrivers) {
            String name = driver.name().toLowerCase();
            assertTrue(
                "Resilience driver should have resilience-related name: " + driver.name(),
                name.contains("retry") || name.contains("timeout") || name.contains("circuit") || name.contains("chaos")
            );
        }
    }

    @Test
    public void testTestingDriversHaveTestingCapability() {
        List<DriverCapability> testingDrivers = capabilities.findByCapability("testing");
        assertFalse("Should have testing drivers", testingDrivers.isEmpty());

        for (DriverCapability driver : testingDrivers) {
            String name = driver.name().toLowerCase();
            assertTrue(
                "Testing driver should have test-related name: " + driver.name(),
                name.contains("mock") || name.contains("chaos") || name.contains("sink")
            );
        }
    }

    // ========== Side Effects Consistency Tests ==========

    @Test
    public void testCircuitBreakerDriverHasStatefulSideEffect() {
        DriverCapability cbDriver = capabilities.findByPrefix("circuitbreaker").orElse(null);
        assertNotNull("Should have circuitbreaker driver", cbDriver);
        if (cbDriver.sideEffects() != null) {
            assertTrue("CircuitBreaker driver should have stateful side effect",
                cbDriver.sideEffects().stateful());
        }
    }

    @Test
    public void testFederatingDriverExists() {
        DriverCapability fedDriver = capabilities.findByPrefix("federate").orElse(null);
        assertNotNull("Should have federate driver", fedDriver);
        // Federate driver routes queries across multiple databases
        assertTrue("Federate driver should be composable", fedDriver.composable());
    }

    // ========== Dependency Consistency Tests ==========

    @Test
    public void testDriversWithDependenciesHaveValidDependencies() {
        List<DriverCapability> driversWithDeps = capabilities.findWithDependencies();

        for (DriverCapability driver : driversWithDeps) {
            assertNotNull("Dependencies should not be null", driver.dependencies());
            assertFalse("Dependencies should not be empty", driver.dependencies().isEmpty());

            for (DriverCapability.Dependency dep : driver.dependencies()) {
                assertNotNull("Dependency groupId should not be null", dep.groupId());
                assertFalse("Dependency groupId should not be empty", dep.groupId().isEmpty());
                assertNotNull("Dependency artifactId should not be null", dep.artifactId());
                assertFalse("Dependency artifactId should not be empty", dep.artifactId().isEmpty());
            }
        }
    }

    @Test
    public void testDriversWithDependenciesAreValid() {
        // After rescope, verify any drivers with dependencies have valid dependency info
        List<DriverCapability> driversWithDeps = capabilities.findWithDependencies();
        for (DriverCapability driver : driversWithDeps) {
            assertNotNull("Driver dependencies should not be null", driver.dependencies());
            assertFalse("Driver should have at least one dependency", driver.dependencies().isEmpty());

            for (DriverCapability.Dependency dep : driver.dependencies()) {
                assertNotNull("Dependency should have groupId", dep.groupId());
                assertNotNull("Dependency should have artifactId", dep.artifactId());
            }
        }
    }

    // ========== Terminal/Composable Consistency Tests ==========

    @Test
    public void testTerminalDriversExist() {
        List<DriverCapability> terminal = capabilities.findTerminal();
        assertFalse("Should have at least one terminal driver", terminal.isEmpty());
    }

    @Test
    public void testComposableDriversExist() {
        List<DriverCapability> composable = capabilities.findComposable();
        assertFalse("Should have at least one composable driver", composable.isEmpty());
        assertTrue(
            "Most drivers should be composable",
            composable.size() > capabilities.getDriverCount() / 2
        );
    }

    @Test
    public void testSinkDriverIsNotTerminal() {
        // Sink is a proxy driver that delegates to an underlying driver
        // (it just discards operations instead of forwarding them)
        DriverCapability sink = capabilities.findByPrefix("sink").orElse(null);
        assertNotNull("Should have sink driver", sink);
        assertFalse("Sink driver should NOT be terminal (it's a proxy)", sink.terminal());
    }

    @Test
    public void testMockDriverIsTerminal() {
        DriverCapability mock = capabilities.findByPrefix("mock").orElse(null);
        assertNotNull("Should have mock driver", mock);
        assertTrue("Mock driver should be terminal", mock.terminal());
    }

    // ========== Parameter Consistency Tests ==========

    @Test
    public void testParametersHaveRequiredFields() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() != null) {
                for (DriverCapability.Parameter param : driver.parameters()) {
                    assertNotNull(
                        "Parameter name should not be null for " + driver.prefix(),
                        param.name()
                    );
                    assertFalse(
                        "Parameter name should not be empty for " + driver.prefix(),
                        param.name().isEmpty()
                    );
                    assertNotNull(
                        "Parameter type should not be null for " + driver.prefix() + "." + param.name(),
                        param.type()
                    );
                }
            }
        }
    }

    @Test
    public void testNumericParametersHaveValidRanges() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() != null) {
                for (DriverCapability.Parameter param : driver.parameters()) {
                    if (param.min() != null && param.max() != null) {
                        assertTrue(
                            "Min should be <= max for " + driver.prefix() + "." + param.name(),
                            param.min().doubleValue() <= param.max().doubleValue()
                        );
                    }
                }
            }
        }
    }

    // ========== Cross-Reference with Codebase Tests ==========

    @Test
    public void testAllExpectedDriverPrefixesArePresent() {
        // These prefixes should all be present after rescope
        String[] expectedPrefixes = {
            "cat", "filter", "tee", "mapuser", "sink", "mock", "readonly",
            "retry", "chaos", "mask", "timeout", "circuitbreaker", "schema", "federate"
        };

        for (String prefix : expectedPrefixes) {
            assertTrue(
                "Expected driver prefix '" + prefix + "' should be present",
                capabilities.hasDriver(prefix)
            );
        }
    }

    @Test
    public void testFindByPrefixWorksForAllDrivers() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertTrue(
                "findByPrefix should find " + driver.prefix(),
                capabilities.findByPrefix(driver.prefix()).isPresent()
            );
        }
    }

    @Test
    public void testFindByClassWorksForAllDrivers() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertTrue(
                "findByClass should find " + driver.driverClass(),
                capabilities.findByClass(driver.driverClass()).isPresent()
            );
        }
    }

    // ========== API Consistency Tests ==========

    @Test
    public void testGetDriverCountMatchesAllDrivers() {
        assertEquals(
            "getDriverCount should match getAllDrivers size",
            capabilities.getDriverCount(),
            capabilities.getAllDrivers().size()
        );
    }

    @Test
    public void testHasDriverConsistentWithFindByPrefix() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertEquals(
                "hasDriver should be consistent with findByPrefix for " + driver.prefix(),
                capabilities.hasDriver(driver.prefix()),
                capabilities.findByPrefix(driver.prefix()).isPresent()
            );
        }
    }

    @Test
    public void testReloadProducesSameResults() {
        PjdbcCapabilities reloaded = PjdbcCapabilities.reload();

        assertEquals("Reloaded version should match", capabilities.getVersion(), reloaded.getVersion());
        assertEquals("Reloaded driver count should match", capabilities.getDriverCount(), reloaded.getDriverCount());

        // Verify all drivers are present
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertTrue(
                "Reloaded capabilities should have driver " + driver.prefix(),
                reloaded.hasDriver(driver.prefix())
            );
        }
    }
}
