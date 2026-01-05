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
        // The manifest should have a reasonable number of drivers (at least 15)
        assertTrue("Should have at least 15 drivers", capabilities.getDriverCount() >= 15);
        // And not more than 50 (sanity check)
        assertTrue("Should have fewer than 50 drivers", capabilities.getDriverCount() < 50);
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

        // These are the expected capability tags based on the manifest
        assertTrue("Should have 'caching' tag", tags.contains("caching"));
        assertTrue("Should have 'pooling' tag", tags.contains("pooling"));
        assertTrue("Should have 'logging' tag", tags.contains("logging"));
        assertTrue("Should have 'testing' tag", tags.contains("testing"));
        assertTrue("Should have 'security' tag", tags.contains("security"));
    }

    @Test
    public void testCachingDriversHaveCachingCapability() {
        List<DriverCapability> cachingDrivers = capabilities.findByCapability("caching");
        assertFalse("Should have caching drivers", cachingDrivers.isEmpty());

        for (DriverCapability driver : cachingDrivers) {
            String name = driver.name().toLowerCase();
            assertTrue(
                "Caching driver should have 'caching' in name: " + driver.name(),
                name.contains("caching") || name.contains("cache")
            );
        }
    }

    @Test
    public void testPoolingDriversHavePoolingCapability() {
        List<DriverCapability> poolingDrivers = capabilities.findByCapability("pooling");
        assertFalse("Should have pooling drivers", poolingDrivers.isEmpty());

        for (DriverCapability driver : poolingDrivers) {
            String name = driver.name().toLowerCase();
            assertTrue(
                "Pooling driver should have 'pool' in name: " + driver.name(),
                name.contains("pool")
            );
        }
    }

    // ========== Side Effects Consistency Tests ==========

    @Test
    public void testLoggingDriverHasLoggingSideEffect() {
        DriverCapability logDriver = capabilities.findByPrefix("log").orElse(null);
        assertNotNull("Should have log driver", logDriver);
        assertNotNull("Log driver should have side effects", logDriver.sideEffects());
        assertTrue("Log driver should have logging side effect", logDriver.sideEffects().logging());
    }

    @Test
    public void testMetricsDriverHasMetricsSideEffect() {
        DriverCapability metricsDriver = capabilities.findByPrefix("metrics").orElse(null);
        assertNotNull("Should have metrics driver", metricsDriver);
        assertNotNull("Metrics driver should have side effects", metricsDriver.sideEffects());
        assertTrue("Metrics driver should have metrics side effect", metricsDriver.sideEffects().metrics());
    }

    @Test
    public void testCachingDriversWithNetworkHaveNetworkSideEffect() {
        // Redis, Memcached, Hazelcast should have network side effect
        for (String prefix : List.of("rediscache", "memcache", "hazelcast")) {
            DriverCapability driver = capabilities.findByPrefix(prefix).orElse(null);
            if (driver != null && driver.sideEffects() != null) {
                assertTrue(
                    prefix + " driver should have network side effect",
                    driver.sideEffects().network()
                );
            }
        }
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
    public void testHikariDriverHasHikariDependency() {
        DriverCapability hikari = capabilities.findByPrefix("hikaricp").orElse(null);
        assertNotNull("Should have HikariCP driver", hikari);
        assertNotNull("HikariCP driver should have dependencies", hikari.dependencies());
        assertFalse("HikariCP driver should have at least one dependency", hikari.dependencies().isEmpty());

        boolean hasHikariDep = hikari.dependencies().stream()
            .anyMatch(d -> d.artifactId().equals("HikariCP"));
        assertTrue("HikariCP driver should depend on HikariCP", hasHikariDep);
    }

    @Test
    public void testRedisDriverHasJedisDependency() {
        DriverCapability redis = capabilities.findByPrefix("rediscache").orElse(null);
        assertNotNull("Should have Redis caching driver", redis);

        if (redis.dependencies() != null && !redis.dependencies().isEmpty()) {
            boolean hasJedisDep = redis.dependencies().stream()
                .anyMatch(d -> d.artifactId().equals("jedis"));
            assertTrue("Redis driver should depend on jedis", hasJedisDep);
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
        // These prefixes should all be present based on the driver files in the codebase
        String[] expectedPrefixes = {
            "cat", "log", "filter", "pool", "hikaricp", "tee", "mapuser",
            "sink", "mock", "readonly", "retry", "chaos", "cache",
            "rediscache", "memcache", "hazelcast", "trace", "metrics", "mask"
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
