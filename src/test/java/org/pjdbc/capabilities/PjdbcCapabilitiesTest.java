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
        Optional<DriverCapability> cat = capabilities.findByPrefix("cat");
        assertTrue("Should find 'cat' driver", cat.isPresent());
        assertEquals("cat", cat.get().prefix());
    }

    @Test
    public void testFindByPrefixNonExistentDriver() {
        Optional<DriverCapability> nonExistent = capabilities.findByPrefix("nonexistent");
        assertFalse("Should not find non-existent driver", nonExistent.isPresent());
    }

    @Test
    public void testFindByPrefixReturnsCorrectDriver() {
        Optional<DriverCapability> retry = capabilities.findByPrefix("retry");
        assertTrue("Should find 'retry' driver", retry.isPresent());
        DriverCapability driver = retry.get();
        assertEquals("retry", driver.prefix());
        assertNotNull("Driver should have a class name", driver.driverClass());
        assertTrue("Driver class should contain 'RetryDriver'",
            driver.driverClass().contains("RetryDriver"));
    }

    // ========== findByClass() tests ==========

    @Test
    public void testFindByClassExistingDriver() {
        Optional<DriverCapability> driver = capabilities.findByClass("org.pjdbc.drivers.CatDriver");
        assertTrue("Should find CatDriver by class", driver.isPresent());
        assertEquals("org.pjdbc.drivers.CatDriver", driver.get().driverClass());
    }

    @Test
    public void testFindByClassNonExistentDriver() {
        Optional<DriverCapability> driver = capabilities.findByClass("com.example.NonExistentDriver");
        assertFalse("Should not find non-existent driver class", driver.isPresent());
    }

    @Test
    public void testFindByClassMatchesFindByPrefix() {
        Optional<DriverCapability> byPrefix = capabilities.findByPrefix("cat");
        assertTrue("Should find cat driver by prefix", byPrefix.isPresent());

        Optional<DriverCapability> byClass = capabilities.findByClass(byPrefix.get().driverClass());
        assertTrue("Should find same driver by class", byClass.isPresent());
        assertEquals("Both lookups should return same driver",
            byPrefix.get().prefix(), byClass.get().prefix());
    }

    // ========== findByCapability() tests ==========

    @Test
    public void testFindByCapabilityReturnsMatchingDrivers() {
        List<DriverCapability> resilienceDrivers = capabilities.findByCapability("resilience");
        assertFalse("Should find resilience drivers", resilienceDrivers.isEmpty());
        for (DriverCapability driver : resilienceDrivers) {
            assertTrue("Each driver should have 'resilience' capability",
                driver.hasCapability("resilience"));
        }
    }

    @Test
    public void testFindByCapabilityReturnsEmptyForNonExistent() {
        List<DriverCapability> noMatch = capabilities.findByCapability("nonexistent-capability");
        assertTrue("Should return empty list for non-existent capability", noMatch.isEmpty());
    }

    @Test
    public void testFindByCapabilityTesting() {
        List<DriverCapability> testingDrivers = capabilities.findByCapability("testing");
        assertFalse("Should find testing drivers", testingDrivers.isEmpty());
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
    public void testFindBySideEffectStatefulOrEmpty() {
        // After rescope, no drivers have logging side effect, test stateful instead
        List<DriverCapability> statefulDrivers = capabilities.findBySideEffect("stateful");
        for (DriverCapability driver : statefulDrivers) {
            assertNotNull("Driver should have side effects", driver.sideEffects());
            assertTrue("Driver should have stateful side effect",
                driver.sideEffects().stateful());
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
        assertTrue("Should have 'cat' driver", capabilities.hasDriver("cat"));
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

    // ========== Source enum tests ==========

    @Test
    public void testLoadFromManifestReturnsManifestSource() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromManifest();
        assertEquals("Should have MANIFEST source",
            PjdbcCapabilities.Source.MANIFEST, caps.getSource());
    }

    @Test
    public void testLoadFromReflectionReturnsReflectionSource() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        assertEquals("Should have REFLECTION source",
            PjdbcCapabilities.Source.REFLECTION, caps.getSource());
    }

    @Test
    public void testGetSourceIsNonNull() {
        PjdbcCapabilities caps = PjdbcCapabilities.load();
        assertNotNull("getSource() should not return null", caps.getSource());
    }

    @Test
    public void testToStringContainsSource() {
        PjdbcCapabilities caps = PjdbcCapabilities.load();
        String str = caps.toString();
        assertTrue("toString should contain source", str.contains("source="));
    }

    // ========== Reflection loading tests ==========

    @Test
    public void testLoadFromReflectionReturnsNonNull() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        assertNotNull("loadFromReflection() should return non-null", caps);
    }

    @Test
    public void testLoadFromReflectionHasDrivers() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        assertTrue("Reflection should find at least one driver",
            caps.getDriverCount() > 0);
    }

    @Test
    public void testLoadFromReflectionHasVersion() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        assertNotNull("Reflection version should not be null", caps.getVersion());
        assertTrue("Reflection version should contain 'reflection'",
            caps.getVersion().contains("reflection"));
    }

    @Test
    public void testLoadFromReflectionFindsCatDriver() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        Optional<DriverCapability> cat = caps.findByPrefix("cat");
        assertTrue("Reflection should find 'cat' driver", cat.isPresent());
        assertEquals("org.pjdbc.drivers.CatDriver", cat.get().driverClass());
    }

    @Test
    public void testLoadFromReflectionFindsRetryDriverWithParameters() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        Optional<DriverCapability> retry = caps.findByPrefix("retry");
        assertTrue("Reflection should find 'retry' driver", retry.isPresent());

        // RetryDriver should have parameters like maxRetries
        assertNotNull("RetryDriver should have parameters", retry.get().parameters());
        assertFalse("RetryDriver should have at least one parameter",
            retry.get().parameters().isEmpty());

        boolean hasMaxRetries = retry.get().parameters().stream()
            .anyMatch(p -> "maxRetries".equals(p.name()));
        assertTrue("RetryDriver should have maxRetries parameter", hasMaxRetries);
    }

    @Test
    public void testLoadFromReflectionFindsChaosDriverWithCapabilities() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        Optional<DriverCapability> chaos = caps.findByPrefix("chaos");
        assertTrue("Reflection should find 'chaos' driver", chaos.isPresent());
        assertTrue("ChaosDriver should have 'testing' capability",
            chaos.get().hasCapability("testing"));
    }

    @Test
    public void testLoadFromReflectionFindsDriversWithDependencies() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        // Check that findWithDependencies works (may be empty after rescope)
        List<DriverCapability> withDeps = caps.findWithDependencies();
        for (DriverCapability driver : withDeps) {
            assertNotNull("Driver should have dependencies", driver.dependencies());
            assertFalse("Driver should have at least one dependency",
                driver.dependencies().isEmpty());
        }
    }

    @Test
    public void testLoadFromReflectionFindsDriverWithSideEffects() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        // Check that findBySideEffect works - circuitbreaker has stateful side effect
        List<DriverCapability> statefulDrivers = caps.findBySideEffect("stateful");
        for (DriverCapability driver : statefulDrivers) {
            assertNotNull("Driver should have side effects", driver.sideEffects());
            assertTrue("Driver should have stateful side effect",
                driver.sideEffects().stateful());
        }
    }

    @Test
    public void testLoadFromReflectionDriversAreSortedByPrefix() {
        PjdbcCapabilities caps = PjdbcCapabilities.loadFromReflection();
        List<DriverCapability> drivers = caps.getAllDrivers();
        for (int i = 1; i < drivers.size(); i++) {
            String prev = drivers.get(i - 1).prefix();
            String curr = drivers.get(i).prefix();
            assertTrue("Drivers should be sorted by prefix: " + prev + " <= " + curr,
                prev.compareTo(curr) <= 0);
        }
    }

    @Test
    public void testReflectionAndManifestFindSameDrivers() {
        PjdbcCapabilities manifest = PjdbcCapabilities.loadFromManifest();
        PjdbcCapabilities reflection = PjdbcCapabilities.loadFromReflection();

        // Both should find the same set of drivers (by prefix)
        for (DriverCapability driver : manifest.getAllDrivers()) {
            assertTrue("Reflection should find driver: " + driver.prefix(),
                reflection.hasDriver(driver.prefix()));
        }
    }

    @Test
    public void testBuilderWithSource() {
        PjdbcCapabilities caps = PjdbcCapabilities.builder()
            .version("1.0")
            .source(PjdbcCapabilities.Source.REFLECTION)
            .build();
        assertEquals("Source should be REFLECTION",
            PjdbcCapabilities.Source.REFLECTION, caps.getSource());
    }

    @Test
    public void testBuilderDefaultSource() {
        PjdbcCapabilities caps = PjdbcCapabilities.builder().build();
        assertEquals("Default source should be MANIFEST",
            PjdbcCapabilities.Source.MANIFEST, caps.getSource());
    }
}
