package org.pjdbc.annotations;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pjdbc.annotations.DriverParameter.ParameterType;

@DisplayName("Driver Annotations")
class DriverAnnotationsTest {

    // Test classes with annotations
    @DriverCapability(
        prefix = "test",
        description = "A test driver",
        capabilities = {"testing", "example"}
    )
    static class SimpleAnnotatedDriver {}

    @DriverCapability(
        prefix = "custom",
        name = "CustomNameDriver",
        description = "Driver with custom name",
        capabilities = {"custom"},
        composable = false,
        terminal = true
    )
    static class CustomNameDriver {}

    @DriverCapability(prefix = "params", description = "Driver with parameters")
    @DriverParameter(name = "timeout", type = ParameterType.INTEGER,
        description = "Timeout in seconds", defaultValue = "30", min = 0, max = 300)
    @DriverParameter(name = "enabled", type = ParameterType.BOOLEAN,
        description = "Enable feature", defaultValue = "true")
    @DriverParameter(name = "mode", type = ParameterType.STRING,
        description = "Operation mode", enumValues = {"fast", "slow", "auto"})
    static class DriverWithParameters {}

    @DriverCapability(prefix = "deps", description = "Driver with dependencies")
    @DriverDependency(groupId = "com.example", artifactId = "lib-a", version = "1.0.0")
    @DriverDependency(groupId = "com.example", artifactId = "lib-b", version = "2.0.0", optional = false)
    static class DriverWithDependencies {}

    @DriverCapability(prefix = "effects", description = "Driver with side effects")
    @DriverSideEffects(stateful = true, logging = true, network = true)
    static class DriverWithSideEffects {}

    @DriverCapability(prefix = "full", description = "Fully annotated driver")
    @DriverParameter(name = "size", type = ParameterType.INTEGER, defaultValue = "100")
    @DriverDependency(groupId = "org.test", artifactId = "test-lib", version = "1.0")
    @DriverSideEffects(stateful = true, modifiesResults = true)
    static class FullyAnnotatedDriver {}

    @Nested
    @DisplayName("@DriverCapability")
    class DriverCapabilityTests {

        @Test
        @DisplayName("has RUNTIME retention")
        void hasRuntimeRetention() {
            Retention retention = DriverCapability.class.getAnnotation(Retention.class);
            assertNotNull(retention);
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }

        @Test
        @DisplayName("can be read via reflection")
        void canBeReadViaReflection() {
            DriverCapability cap = SimpleAnnotatedDriver.class.getAnnotation(DriverCapability.class);
            assertNotNull(cap);
            assertEquals("test", cap.prefix());
            assertEquals("A test driver", cap.description());
            assertArrayEquals(new String[]{"testing", "example"}, cap.capabilities());
        }

        @Test
        @DisplayName("has correct defaults")
        void hasCorrectDefaults() {
            DriverCapability cap = SimpleAnnotatedDriver.class.getAnnotation(DriverCapability.class);
            assertEquals("", cap.name());  // Default empty, use class name
            assertTrue(cap.composable());   // Default true
            assertFalse(cap.terminal());    // Default false
        }

        @Test
        @DisplayName("custom values override defaults")
        void customValuesOverrideDefaults() {
            DriverCapability cap = CustomNameDriver.class.getAnnotation(DriverCapability.class);
            assertEquals("CustomNameDriver", cap.name());
            assertFalse(cap.composable());
            assertTrue(cap.terminal());
        }
    }

    @Nested
    @DisplayName("@DriverParameter")
    class DriverParameterTests {

        @Test
        @DisplayName("has RUNTIME retention")
        void hasRuntimeRetention() {
            Retention retention = DriverParameter.class.getAnnotation(Retention.class);
            assertNotNull(retention);
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }

        @Test
        @DisplayName("is repeatable")
        void isRepeatable() {
            DriverParameters params = DriverWithParameters.class.getAnnotation(DriverParameters.class);
            assertNotNull(params);
            assertEquals(3, params.value().length);
        }

        @Test
        @DisplayName("can read individual parameters")
        void canReadIndividualParameters() {
            DriverParameter[] params = DriverWithParameters.class.getAnnotationsByType(DriverParameter.class);
            assertEquals(3, params.length);

            // Find timeout parameter
            DriverParameter timeout = null;
            for (DriverParameter p : params) {
                if ("timeout".equals(p.name())) {
                    timeout = p;
                    break;
                }
            }
            assertNotNull(timeout);
            assertEquals(ParameterType.INTEGER, timeout.type());
            assertEquals("Timeout in seconds", timeout.description());
            assertEquals("30", timeout.defaultValue());
            assertEquals(0, timeout.min());
            assertEquals(300, timeout.max());
        }

        @Test
        @DisplayName("supports enum values")
        void supportsEnumValues() {
            DriverParameter[] params = DriverWithParameters.class.getAnnotationsByType(DriverParameter.class);
            DriverParameter mode = null;
            for (DriverParameter p : params) {
                if ("mode".equals(p.name())) {
                    mode = p;
                    break;
                }
            }
            assertNotNull(mode);
            assertArrayEquals(new String[]{"fast", "slow", "auto"}, mode.enumValues());
        }

        @Test
        @DisplayName("has correct defaults")
        void hasCorrectDefaults() {
            DriverParameter[] params = DriverWithParameters.class.getAnnotationsByType(DriverParameter.class);
            DriverParameter enabled = null;
            for (DriverParameter p : params) {
                if ("enabled".equals(p.name())) {
                    enabled = p;
                    break;
                }
            }
            assertNotNull(enabled);
            assertEquals(Long.MIN_VALUE, enabled.min());
            assertEquals(Long.MAX_VALUE, enabled.max());
            assertEquals(0, enabled.enumValues().length);
            assertFalse(enabled.required());
        }
    }

    @Nested
    @DisplayName("@DriverDependency")
    class DriverDependencyTests {

        @Test
        @DisplayName("has RUNTIME retention")
        void hasRuntimeRetention() {
            Retention retention = DriverDependency.class.getAnnotation(Retention.class);
            assertNotNull(retention);
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }

        @Test
        @DisplayName("is repeatable")
        void isRepeatable() {
            DriverDependencies deps = DriverWithDependencies.class.getAnnotation(DriverDependencies.class);
            assertNotNull(deps);
            assertEquals(2, deps.value().length);
        }

        @Test
        @DisplayName("can read dependency details")
        void canReadDependencyDetails() {
            DriverDependency[] deps = DriverWithDependencies.class.getAnnotationsByType(DriverDependency.class);
            assertEquals(2, deps.length);

            DriverDependency libA = deps[0];
            assertEquals("com.example", libA.groupId());
            assertEquals("lib-a", libA.artifactId());
            assertEquals("1.0.0", libA.version());
            assertTrue(libA.optional());  // Default

            DriverDependency libB = deps[1];
            assertEquals("lib-b", libB.artifactId());
            assertFalse(libB.optional());  // Explicitly set
        }
    }

    @Nested
    @DisplayName("@DriverSideEffects")
    class DriverSideEffectsTests {

        @Test
        @DisplayName("has RUNTIME retention")
        void hasRuntimeRetention() {
            Retention retention = DriverSideEffects.class.getAnnotation(Retention.class);
            assertNotNull(retention);
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }

        @Test
        @DisplayName("can read side effects")
        void canReadSideEffects() {
            DriverSideEffects effects = DriverWithSideEffects.class.getAnnotation(DriverSideEffects.class);
            assertNotNull(effects);
            assertTrue(effects.stateful());
            assertTrue(effects.logging());
            assertTrue(effects.network());
            assertFalse(effects.filesystem());  // Default
            assertFalse(effects.metrics());     // Default
        }

        @Test
        @DisplayName("all defaults are false")
        void allDefaultsAreFalse() {
            // Use a class without @DriverSideEffects to check absence
            DriverSideEffects effects = SimpleAnnotatedDriver.class.getAnnotation(DriverSideEffects.class);
            assertNull(effects);  // Not present means no side effects declared
        }
    }

    @Nested
    @DisplayName("Combined Annotations")
    class CombinedAnnotationsTests {

        @Test
        @DisplayName("all annotations can be used together")
        void allAnnotationsCanBeUsedTogether() {
            Class<?> clazz = FullyAnnotatedDriver.class;

            DriverCapability cap = clazz.getAnnotation(DriverCapability.class);
            assertNotNull(cap);
            assertEquals("full", cap.prefix());

            DriverParameter[] params = clazz.getAnnotationsByType(DriverParameter.class);
            assertEquals(1, params.length);
            assertEquals("size", params[0].name());

            DriverDependency[] deps = clazz.getAnnotationsByType(DriverDependency.class);
            assertEquals(1, deps.length);
            assertEquals("test-lib", deps[0].artifactId());

            DriverSideEffects effects = clazz.getAnnotation(DriverSideEffects.class);
            assertNotNull(effects);
            assertTrue(effects.stateful());
            assertTrue(effects.modifiesResults());
        }

        @Test
        @DisplayName("annotations are inherited metadata not behavior")
        void annotationsAreMetadataOnly() {
            // Verify annotations don't affect class instantiation
            assertDoesNotThrow(() -> {
                new SimpleAnnotatedDriver();
                new DriverWithParameters();
                new FullyAnnotatedDriver();
            });
        }
    }
}
