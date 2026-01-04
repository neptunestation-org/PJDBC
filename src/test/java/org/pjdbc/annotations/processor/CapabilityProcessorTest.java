package org.pjdbc.annotations.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.annotation.processing.Processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
                org.pjdbc.annotations.DriverParameter.ParameterType.class);
            method.setAccessible(true);
            return method.invoke(processor, value, type);
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
    }
}
