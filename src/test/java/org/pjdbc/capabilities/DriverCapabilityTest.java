package org.pjdbc.capabilities;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

/**
 * Unit tests for the DriverCapability record and its nested records.
 */
public class DriverCapabilityTest {

    // ========== DriverCapability field access tests ==========

    @Test
    public void testRecordFieldAccess() {
        DriverCapability driver = new DriverCapability(
            "Test Driver",
            "test",
            "org.example.TestDriver",
            "A test driver for unit testing",
            List.of("testing", "mock"),
            List.of(),
            List.of(),
            DriverCapability.SideEffects.NONE,
            true,
            false
        );

        assertEquals("Test Driver", driver.name());
        assertEquals("test", driver.prefix());
        assertEquals("org.example.TestDriver", driver.driverClass());
        assertEquals("A test driver for unit testing", driver.description());
        assertEquals(2, driver.capabilities().size());
        assertTrue(driver.capabilities().contains("testing"));
        assertTrue(driver.composable());
        assertFalse(driver.terminal());
    }

    @Test
    public void testRecordWithNullOptionalFields() {
        DriverCapability driver = new DriverCapability(
            "Minimal",
            "min",
            "org.example.Min",
            null,  // null description
            null,  // null capabilities
            null,  // null parameters
            null,  // null dependencies
            null,  // null sideEffects
            true,
            false
        );

        assertEquals("Minimal", driver.name());
        assertNull(driver.description());
        assertNull(driver.capabilities());
        assertNull(driver.parameters());
        assertNull(driver.dependencies());
        assertNull(driver.sideEffects());
    }

    // ========== hasCapability() tests ==========

    @Test
    public void testHasCapabilityReturnsTrue() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            List.of("caching", "pooling", "logging"),
            null, null, null, true, false
        );

        assertTrue(driver.hasCapability("caching"));
        assertTrue(driver.hasCapability("pooling"));
        assertTrue(driver.hasCapability("logging"));
    }

    @Test
    public void testHasCapabilityReturnsFalse() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            List.of("caching"),
            null, null, null, true, false
        );

        assertFalse(driver.hasCapability("pooling"));
        assertFalse(driver.hasCapability("nonexistent"));
    }

    @Test
    public void testHasCapabilityWithNullCapabilities() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null,  // null capabilities
            null, null, null, true, false
        );

        assertFalse(driver.hasCapability("anything"));
    }

    @Test
    public void testHasCapabilityWithEmptyCapabilities() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            List.of(),  // empty capabilities
            null, null, null, true, false
        );

        assertFalse(driver.hasCapability("anything"));
    }

    // ========== getParameter() tests ==========

    @Test
    public void testGetParameterFound() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "timeout", "integer", "Timeout in seconds", 30, false, 1, 300, null
        );
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, List.of(param), null, null, true, false
        );

        Optional<DriverCapability.Parameter> found = driver.getParameter("timeout");
        assertTrue(found.isPresent());
        assertEquals("timeout", found.get().name());
        assertEquals("integer", found.get().type());
    }

    @Test
    public void testGetParameterNotFound() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "timeout", "integer", null, null, false, null, null, null
        );
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, List.of(param), null, null, true, false
        );

        Optional<DriverCapability.Parameter> found = driver.getParameter("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    public void testGetParameterWithNullParameters() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, null, null, null, true, false
        );

        Optional<DriverCapability.Parameter> found = driver.getParameter("anything");
        assertFalse(found.isPresent());
    }

    @Test
    public void testGetParameterWithEmptyParameters() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, List.of(), null, null, true, false
        );

        Optional<DriverCapability.Parameter> found = driver.getParameter("anything");
        assertFalse(found.isPresent());
    }

    @Test
    public void testGetParameterMultipleParameters() {
        DriverCapability.Parameter param1 = new DriverCapability.Parameter(
            "timeout", "integer", null, 30, false, null, null, null
        );
        DriverCapability.Parameter param2 = new DriverCapability.Parameter(
            "retries", "integer", null, 3, false, null, null, null
        );
        DriverCapability.Parameter param3 = new DriverCapability.Parameter(
            "format", "string", null, "json", false, null, null, null
        );

        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, List.of(param1, param2, param3), null, null, true, false
        );

        assertTrue(driver.getParameter("timeout").isPresent());
        assertTrue(driver.getParameter("retries").isPresent());
        assertTrue(driver.getParameter("format").isPresent());
        assertFalse(driver.getParameter("unknown").isPresent());
    }

    // ========== getUrlPrefix() tests ==========

    @Test
    public void testGetUrlPrefix() {
        DriverCapability driver = new DriverCapability(
            "Test", "test", "org.example.Test", null,
            null, null, null, null, true, false
        );

        assertEquals("jdbc:test:", driver.getUrlPrefix());
    }

    @Test
    public void testGetUrlPrefixVariousDrivers() {
        assertEquals("jdbc:log:", createDriver("log").getUrlPrefix());
        assertEquals("jdbc:pool:", createDriver("pool").getUrlPrefix());
        assertEquals("jdbc:cache:", createDriver("cache").getUrlPrefix());
        assertEquals("jdbc:retry:", createDriver("retry").getUrlPrefix());
    }

    private DriverCapability createDriver(String prefix) {
        return new DriverCapability(
            "Driver", prefix, "org.example.Driver", null,
            null, null, null, null, true, false
        );
    }

    // ========== Parameter record tests ==========

    @Test
    public void testParameterRecordFields() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "timeout",
            "integer",
            "Connection timeout in seconds",
            30,
            true,
            1,
            300,
            null
        );

        assertEquals("timeout", param.name());
        assertEquals("integer", param.type());
        assertEquals("Connection timeout in seconds", param.description());
        assertEquals(30, param.defaultValue());
        assertTrue(param.required());
        assertEquals(1, param.min());
        assertEquals(300, param.max());
        assertNull(param.enumValues());
    }

    @Test
    public void testParameterWithEnumValues() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "level",
            "enum",
            "Log level",
            "INFO",
            false,
            null,
            null,
            List.of("DEBUG", "INFO", "WARN", "ERROR")
        );

        assertEquals("level", param.name());
        assertEquals("enum", param.type());
        assertEquals("INFO", param.defaultValue());
        assertNotNull(param.enumValues());
        assertEquals(4, param.enumValues().size());
        assertTrue(param.enumValues().contains("DEBUG"));
        assertTrue(param.enumValues().contains("ERROR"));
    }

    @Test
    public void testParameterWithNullFields() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "optional",
            "string",
            null,
            null,
            false,
            null,
            null,
            null
        );

        assertEquals("optional", param.name());
        assertNull(param.description());
        assertNull(param.defaultValue());
        assertNull(param.min());
        assertNull(param.max());
        assertNull(param.enumValues());
    }

    // ========== Dependency record tests ==========

    @Test
    public void testDependencyRecordFields() {
        DriverCapability.Dependency dep = new DriverCapability.Dependency(
            "com.zaxxer",
            "HikariCP",
            "5.0.0",
            true
        );

        assertEquals("com.zaxxer", dep.groupId());
        assertEquals("HikariCP", dep.artifactId());
        assertEquals("5.0.0", dep.version());
        assertTrue(dep.optional());
    }

    @Test
    public void testDependencyToMavenCoordinates() {
        DriverCapability.Dependency dep = new DriverCapability.Dependency(
            "com.zaxxer",
            "HikariCP",
            "5.0.0",
            true
        );

        assertEquals("com.zaxxer:HikariCP:5.0.0", dep.toMavenCoordinates());
    }

    @Test
    public void testDependencyToMavenCoordinatesWithoutVersion() {
        DriverCapability.Dependency dep = new DriverCapability.Dependency(
            "com.zaxxer",
            "HikariCP",
            null,
            true
        );

        assertEquals("com.zaxxer:HikariCP", dep.toMavenCoordinates());
    }

    @Test
    public void testDependencyNonOptional() {
        DriverCapability.Dependency dep = new DriverCapability.Dependency(
            "org.example",
            "required-lib",
            "1.0",
            false
        );

        assertFalse(dep.optional());
    }

    // ========== SideEffects record tests ==========

    @Test
    public void testSideEffectsAllTrue() {
        DriverCapability.SideEffects effects = new DriverCapability.SideEffects(
            true, true, true, true, true
        );

        assertTrue(effects.logging());
        assertTrue(effects.metrics());
        assertTrue(effects.network());
        assertTrue(effects.filesystem());
        assertTrue(effects.stateful());
    }

    @Test
    public void testSideEffectsAllFalse() {
        DriverCapability.SideEffects effects = new DriverCapability.SideEffects(
            false, false, false, false, false
        );

        assertFalse(effects.logging());
        assertFalse(effects.metrics());
        assertFalse(effects.network());
        assertFalse(effects.filesystem());
        assertFalse(effects.stateful());
    }

    @Test
    public void testSideEffectsNoneConstant() {
        DriverCapability.SideEffects none = DriverCapability.SideEffects.NONE;

        assertNotNull(none);
        assertFalse(none.logging());
        assertFalse(none.metrics());
        assertFalse(none.network());
        assertFalse(none.filesystem());
        assertFalse(none.stateful());
    }

    @Test
    public void testSideEffectsMixed() {
        DriverCapability.SideEffects effects = new DriverCapability.SideEffects(
            true, false, true, false, true
        );

        assertTrue(effects.logging());
        assertFalse(effects.metrics());
        assertTrue(effects.network());
        assertFalse(effects.filesystem());
        assertTrue(effects.stateful());
    }

    // ========== equals/hashCode tests ==========

    @Test
    public void testDriverCapabilityEquals() {
        DriverCapability driver1 = new DriverCapability(
            "Test", "test", "org.example.Test", "desc",
            List.of("cap"), List.of(), List.of(),
            DriverCapability.SideEffects.NONE, true, false
        );
        DriverCapability driver2 = new DriverCapability(
            "Test", "test", "org.example.Test", "desc",
            List.of("cap"), List.of(), List.of(),
            DriverCapability.SideEffects.NONE, true, false
        );

        assertEquals(driver1, driver2);
        assertEquals(driver1.hashCode(), driver2.hashCode());
    }

    @Test
    public void testDriverCapabilityNotEquals() {
        DriverCapability driver1 = new DriverCapability(
            "Test1", "test1", "org.example.Test1", null,
            null, null, null, null, true, false
        );
        DriverCapability driver2 = new DriverCapability(
            "Test2", "test2", "org.example.Test2", null,
            null, null, null, null, true, false
        );

        assertNotEquals(driver1, driver2);
    }

    @Test
    public void testParameterEquals() {
        DriverCapability.Parameter param1 = new DriverCapability.Parameter(
            "timeout", "integer", "desc", 30, true, 1, 100, null
        );
        DriverCapability.Parameter param2 = new DriverCapability.Parameter(
            "timeout", "integer", "desc", 30, true, 1, 100, null
        );

        assertEquals(param1, param2);
        assertEquals(param1.hashCode(), param2.hashCode());
    }

    @Test
    public void testDependencyEquals() {
        DriverCapability.Dependency dep1 = new DriverCapability.Dependency(
            "com.example", "lib", "1.0", true
        );
        DriverCapability.Dependency dep2 = new DriverCapability.Dependency(
            "com.example", "lib", "1.0", true
        );

        assertEquals(dep1, dep2);
        assertEquals(dep1.hashCode(), dep2.hashCode());
    }

    @Test
    public void testSideEffectsEquals() {
        DriverCapability.SideEffects effects1 = new DriverCapability.SideEffects(
            true, false, true, false, true
        );
        DriverCapability.SideEffects effects2 = new DriverCapability.SideEffects(
            true, false, true, false, true
        );

        assertEquals(effects1, effects2);
        assertEquals(effects1.hashCode(), effects2.hashCode());
    }

    // ========== toString tests ==========

    @Test
    public void testDriverCapabilityToString() {
        DriverCapability driver = new DriverCapability(
            "Test Driver", "test", "org.example.Test", "A test",
            List.of("testing"), null, null, null, true, false
        );

        String str = driver.toString();
        assertTrue(str.contains("Test Driver"));
        assertTrue(str.contains("test"));
        assertTrue(str.contains("org.example.Test"));
    }

    @Test
    public void testParameterToString() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "timeout", "integer", null, 30, false, null, null, null
        );

        String str = param.toString();
        assertTrue(str.contains("timeout"));
        assertTrue(str.contains("integer"));
    }

    @Test
    public void testDependencyToString() {
        DriverCapability.Dependency dep = new DriverCapability.Dependency(
            "com.example", "library", "1.0", true
        );

        String str = dep.toString();
        assertTrue(str.contains("com.example"));
        assertTrue(str.contains("library"));
    }

    // ========== Edge cases ==========

    @Test
    public void testDriverWithEmptyLists() {
        DriverCapability driver = new DriverCapability(
            "Empty", "empty", "org.example.Empty", null,
            List.of(), List.of(), List.of(),
            DriverCapability.SideEffects.NONE, true, false
        );

        assertTrue(driver.capabilities().isEmpty());
        assertTrue(driver.parameters().isEmpty());
        assertTrue(driver.dependencies().isEmpty());
        assertFalse(driver.hasCapability("any"));
    }

    @Test
    public void testDriverBothComposableAndTerminal() {
        // This is a valid case (e.g., sink driver)
        DriverCapability driver = new DriverCapability(
            "Sink", "sink", "org.example.Sink", null,
            null, null, null, null, true, true
        );

        assertTrue(driver.composable());
        assertTrue(driver.terminal());
    }

    @Test
    public void testDriverNeitherComposableNorTerminal() {
        DriverCapability driver = new DriverCapability(
            "Standalone", "standalone", "org.example.Standalone", null,
            null, null, null, null, false, false
        );

        assertFalse(driver.composable());
        assertFalse(driver.terminal());
    }

    @Test
    public void testParameterWithAllFields() {
        DriverCapability.Parameter param = new DriverCapability.Parameter(
            "level",
            "enum",
            "The logging level",
            "INFO",
            true,
            0,
            3,
            List.of("DEBUG", "INFO", "WARN", "ERROR")
        );

        assertEquals("level", param.name());
        assertEquals("enum", param.type());
        assertEquals("The logging level", param.description());
        assertEquals("INFO", param.defaultValue());
        assertTrue(param.required());
        assertEquals(0, param.min());
        assertEquals(3, param.max());
        assertEquals(4, param.enumValues().size());
    }

    @Test
    public void testMultipleDependencies() {
        DriverCapability.Dependency dep1 = new DriverCapability.Dependency(
            "com.zaxxer", "HikariCP", "5.0.0", true
        );
        DriverCapability.Dependency dep2 = new DriverCapability.Dependency(
            "redis.clients", "jedis", "4.0.0", true
        );

        DriverCapability driver = new DriverCapability(
            "Multi", "multi", "org.example.Multi", null,
            null, null, List.of(dep1, dep2), null, true, false
        );

        assertEquals(2, driver.dependencies().size());
        assertEquals("com.zaxxer:HikariCP:5.0.0", driver.dependencies().get(0).toMavenCoordinates());
        assertEquals("redis.clients:jedis:4.0.0", driver.dependencies().get(1).toMavenCoordinates());
    }
}
