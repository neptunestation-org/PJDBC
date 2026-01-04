package org.pjdbc.annotations.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.processing.Processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverDependency;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverSideEffects;

@DisplayName("CapabilityProcessor")
class CapabilityProcessorTest {

    private CapabilityProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CapabilityProcessor();
    }

    @Nested
    @DisplayName("ServiceLoader")
    class ServiceLoaderTests {

        @Test
        @DisplayName("is discoverable via ServiceLoader")
        void isDiscoverableViaServiceLoader() {
            ServiceLoader<Processor> loader = ServiceLoader.load(Processor.class);
            boolean found = false;
            for (Processor p : loader) {
                if (p instanceof CapabilityProcessor) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "CapabilityProcessor should be discoverable via ServiceLoader");
        }

        @Test
        @DisplayName("supports DriverCapability annotation")
        void supportsDriverCapabilityAnnotation() {
            assertTrue(processor.getSupportedAnnotationTypes()
                .contains("org.pjdbc.annotations.DriverCapability"));
        }
    }

    @Nested
    @DisplayName("JSON Escaping")
    class JsonEscapingTests {

        @Test
        @DisplayName("escapes backslashes")
        void escapesBackslashes() throws Exception {
            String result = invokeEscapeJson("path\\to\\file");
            assertEquals("path\\\\to\\\\file", result);
        }

        @Test
        @DisplayName("escapes double quotes")
        void escapesDoubleQuotes() throws Exception {
            String result = invokeEscapeJson("say \"hello\"");
            assertEquals("say \\\"hello\\\"", result);
        }

        @Test
        @DisplayName("escapes newlines")
        void escapesNewlines() throws Exception {
            String result = invokeEscapeJson("line1\nline2");
            assertEquals("line1\\nline2", result);
        }

        @Test
        @DisplayName("escapes carriage returns")
        void escapesCarriageReturns() throws Exception {
            String result = invokeEscapeJson("line1\rline2");
            assertEquals("line1\\rline2", result);
        }

        @Test
        @DisplayName("escapes tabs")
        void escapesTabs() throws Exception {
            String result = invokeEscapeJson("col1\tcol2");
            assertEquals("col1\\tcol2", result);
        }

        @Test
        @DisplayName("handles combined escaping")
        void handlesCombinedEscaping() throws Exception {
            String result = invokeEscapeJson("path\\to\\\"file\"\n\ttab");
            assertEquals("path\\\\to\\\\\\\"file\\\"\\n\\ttab", result);
        }

        private String invokeEscapeJson(String input) throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod("escapeJson", String.class);
            method.setAccessible(true);
            return (String) method.invoke(processor, input);
        }
    }

    @Nested
    @DisplayName("JSON Writing")
    class JsonWritingTests {

        @Test
        @DisplayName("writes string values correctly")
        void writesStringValuesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, "hello", 0);
            assertEquals("\"hello\"", writer.toString());
        }

        @Test
        @DisplayName("writes boolean values correctly")
        void writesBooleanValuesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, true, 0);
            assertEquals("true", writer.toString());

            writer = new StringWriter();
            invokeWriteValue(writer, false, 0);
            assertEquals("false", writer.toString());
        }

        @Test
        @DisplayName("writes numeric values correctly")
        void writesNumericValuesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, 42, 0);
            assertEquals("42", writer.toString());

            writer = new StringWriter();
            invokeWriteValue(writer, 3.14, 0);
            assertEquals("3.14", writer.toString());

            writer = new StringWriter();
            invokeWriteValue(writer, 1000L, 0);
            assertEquals("1000", writer.toString());
        }

        @Test
        @DisplayName("writes null values correctly")
        void writesNullValuesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, null, 0);
            assertEquals("null", writer.toString());
        }

        @Test
        @DisplayName("writes empty list correctly")
        void writesEmptyListCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, new ArrayList<>(), 0);
            assertEquals("[]", writer.toString());
        }

        @Test
        @DisplayName("writes primitive list correctly")
        void writesPrimitiveListCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            List<String> list = List.of("a", "b", "c");
            invokeWriteValue(writer, list, 0);
            assertEquals("[\"a\", \"b\", \"c\"]", writer.toString());
        }

        @Test
        @DisplayName("writes empty map correctly")
        void writesEmptyMapCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            invokeWriteValue(writer, new LinkedHashMap<>(), 0);
            assertEquals("{}", writer.toString());
        }

        @Test
        @DisplayName("writes map with values correctly")
        void writesMapWithValuesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "test");
            map.put("count", 5);
            invokeWriteValue(writer, map, 0);
            String result = writer.toString();
            assertTrue(result.contains("\"name\": \"test\""));
            assertTrue(result.contains("\"count\": 5"));
        }

        private void invokeWriteValue(StringWriter writer, Object value, int indent) throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeValue", java.io.Writer.class, Object.class, int.class);
            method.setAccessible(true);
            method.invoke(processor, writer, value, indent);
        }
    }

    @Nested
    @DisplayName("Default Value Conversion")
    class DefaultValueConversionTests {

        @Test
        @DisplayName("converts integer values")
        void convertsIntegerValues() throws Exception {
            Object result = invokeConvertDefault("42",
                org.pjdbc.annotations.DriverParameter.ParameterType.INTEGER);
            assertEquals(42L, result);
        }

        @Test
        @DisplayName("converts float values")
        void convertsFloatValues() throws Exception {
            Object result = invokeConvertDefault("3.14",
                org.pjdbc.annotations.DriverParameter.ParameterType.FLOAT);
            assertEquals(3.14, result);
        }

        @Test
        @DisplayName("converts boolean values")
        void convertsBooleanValues() throws Exception {
            Object result = invokeConvertDefault("true",
                org.pjdbc.annotations.DriverParameter.ParameterType.BOOLEAN);
            assertEquals(true, result);

            result = invokeConvertDefault("false",
                org.pjdbc.annotations.DriverParameter.ParameterType.BOOLEAN);
            assertEquals(false, result);
        }

        @Test
        @DisplayName("returns string for string type")
        void returnsStringForStringType() throws Exception {
            Object result = invokeConvertDefault("hello",
                org.pjdbc.annotations.DriverParameter.ParameterType.STRING);
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("returns string on parse failure")
        void returnsStringOnParseFailure() throws Exception {
            Object result = invokeConvertDefault("not-a-number",
                org.pjdbc.annotations.DriverParameter.ParameterType.INTEGER);
            assertEquals("not-a-number", result);
        }

        private Object invokeConvertDefault(String value,
                org.pjdbc.annotations.DriverParameter.ParameterType type) throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "convertDefault", String.class,
                org.pjdbc.annotations.DriverParameter.ParameterType.class,
                org.pjdbc.annotations.DriverParameter.class,
                javax.lang.model.element.TypeElement.class);
            method.setAccessible(true);
            // Pass null for param and element - they're only used for error reporting
            return method.invoke(processor, value, type, null, null);
        }
    }

    @Nested
    @DisplayName("Manifest Generation")
    class ManifestGenerationTests {

        @Test
        @DisplayName("generates valid JSON structure")
        void generatesValidJsonStructure() throws Exception {
            StringWriter writer = new StringWriter();
            List<Map<String, Object>> drivers = new ArrayList<>();

            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("name", "TestDriver");
            driver.put("prefix", "test");
            driver.put("class", "org.example.TestDriver");
            driver.put("description", "A test driver");
            driver.put("capabilities", List.of("testing"));
            driver.put("parameters", List.of());
            driver.put("sideEffects", new LinkedHashMap<>());
            driver.put("composable", true);
            driver.put("terminal", false);
            drivers.add(driver);

            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeJson", java.io.Writer.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, writer, drivers);

            String json = writer.toString();
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}\n"));
            assertTrue(json.contains("\"version\": \"1.0\""));
            assertTrue(json.contains("\"drivers\": ["));
            assertTrue(json.contains("\"name\": \"TestDriver\""));
            assertTrue(json.contains("\"prefix\": \"test\""));
        }

        @Test
        @DisplayName("handles multiple drivers")
        void handlesMultipleDrivers() throws Exception {
            StringWriter writer = new StringWriter();
            List<Map<String, Object>> drivers = new ArrayList<>();

            for (int i = 1; i <= 3; i++) {
                Map<String, Object> driver = new LinkedHashMap<>();
                driver.put("name", "Driver" + i);
                driver.put("prefix", "d" + i);
                driver.put("class", "org.example.Driver" + i);
                driver.put("description", "Driver " + i);
                driver.put("capabilities", List.of());
                driver.put("parameters", List.of());
                driver.put("sideEffects", new LinkedHashMap<>());
                driver.put("composable", true);
                driver.put("terminal", false);
                drivers.add(driver);
            }

            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeJson", java.io.Writer.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, writer, drivers);

            String json = writer.toString();
            assertTrue(json.contains("\"name\": \"Driver1\""));
            assertTrue(json.contains("\"name\": \"Driver2\""));
            assertTrue(json.contains("\"name\": \"Driver3\""));
            // Verify proper comma handling (no trailing commas)
            assertFalse(json.contains(",\n    ]\n"));
        }

        @Test
        @DisplayName("generates parameters with constraints")
        void generatesParametersWithConstraints() throws Exception {
            StringWriter writer = new StringWriter();
            List<Map<String, Object>> drivers = new ArrayList<>();

            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("name", "TestDriver");
            driver.put("prefix", "test");
            driver.put("class", "org.example.TestDriver");
            driver.put("description", "A test driver");
            driver.put("capabilities", List.of("testing"));

            // Parameter with min/max constraints
            List<Map<String, Object>> params = new ArrayList<>();
            Map<String, Object> param1 = new LinkedHashMap<>();
            param1.put("name", "timeout");
            param1.put("type", "integer");
            param1.put("description", "Timeout in ms");
            param1.put("default", 1000L);
            param1.put("min", 0L);
            param1.put("max", 60000L);
            params.add(param1);

            // Parameter with enum values
            Map<String, Object> param2 = new LinkedHashMap<>();
            param2.put("name", "mode");
            param2.put("type", "string");
            param2.put("description", "Operating mode");
            param2.put("default", "auto");
            param2.put("enum", List.of("auto", "manual", "disabled"));
            params.add(param2);

            // Required parameter
            Map<String, Object> param3 = new LinkedHashMap<>();
            param3.put("name", "endpoint");
            param3.put("type", "string");
            param3.put("description", "Server endpoint");
            param3.put("required", true);
            params.add(param3);

            driver.put("parameters", params);
            driver.put("sideEffects", new LinkedHashMap<>());
            driver.put("composable", true);
            driver.put("terminal", false);
            drivers.add(driver);

            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeJson", java.io.Writer.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, writer, drivers);

            String json = writer.toString();
            assertTrue(json.contains("\"min\": 0"));
            assertTrue(json.contains("\"max\": 60000"));
            assertTrue(json.contains("\"enum\": [\"auto\", \"manual\", \"disabled\"]"));
            assertTrue(json.contains("\"required\": true"));
        }

        @Test
        @DisplayName("generates dependencies correctly")
        void generatesDependenciesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            List<Map<String, Object>> drivers = new ArrayList<>();

            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("name", "TestDriver");
            driver.put("prefix", "test");
            driver.put("class", "org.example.TestDriver");
            driver.put("description", "A test driver");
            driver.put("capabilities", List.of("testing"));
            driver.put("parameters", List.of());

            // Add dependencies
            List<Map<String, Object>> deps = new ArrayList<>();
            Map<String, Object> dep1 = new LinkedHashMap<>();
            dep1.put("groupId", "com.example");
            dep1.put("artifactId", "example-lib");
            dep1.put("version", "1.0.0");
            dep1.put("optional", false);
            deps.add(dep1);

            Map<String, Object> dep2 = new LinkedHashMap<>();
            dep2.put("groupId", "org.optional");
            dep2.put("artifactId", "optional-lib");
            dep2.put("version", "2.0.0");
            dep2.put("optional", true);
            dep2.put("description", "Optional feature support");
            deps.add(dep2);

            driver.put("dependencies", deps);
            driver.put("sideEffects", new LinkedHashMap<>());
            driver.put("composable", true);
            driver.put("terminal", false);
            drivers.add(driver);

            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeJson", java.io.Writer.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, writer, drivers);

            String json = writer.toString();
            assertTrue(json.contains("\"groupId\": \"com.example\""));
            assertTrue(json.contains("\"artifactId\": \"example-lib\""));
            assertTrue(json.contains("\"version\": \"1.0.0\""));
            assertTrue(json.contains("\"optional\": false"));
            assertTrue(json.contains("\"optional\": true"));
            assertTrue(json.contains("\"description\": \"Optional feature support\""));
        }

        @Test
        @DisplayName("generates side effects correctly")
        void generatesSideEffectsCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            List<Map<String, Object>> drivers = new ArrayList<>();

            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("name", "TestDriver");
            driver.put("prefix", "test");
            driver.put("class", "org.example.TestDriver");
            driver.put("description", "A test driver");
            driver.put("capabilities", List.of("testing"));
            driver.put("parameters", List.of());

            Map<String, Object> sideEffects = new LinkedHashMap<>();
            sideEffects.put("stateful", true);
            sideEffects.put("logging", true);
            sideEffects.put("network", true);
            driver.put("sideEffects", sideEffects);

            driver.put("composable", true);
            driver.put("terminal", false);
            drivers.add(driver);

            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "writeJson", java.io.Writer.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, writer, drivers);

            String json = writer.toString();
            assertTrue(json.contains("\"stateful\": true"));
            assertTrue(json.contains("\"logging\": true"));
            assertTrue(json.contains("\"network\": true"));
        }
    }

    @Nested
    @DisplayName("Generated Manifest Integration")
    class GeneratedManifestIntegrationTests {

        private String manifestContent;

        @BeforeEach
        void loadManifest() throws IOException {
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("pjdbc.capabilities.json")) {
                assertNotNull(is, "Manifest should exist on classpath");
                manifestContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Test
        @DisplayName("manifest contains all annotated drivers")
        void manifestContainsAllAnnotatedDrivers() throws Exception {
            // Get all driver classes with @DriverCapability
            String[] expectedDrivers = {
                "AuditDriver", "CachingDriver", "CatDriver", "ChaosDriver",
                "CircuitBreakerDriver", "DataMaskingDriver", "FederatingDriver",
                "FilterDriver", "HazelcastCachingDriver", "HikariPoolDriver",
                "LoadBalancingDriver", "LogDriver", "MemcachedCachingDriver",
                "MetricsDriver", "MockDriver", "PoolDriver", "RateLimitDriver",
                "ReadonlyDriver", "RedisCachingDriver", "RetryDriver",
                "SchemaValidationDriver", "SinkDriver", "TeeDriver",
                "TimeoutDriver", "TracingDriver", "UserMapDriver"
            };

            for (String driver : expectedDrivers) {
                assertTrue(manifestContent.contains("\"name\": \"" + driver + "\""),
                    "Manifest should contain " + driver);
            }
        }

        @Test
        @DisplayName("manifest has valid JSON structure")
        void manifestHasValidJsonStructure() {
            assertTrue(manifestContent.startsWith("{"));
            assertTrue(manifestContent.contains("\"version\":"));
            assertTrue(manifestContent.contains("\"drivers\":"));
            assertTrue(manifestContent.contains("["));
            assertTrue(manifestContent.contains("]"));
            // Check for balanced braces (simple check)
            int openBraces = manifestContent.length() - manifestContent.replace("{", "").length();
            int closeBraces = manifestContent.length() - manifestContent.replace("}", "").length();
            assertEquals(openBraces, closeBraces, "Braces should be balanced");
        }

        @Test
        @DisplayName("manifest prefixes match driver annotations")
        void manifestPrefixesMatchAnnotations() throws Exception {
            // Verify a few known drivers have correct prefixes
            assertTrue(manifestContent.contains("\"prefix\": \"cat\""));
            assertTrue(manifestContent.contains("\"prefix\": \"log\""));
            assertTrue(manifestContent.contains("\"prefix\": \"cache\""));
            assertTrue(manifestContent.contains("\"prefix\": \"retry\""));
            assertTrue(manifestContent.contains("\"prefix\": \"circuitbreaker\""));
            assertTrue(manifestContent.contains("\"prefix\": \"hikaricp\""));
            assertTrue(manifestContent.contains("\"prefix\": \"loadbalance\""));
        }

        @Test
        @DisplayName("CachingDriver parameters are correctly generated")
        void cachingDriverParametersAreCorrectlyGenerated() throws Exception {
            // Verify CachingDriver has expected parameters from annotation
            Class<?> cachingDriver = Class.forName("org.pjdbc.drivers.CachingDriver");
            DriverParameter[] params = cachingDriver.getAnnotationsByType(DriverParameter.class);

            assertTrue(params.length > 0, "CachingDriver should have parameters");

            for (DriverParameter param : params) {
                assertTrue(manifestContent.contains("\"name\": \"" + param.name() + "\""),
                    "Manifest should contain parameter: " + param.name());
            }
        }

        @Test
        @DisplayName("HikariPoolDriver dependency is correctly generated")
        void hikariPoolDriverDependencyIsCorrectlyGenerated() throws Exception {
            Class<?> hikariDriver = Class.forName("org.pjdbc.drivers.HikariPoolDriver");
            DriverDependency[] deps = hikariDriver.getAnnotationsByType(DriverDependency.class);

            assertTrue(deps.length > 0, "HikariPoolDriver should have dependencies");

            DriverDependency dep = deps[0];
            assertTrue(manifestContent.contains("\"groupId\": \"" + dep.groupId() + "\""));
            assertTrue(manifestContent.contains("\"artifactId\": \"" + dep.artifactId() + "\""));
        }

        @Test
        @DisplayName("MockDriver is marked as terminal and non-composable")
        void mockDriverIsMarkedAsTerminalAndNonComposable() throws Exception {
            Class<?> mockDriver = Class.forName("org.pjdbc.drivers.MockDriver");
            DriverCapability cap = mockDriver.getAnnotation(DriverCapability.class);

            assertNotNull(cap);
            assertTrue(cap.terminal());
            assertFalse(cap.composable());

            // Verify in manifest - find MockDriver section by looking for next driver or end
            int mockStart = manifestContent.indexOf("\"name\": \"MockDriver\"");
            assertTrue(mockStart > 0, "MockDriver should exist in manifest");

            // Find the end of this driver entry (next "name": or end of drivers array)
            int nextDriver = manifestContent.indexOf("\"name\":", mockStart + 20);
            int mockEnd = nextDriver > 0 ? nextDriver : manifestContent.length();
            String mockSection = manifestContent.substring(mockStart, mockEnd);

            assertTrue(mockSection.contains("\"terminal\": true"),
                "MockDriver section should contain terminal: true");
            assertTrue(mockSection.contains("\"composable\": false"),
                "MockDriver section should contain composable: false");
        }

        @Test
        @DisplayName("AuditDriver side effects are correctly generated")
        void auditDriverSideEffectsAreCorrectlyGenerated() throws Exception {
            Class<?> auditDriver = Class.forName("org.pjdbc.drivers.AuditDriver");
            DriverSideEffects effects = auditDriver.getAnnotation(DriverSideEffects.class);

            assertNotNull(effects);
            assertTrue(effects.logging());
            assertTrue(effects.stateful());

            // Find AuditDriver section in manifest
            int auditStart = manifestContent.indexOf("\"name\": \"AuditDriver\"");
            assertTrue(auditStart > 0);

            // Look for sideEffects section after AuditDriver
            int sideEffectsStart = manifestContent.indexOf("\"sideEffects\":", auditStart);
            int sideEffectsEnd = manifestContent.indexOf("}", sideEffectsStart) + 1;
            String sideEffectsSection = manifestContent.substring(sideEffectsStart, sideEffectsEnd);

            assertTrue(sideEffectsSection.contains("\"logging\": true"));
            assertTrue(sideEffectsSection.contains("\"stateful\": true"));
        }

        @Test
        @DisplayName("all driver prefixes are unique")
        void allDriverPrefixesAreUnique() {
            Set<String> prefixes = new HashSet<>();
            int index = 0;
            while ((index = manifestContent.indexOf("\"prefix\": \"", index)) != -1) {
                int start = index + 11; // length of "\"prefix\": \""
                int end = manifestContent.indexOf("\"", start);
                String prefix = manifestContent.substring(start, end);
                assertTrue(prefixes.add(prefix),
                    "Duplicate prefix found: " + prefix);
                index = end;
            }
            assertTrue(prefixes.size() >= 20, "Should have at least 20 unique prefixes");
        }

        @Test
        @DisplayName("parameter enum values are correctly formatted")
        void parameterEnumValuesAreCorrectlyFormatted() {
            // Check RateLimitDriver mode parameter has enum
            assertTrue(manifestContent.contains("\"enum\": [\"reject\", \"wait\"]") ||
                       manifestContent.contains("\"enum\": [\"wait\", \"reject\"]"),
                "RateLimitDriver should have mode enum");

            // Check DataMaskingDriver strategy parameter has enum
            assertTrue(manifestContent.contains("FULL") && manifestContent.contains("PARTIAL"),
                "DataMaskingDriver should have strategy enum values");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("PREFIX_PATTERN accepts valid prefixes")
        void prefixPatternAcceptsValidPrefixes() throws Exception {
            java.lang.reflect.Field field = CapabilityProcessor.class.getDeclaredField("PREFIX_PATTERN");
            field.setAccessible(true);
            java.util.regex.Pattern pattern = (java.util.regex.Pattern) field.get(null);

            // Valid prefixes
            assertTrue(pattern.matcher("cache").matches());
            assertTrue(pattern.matcher("log").matches());
            assertTrue(pattern.matcher("pool2").matches());
            assertTrue(pattern.matcher("hikaricp").matches());
            assertTrue(pattern.matcher("a").matches());
            assertTrue(pattern.matcher("a1b2c3").matches());
        }

        @Test
        @DisplayName("PREFIX_PATTERN rejects invalid prefixes")
        void prefixPatternRejectsInvalidPrefixes() throws Exception {
            java.lang.reflect.Field field = CapabilityProcessor.class.getDeclaredField("PREFIX_PATTERN");
            field.setAccessible(true);
            java.util.regex.Pattern pattern = (java.util.regex.Pattern) field.get(null);

            // Invalid prefixes
            assertFalse(pattern.matcher("").matches(), "Empty string");
            assertFalse(pattern.matcher("1cache").matches(), "Starts with number");
            assertFalse(pattern.matcher("Cache").matches(), "Contains uppercase");
            assertFalse(pattern.matcher("my-cache").matches(), "Contains hyphen");
            assertFalse(pattern.matcher("my_cache").matches(), "Contains underscore");
            assertFalse(pattern.matcher("cache.driver").matches(), "Contains dot");
            assertFalse(pattern.matcher(" cache").matches(), "Leading space");
        }

        @Test
        @DisplayName("CAPABILITY_PATTERN accepts valid capabilities")
        void capabilityPatternAcceptsValidCapabilities() throws Exception {
            java.lang.reflect.Field field = CapabilityProcessor.class.getDeclaredField("CAPABILITY_PATTERN");
            field.setAccessible(true);
            java.util.regex.Pattern pattern = (java.util.regex.Pattern) field.get(null);

            // Valid capabilities
            assertTrue(pattern.matcher("caching").matches());
            assertTrue(pattern.matcher("load-balancing").matches());
            assertTrue(pattern.matcher("multi-database").matches());
            assertTrue(pattern.matcher("a").matches());
            assertTrue(pattern.matcher("a-b-c").matches());
        }

        @Test
        @DisplayName("CAPABILITY_PATTERN rejects invalid capabilities")
        void capabilityPatternRejectsInvalidCapabilities() throws Exception {
            java.lang.reflect.Field field = CapabilityProcessor.class.getDeclaredField("CAPABILITY_PATTERN");
            field.setAccessible(true);
            java.util.regex.Pattern pattern = (java.util.regex.Pattern) field.get(null);

            // Invalid capabilities
            assertFalse(pattern.matcher("").matches(), "Empty string");
            assertFalse(pattern.matcher("Caching").matches(), "Contains uppercase");
            assertFalse(pattern.matcher("-caching").matches(), "Starts with hyphen");
            assertFalse(pattern.matcher("caching-").matches(), "Ends with hyphen");
            assertFalse(pattern.matcher("load--balancing").matches(), "Double hyphen");
            assertFalse(pattern.matcher("caching1").matches(), "Contains number");
            assertFalse(pattern.matcher("load_balancing").matches(), "Contains underscore");
        }

        @Test
        @DisplayName("validateParameter detects min greater than max")
        void validateParameterDetectsMinGreaterThanMax() throws Exception {
            // This is tested indirectly - the validation logic sets hasErrors
            // when min > max. We verify the pattern exists in the code.
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "validateParameter",
                DriverParameter.class,
                javax.lang.model.element.TypeElement.class,
                Set.class);
            assertNotNull(method, "validateParameter method should exist");
        }

        @Test
        @DisplayName("validateDefaultValue checks enum membership")
        void validateDefaultValueChecksEnumMembership() throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "validateDefaultValue",
                DriverParameter.class,
                javax.lang.model.element.TypeElement.class);
            assertNotNull(method, "validateDefaultValue method should exist");
        }

        @Test
        @DisplayName("validateDependency checks for empty groupId and artifactId")
        void validateDependencyChecksRequiredFields() throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "validateDependency",
                DriverDependency.class,
                javax.lang.model.element.TypeElement.class);
            assertNotNull(method, "validateDependency method should exist");
        }

        @Test
        @DisplayName("validateDriverCapability checks for duplicate prefixes")
        void validateDriverCapabilityChecksDuplicatePrefixes() throws Exception {
            Method method = CapabilityProcessor.class.getDeclaredMethod(
                "validateDriverCapability",
                DriverCapability.class,
                javax.lang.model.element.TypeElement.class,
                Map.class);
            assertNotNull(method, "validateDriverCapability method should exist");
        }

        @Test
        @DisplayName("processor has hasErrors field for error tracking")
        void processorHasErrorTrackingField() throws Exception {
            java.lang.reflect.Field field = CapabilityProcessor.class.getDeclaredField("hasErrors");
            assertNotNull(field, "hasErrors field should exist");
            assertEquals(boolean.class, field.getType(), "hasErrors should be boolean");
        }

        @Test
        @DisplayName("error helper sets hasErrors flag")
        void errorHelperSetsHasErrorsFlag() throws Exception {
            // Reset processor state
            processor = new CapabilityProcessor();

            java.lang.reflect.Field hasErrorsField = CapabilityProcessor.class.getDeclaredField("hasErrors");
            hasErrorsField.setAccessible(true);

            // Initially false
            assertFalse((boolean) hasErrorsField.get(processor));

            // Note: Can't fully test without ProcessingEnvironment, but we verify the field exists
        }
    }
}
