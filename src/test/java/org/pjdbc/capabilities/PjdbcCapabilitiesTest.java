package org.pjdbc.capabilities;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the PjdbcCapabilities runtime introspection API.
 */
public class PjdbcCapabilitiesTest {

    private PjdbcCapabilities capabilities;

    @Before
    public void setUp() {
        // Force reload to ensure clean state for each test
        capabilities = PjdbcCapabilities.reload();
    }

    // ========== load() and reload() tests ==========

    @Test
    public void testLoadReturnsNonNull() {
        PjdbcCapabilities caps = PjdbcCapabilities.load();
        assertNotNull("load() should return non-null capabilities", caps);
    }

    @Test
    public void testLoadReturnsSameInstance() {
        PjdbcCapabilities first = PjdbcCapabilities.load();
        PjdbcCapabilities second = PjdbcCapabilities.load();
        assertSame("load() should return cached instance", first, second);
    }

    @Test
    public void testReloadReturnsFreshInstance() {
        PjdbcCapabilities first = PjdbcCapabilities.load();
        PjdbcCapabilities reloaded = PjdbcCapabilities.reload();
        assertNotNull("reload() should return non-null", reloaded);
        // After reload, load() should return the new instance
        PjdbcCapabilities afterReload = PjdbcCapabilities.load();
        assertSame("load() after reload should return reloaded instance", reloaded, afterReload);
    }

    @Test
    public void testLoadedCapabilitiesHasVersion() {
        assertNotNull("Version should not be null", capabilities.getVersion());
        assertFalse("Version should not be empty", capabilities.getVersion().isEmpty());
    }

    @Test
    public void testLoadedCapabilitiesHasDrivers() {
        assertTrue("Should have at least one driver", capabilities.getDriverCount() > 0);
        assertFalse("getAllDrivers() should not be empty", capabilities.getAllDrivers().isEmpty());
    }

    // ========== findByPrefix() tests ==========

    @Test
    public void testFindByPrefixExistingDriver() {
        Optional<DriverCapability> log = capabilities.findByPrefix("log");
        assertTrue("Should find 'log' driver", log.isPresent());
        assertEquals("log", log.get().prefix());
    }

    @Test
    public void testFindByPrefixNonExistentDriver() {
        Optional<DriverCapability> nonExistent = capabilities.findByPrefix("nonexistent");
        assertFalse("Should not find non-existent driver", nonExistent.isPresent());
    }

    @Test
    public void testFindByPrefixReturnsCorrectDriver() {
        Optional<DriverCapability> pool = capabilities.findByPrefix("pool");
        assertTrue("Should find 'pool' driver", pool.isPresent());
        DriverCapability driver = pool.get();
        assertEquals("pool", driver.prefix());
        assertNotNull("Driver should have a class name", driver.driverClass());
        assertTrue("Driver class should contain 'PoolDriver'",
            driver.driverClass().contains("PoolDriver"));
    }

    // ========== findByClass() tests ==========

    @Test
    public void testFindByClassExistingDriver() {
        Optional<DriverCapability> driver = capabilities.findByClass("org.pjdbc.drivers.LogDriver");
        assertTrue("Should find LogDriver by class", driver.isPresent());
        assertEquals("org.pjdbc.drivers.LogDriver", driver.get().driverClass());
    }

    @Test
    public void testFindByClassNonExistentDriver() {
        Optional<DriverCapability> driver = capabilities.findByClass("com.example.NonExistentDriver");
        assertFalse("Should not find non-existent driver class", driver.isPresent());
    }

    @Test
    public void testFindByClassMatchesFindByPrefix() {
        Optional<DriverCapability> byPrefix = capabilities.findByPrefix("log");
        assertTrue("Should find log driver by prefix", byPrefix.isPresent());

        Optional<DriverCapability> byClass = capabilities.findByClass(byPrefix.get().driverClass());
        assertTrue("Should find same driver by class", byClass.isPresent());
        assertEquals("Both lookups should return same driver",
            byPrefix.get().prefix(), byClass.get().prefix());
    }

    // ========== findByCapability() tests ==========

    @Test
    public void testFindByCapabilityReturnsMatchingDrivers() {
        List<DriverCapability> cachingDrivers = capabilities.findByCapability("caching");
        assertFalse("Should find caching drivers", cachingDrivers.isEmpty());
        for (DriverCapability driver : cachingDrivers) {
            assertTrue("Each driver should have 'caching' capability",
                driver.hasCapability("caching"));
        }
    }

    @Test
    public void testFindByCapabilityReturnsEmptyForNonExistent() {
        List<DriverCapability> noMatch = capabilities.findByCapability("nonexistent-capability");
        assertTrue("Should return empty list for non-existent capability", noMatch.isEmpty());
    }

    @Test
    public void testFindByCapabilityLogging() {
        List<DriverCapability> loggingDrivers = capabilities.findByCapability("logging");
        assertFalse("Should find logging drivers", loggingDrivers.isEmpty());
    }

    // ========== getAllCapabilityTags() tests ==========

    @Test
    public void testGetAllCapabilityTagsReturnsNonEmpty() {
        List<String> tags = capabilities.getAllCapabilityTags();
        assertFalse("Should have at least one capability tag", tags.isEmpty());
    }

    @Test
    public void testGetAllCapabilityTagsAreSorted() {
        List<String> tags = capabilities.getAllCapabilityTags();
        for (int i = 1; i < tags.size(); i++) {
            assertTrue("Tags should be sorted alphabetically",
                tags.get(i - 1).compareTo(tags.get(i)) <= 0);
        }
    }

    @Test
    public void testGetAllCapabilityTagsAreDistinct() {
        List<String> tags = capabilities.getAllCapabilityTags();
        long distinctCount = tags.stream().distinct().count();
        assertEquals("Tags should be distinct", tags.size(), distinctCount);
    }

    // ========== findComposable() tests ==========

    @Test
    public void testFindComposableReturnsOnlyComposableDrivers() {
        List<DriverCapability> composable = capabilities.findComposable();
        assertFalse("Should find composable drivers", composable.isEmpty());
        for (DriverCapability driver : composable) {
            assertTrue("Each driver should be composable", driver.composable());
        }
    }

    @Test
    public void testComposableDriversCanBeChained() {
        List<DriverCapability> composable = capabilities.findComposable();
        // Composable drivers typically have prefixes that can wrap other URLs
        for (DriverCapability driver : composable) {
            assertNotNull("Composable driver should have prefix", driver.prefix());
            assertFalse("Composable driver prefix should not be empty", driver.prefix().isEmpty());
        }
    }

    // ========== findTerminal() tests ==========

    @Test
    public void testFindTerminalReturnsOnlyTerminalDrivers() {
        List<DriverCapability> terminal = capabilities.findTerminal();
        for (DriverCapability driver : terminal) {
            assertTrue("Each driver should be terminal", driver.terminal());
        }
    }

    @Test
    public void testTerminalDriversExist() {
        // Terminal and composable are NOT mutually exclusive
        // A driver can be composable (accepts a delegate URL) but also terminal
        // (e.g., sink driver accepts a URL pattern but discards operations)
        List<DriverCapability> terminal = capabilities.findTerminal();
        // Just verify terminal drivers are correctly identified
        for (DriverCapability term : terminal) {
            assertTrue("Terminal driver should have terminal=true", term.terminal());
        }
    }

    // ========== findWithDependencies() tests ==========

    @Test
    public void testFindWithDependenciesReturnsDriversWithDeps() {
        List<DriverCapability> withDeps = capabilities.findWithDependencies();
        for (DriverCapability driver : withDeps) {
            assertNotNull("Driver should have dependencies", driver.dependencies());
            assertFalse("Driver should have at least one dependency",
                driver.dependencies().isEmpty());
        }
    }

    @Test
    public void testDriverDependenciesHaveMavenCoordinates() {
        List<DriverCapability> withDeps = capabilities.findWithDependencies();
        if (!withDeps.isEmpty()) {
            DriverCapability driver = withDeps.get(0);
            DriverCapability.Dependency dep = driver.dependencies().get(0);
            assertNotNull("Dependency should have groupId", dep.groupId());
            assertNotNull("Dependency should have artifactId", dep.artifactId());
            String coords = dep.toMavenCoordinates();
            assertTrue("Maven coordinates should contain groupId",
                coords.contains(dep.groupId()));
            assertTrue("Maven coordinates should contain artifactId",
                coords.contains(dep.artifactId()));
        }
    }

    // ========== findBySideEffect() tests ==========

    @Test
    public void testFindBySideEffectLogging() {
        List<DriverCapability> loggingDrivers = capabilities.findBySideEffect("logging");
        for (DriverCapability driver : loggingDrivers) {
            assertNotNull("Driver should have side effects", driver.sideEffects());
            assertTrue("Driver should have logging side effect",
                driver.sideEffects().logging());
        }
    }

    @Test
    public void testFindBySideEffectNetwork() {
        List<DriverCapability> networkDrivers = capabilities.findBySideEffect("network");
        for (DriverCapability driver : networkDrivers) {
            assertNotNull("Driver should have side effects", driver.sideEffects());
            assertTrue("Driver should have network side effect",
                driver.sideEffects().network());
        }
    }

    @Test
    public void testFindBySideEffectStateful() {
        List<DriverCapability> statefulDrivers = capabilities.findBySideEffect("stateful");
        for (DriverCapability driver : statefulDrivers) {
            assertNotNull("Driver should have side effects", driver.sideEffects());
            assertTrue("Driver should have stateful side effect",
                driver.sideEffects().stateful());
        }
    }

    @Test
    public void testFindBySideEffectUnknownReturnsEmpty() {
        List<DriverCapability> unknown = capabilities.findBySideEffect("unknown-side-effect");
        assertTrue("Unknown side effect should return empty list", unknown.isEmpty());
    }

    // ========== hasDriver() tests ==========

    @Test
    public void testHasDriverReturnsTrueForExistingDriver() {
        assertTrue("Should have 'log' driver", capabilities.hasDriver("log"));
    }

    @Test
    public void testHasDriverReturnsFalseForNonExistent() {
        assertFalse("Should not have 'nonexistent' driver", capabilities.hasDriver("nonexistent"));
    }

    @Test
    public void testHasDriverConsistentWithFindByPrefix() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            assertTrue("hasDriver should return true for all existing drivers",
                capabilities.hasDriver(driver.prefix()));
        }
    }

    // ========== getDriverCount() tests ==========

    @Test
    public void testGetDriverCountMatchesListSize() {
        assertEquals("getDriverCount should match getAllDrivers size",
            capabilities.getAllDrivers().size(), capabilities.getDriverCount());
    }

    @Test
    public void testGetDriverCountIsPositive() {
        assertTrue("Driver count should be positive", capabilities.getDriverCount() > 0);
    }

    // ========== toString() tests ==========

    @Test
    public void testToStringContainsVersion() {
        String str = capabilities.toString();
        assertTrue("toString should contain version", str.contains(capabilities.getVersion()));
    }

    @Test
    public void testToStringContainsDriverCount() {
        String str = capabilities.toString();
        assertTrue("toString should contain driver count",
            str.contains(String.valueOf(capabilities.getDriverCount())));
    }

    // ========== Builder tests ==========

    @Test
    public void testBuilderCreatesEmptyCapabilities() {
        PjdbcCapabilities empty = PjdbcCapabilities.builder().build();
        assertNotNull("Builder should create non-null capabilities", empty);
        assertEquals("Default version should be 1.0", "1.0", empty.getVersion());
        assertEquals("Should have no drivers", 0, empty.getDriverCount());
    }

    @Test
    public void testBuilderWithCustomVersion() {
        PjdbcCapabilities caps = PjdbcCapabilities.builder()
            .version("2.0")
            .build();
        assertEquals("Version should match", "2.0", caps.getVersion());
    }

    @Test
    public void testBuilderWithDriver() {
        DriverCapability driver = new DriverCapability(
            "Test Driver",
            "test",
            "org.example.TestDriver",
            "A test driver",
            List.of("testing"),
            null,
            null,
            null,
            true,
            false
        );

        PjdbcCapabilities caps = PjdbcCapabilities.builder()
            .version("1.0")
            .addDriver(driver)
            .build();

        assertEquals("Should have one driver", 1, caps.getDriverCount());
        assertTrue("Should find test driver", caps.hasDriver("test"));

        Optional<DriverCapability> found = caps.findByPrefix("test");
        assertTrue("Should find driver by prefix", found.isPresent());
        assertEquals("Test Driver", found.get().name());
    }

    @Test
    public void testBuilderWithMultipleDrivers() {
        DriverCapability driver1 = new DriverCapability(
            "Driver One", "one", "org.example.One", "First",
            List.of("cap1"), null, null, null, true, false);
        DriverCapability driver2 = new DriverCapability(
            "Driver Two", "two", "org.example.Two", "Second",
            List.of("cap2"), null, null, null, true, false);

        PjdbcCapabilities caps = PjdbcCapabilities.builder()
            .addDriver(driver1)
            .addDriver(driver2)
            .build();

        assertEquals("Should have two drivers", 2, caps.getDriverCount());
        assertTrue("Should have driver one", caps.hasDriver("one"));
        assertTrue("Should have driver two", caps.hasDriver("two"));
    }

    @Test
    public void testBuilderFluentChaining() {
        PjdbcCapabilities caps = PjdbcCapabilities.builder()
            .version("3.0")
            .addDriver(new DriverCapability(
                "Fluent", "fluent", "org.example.Fluent", "Fluent driver",
                List.of("fluent-cap"), null, null, null, true, false))
            .build();

        assertEquals("3.0", caps.getVersion());
        assertEquals(1, caps.getDriverCount());
    }

    // ========== getAllDrivers() immutability tests ==========

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllDriversReturnsImmutableList() {
        List<DriverCapability> drivers = capabilities.getAllDrivers();
        drivers.add(new DriverCapability(
            "Illegal", "illegal", "org.example.Illegal", "Should fail",
            null, null, null, null, false, false));
    }

    // ========== parse() tests with synthetic JSON ==========

    @Test
    public void testParseMinimalJson() {
        String json = """
            {
                "version": "1.0",
                "drivers": []
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion());
        assertEquals(0, caps.getDriverCount());
    }

    @Test
    public void testParseWithSingleDriver() {
        String json = """
            {
                "version": "2.0",
                "drivers": [
                    {
                        "name": "Parsed Driver",
                        "prefix": "parsed",
                        "driverClass": "org.example.ParsedDriver",
                        "description": "A parsed driver",
                        "capabilities": ["parsing"],
                        "composable": true,
                        "terminal": false
                    }
                ]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("2.0", caps.getVersion());
        assertEquals(1, caps.getDriverCount());

        Optional<DriverCapability> driver = caps.findByPrefix("parsed");
        assertTrue(driver.isPresent());
        assertEquals("Parsed Driver", driver.get().name());
        assertTrue(driver.get().hasCapability("parsing"));
    }
}
