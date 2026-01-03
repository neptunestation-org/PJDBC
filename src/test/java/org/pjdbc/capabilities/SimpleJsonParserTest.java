package org.pjdbc.capabilities;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

/**
 * Unit tests for SimpleJsonParser.
 * Tests are executed through PjdbcCapabilities.parse() since the parser is package-private.
 */
public class SimpleJsonParserTest {

    // ========== Basic structure parsing ==========

    @Test
    public void testParseMinimalCapabilities() {
        String json = """
            {"version": "1.0", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion());
        assertEquals(0, caps.getDriverCount());
    }

    @Test
    public void testParseEmptyObject() {
        String json = "{}";
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion()); // default
        assertEquals(0, caps.getDriverCount());
    }

    @Test
    public void testParseWithWhitespace() {
        String json = """
            {
                "version"  :  "2.0"  ,
                "drivers"  :  [  ]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("2.0", caps.getVersion());
    }

    @Test
    public void testParseWithTabs() {
        String json = "{\t\"version\":\t\"1.5\",\t\"drivers\":\t[]\t}";
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.5", caps.getVersion());
    }

    @Test
    public void testParseWithNewlines() {
        String json = "{\n\"version\":\n\"1.0\"\n,\n\"drivers\":\n[\n]\n}";
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion());
    }

    // ========== String parsing ==========

    @Test
    public void testParseSimpleString() {
        String json = """
            {"version": "hello world", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("hello world", caps.getVersion());
    }

    @Test
    public void testParseEmptyString() {
        String json = """
            {"version": "", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("", caps.getVersion());
    }

    @Test
    public void testParseStringWithEscapedQuotes() {
        String json = """
            {"version": "say \\"hello\\"", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("say \"hello\"", caps.getVersion());
    }

    @Test
    public void testParseStringWithEscapedBackslash() {
        String json = """
            {"version": "path\\\\to\\\\file", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("path\\to\\file", caps.getVersion());
    }

    @Test
    public void testParseStringWithNewlineEscape() {
        String json = """
            {"version": "line1\\nline2", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("line1\nline2", caps.getVersion());
    }

    @Test
    public void testParseStringWithTabEscape() {
        String json = """
            {"version": "col1\\tcol2", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("col1\tcol2", caps.getVersion());
    }

    @Test
    public void testParseStringWithCarriageReturn() {
        String json = """
            {"version": "line\\r\\n", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("line\r\n", caps.getVersion());
    }

    @Test
    public void testParseStringWithSlashEscape() {
        String json = """
            {"version": "a\\/b", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("a/b", caps.getVersion());
    }

    // ========== Boolean parsing ==========

    @Test
    public void testParseBooleanTrue() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "composable": true,
                    "terminal": false
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());
        assertTrue(driver.get().composable());
        assertFalse(driver.get().terminal());
    }

    @Test
    public void testParseBooleanFalse() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "composable": false,
                    "terminal": true
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());
        assertFalse(driver.get().composable());
        assertTrue(driver.get().terminal());
    }

    // ========== Number parsing ==========

    @Test
    public void testParseIntegerNumber() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "timeout",
                        "type": "integer",
                        "min": 0,
                        "max": 1000
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("timeout");
        assertTrue(param.isPresent());
        assertEquals(0, param.get().min().intValue());
        assertEquals(1000, param.get().max().intValue());
    }

    @Test
    public void testParseNegativeNumber() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "offset",
                        "type": "integer",
                        "min": -100,
                        "max": 100
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("offset");
        assertTrue(param.isPresent());
        assertEquals(-100, param.get().min().intValue());
    }

    @Test
    public void testParseFloatingPointNumber() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "rate",
                        "type": "double",
                        "min": 0.0,
                        "max": 1.5
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("rate");
        assertTrue(param.isPresent());
        assertEquals(0.0, param.get().min().doubleValue(), 0.001);
        assertEquals(1.5, param.get().max().doubleValue(), 0.001);
    }

    // ========== Array parsing ==========

    @Test
    public void testParseEmptyArray() {
        String json = """
            {"version": "1.0", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertTrue(caps.getAllDrivers().isEmpty());
    }

    @Test
    public void testParseStringArray() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "capabilities": ["cap1", "cap2", "cap3"]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());
        assertEquals(3, driver.get().capabilities().size());
        assertTrue(driver.get().hasCapability("cap1"));
        assertTrue(driver.get().hasCapability("cap2"));
        assertTrue(driver.get().hasCapability("cap3"));
    }

    @Test
    public void testParseArrayWithWhitespace() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "capabilities": [  "a"  ,  "b"  ]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());
        assertEquals(2, driver.get().capabilities().size());
    }

    // ========== Nested object parsing ==========

    @Test
    public void testParseSideEffects() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "sideEffects": {
                        "logging": true,
                        "metrics": false,
                        "network": true,
                        "filesystem": false,
                        "stateful": true
                    }
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        DriverCapability.SideEffects effects = driver.get().sideEffects();
        assertTrue(effects.logging());
        assertFalse(effects.metrics());
        assertTrue(effects.network());
        assertFalse(effects.filesystem());
        assertTrue(effects.stateful());
    }

    @Test
    public void testParseEmptySideEffects() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "sideEffects": {}
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        DriverCapability.SideEffects effects = driver.get().sideEffects();
        assertFalse(effects.logging());
        assertFalse(effects.metrics());
        assertFalse(effects.network());
        assertFalse(effects.filesystem());
        assertFalse(effects.stateful());
    }

    @Test
    public void testParseDependencies() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "dependencies": [{
                        "groupId": "com.example",
                        "artifactId": "library",
                        "version": "1.2.3",
                        "optional": true
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        List<DriverCapability.Dependency> deps = driver.get().dependencies();
        assertEquals(1, deps.size());

        DriverCapability.Dependency dep = deps.get(0);
        assertEquals("com.example", dep.groupId());
        assertEquals("library", dep.artifactId());
        assertEquals("1.2.3", dep.version());
        assertTrue(dep.optional());
    }

    @Test
    public void testParseParameters() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "timeout",
                        "type": "integer",
                        "description": "Timeout in seconds",
                        "default": 30,
                        "required": false,
                        "min": 1,
                        "max": 300
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("timeout");
        assertTrue(param.isPresent());
        assertEquals("timeout", param.get().name());
        assertEquals("integer", param.get().type());
        assertEquals("Timeout in seconds", param.get().description());
        assertEquals(30, ((Number) param.get().defaultValue()).intValue());
        assertFalse(param.get().required());
        assertEquals(1, param.get().min().intValue());
        assertEquals(300, param.get().max().intValue());
    }

    @Test
    public void testParseParameterWithEnumValues() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "level",
                        "type": "enum",
                        "enum": ["DEBUG", "INFO", "WARN", "ERROR"]
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("level");
        assertTrue(param.isPresent());
        assertNotNull(param.get().enumValues());
        assertEquals(4, param.get().enumValues().size());
        assertTrue(param.get().enumValues().contains("DEBUG"));
        assertTrue(param.get().enumValues().contains("ERROR"));
    }

    // ========== Multiple drivers ==========

    @Test
    public void testParseMultipleDrivers() {
        String json = """
            {
                "version": "1.0",
                "drivers": [
                    {"name": "Driver1", "prefix": "d1", "class": "org.example.D1"},
                    {"name": "Driver2", "prefix": "d2", "class": "org.example.D2"},
                    {"name": "Driver3", "prefix": "d3", "class": "org.example.D3"}
                ]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals(3, caps.getDriverCount());
        assertTrue(caps.hasDriver("d1"));
        assertTrue(caps.hasDriver("d2"));
        assertTrue(caps.hasDriver("d3"));
    }

    // ========== Unknown fields handling ==========

    @Test
    public void testIgnoresUnknownTopLevelFields() {
        String json = """
            {
                "version": "1.0",
                "unknown": "value",
                "drivers": [],
                "anotherUnknown": 123
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion());
    }

    @Test
    public void testIgnoresUnknownDriverFields() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "unknownField": "ignored",
                    "anotherUnknown": [1, 2, 3]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertTrue(caps.hasDriver("test"));
    }

    @Test
    public void testIgnoresUnknownNestedObject() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "unknownObject": {"nested": {"deeply": true}}
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertTrue(caps.hasDriver("test"));
    }

    // ========== Null handling ==========

    @Test
    public void testParseNullValue() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "optional",
                        "type": "string",
                        "default": null
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("optional");
        assertTrue(param.isPresent());
        assertNull(param.get().defaultValue());
    }

    // ========== Error cases ==========

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnUnterminatedString() {
        String json = """
            {"version": "unterminated
            """;
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnMissingOpenBrace() {
        String json = """
            "version": "1.0"}
            """;
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnMissingColon() {
        String json = """
            {"version" "1.0"}
            """;
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnInvalidBoolean() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "composable": yes
                }]
            }
            """;
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnUnexpectedEndOfInput() {
        String json = "{\"version\":";
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnUnclosedArray() {
        String json = """
            {"version": "1.0", "drivers": [
            """;
        PjdbcCapabilities.parse(json);
    }

    @Test(expected = PjdbcCapabilitiesException.class)
    public void testErrorOnUnclosedObject() {
        String json = """
            {"version": "1.0", "drivers": [{
            """;
        PjdbcCapabilities.parse(json);
    }

    // ========== Edge cases ==========

    @Test
    public void testParseCompactJson() {
        String json = "{\"version\":\"1.0\",\"drivers\":[{\"name\":\"T\",\"prefix\":\"t\",\"class\":\"C\"}]}";
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("1.0", caps.getVersion());
        assertEquals(1, caps.getDriverCount());
    }

    @Test
    public void testParseDeeplyNestedStructure() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Deep",
                    "prefix": "deep",
                    "class": "org.example.Deep",
                    "parameters": [{
                        "name": "config",
                        "type": "object",
                        "default": null
                    }],
                    "dependencies": [{
                        "groupId": "a",
                        "artifactId": "b",
                        "version": "1.0",
                        "optional": false
                    }],
                    "sideEffects": {
                        "logging": true,
                        "metrics": true,
                        "network": false,
                        "filesystem": false,
                        "stateful": false
                    },
                    "capabilities": ["one", "two", "three"]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("deep");
        assertTrue(driver.isPresent());
        assertEquals(1, driver.get().parameters().size());
        assertEquals(1, driver.get().dependencies().size());
        assertEquals(3, driver.get().capabilities().size());
    }

    @Test
    public void testParseDriverWithDefaultValues() {
        // Driver with minimal fields - defaults should be applied
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Minimal",
                    "prefix": "min",
                    "class": "org.example.Minimal"
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("min");
        assertTrue(driver.isPresent());

        // Check defaults
        assertTrue(driver.get().composable()); // default true
        assertFalse(driver.get().terminal()); // default false
        assertTrue(driver.get().capabilities().isEmpty());
        assertTrue(driver.get().parameters().isEmpty());
        assertTrue(driver.get().dependencies().isEmpty());
    }

    @Test
    public void testParseStringWithSpecialCharacters() {
        String json = """
            {"version": "v1.0-beta+build.123", "drivers": []}
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        assertEquals("v1.0-beta+build.123", caps.getVersion());
    }

    @Test
    public void testParseParameterWithStringDefault() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "format",
                        "type": "string",
                        "default": "json"
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("format");
        assertTrue(param.isPresent());
        assertEquals("json", param.get().defaultValue());
    }

    @Test
    public void testParseParameterWithBooleanDefault() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "enabled",
                        "type": "boolean",
                        "default": true
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("enabled");
        assertTrue(param.isPresent());
        assertEquals(true, param.get().defaultValue());
    }

    @Test
    public void testParseTrailingCommaInArray() {
        // Parser should handle trailing commas gracefully
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "capabilities": ["a", "b",]
                }]
            }
            """;
        // This may or may not throw depending on strictness
        // Our simple parser happens to handle this
        try {
            PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
            // If it parses, verify the result
            assertTrue(caps.hasDriver("test"));
        } catch (PjdbcCapabilitiesException e) {
            // Also acceptable if parser rejects trailing comma
        }
    }

    @Test
    public void testParseLargeNumber() {
        String json = """
            {
                "version": "1.0",
                "drivers": [{
                    "name": "Test",
                    "prefix": "test",
                    "class": "org.example.Test",
                    "parameters": [{
                        "name": "bignum",
                        "type": "long",
                        "max": 9223372036854775807
                    }]
                }]
            }
            """;
        PjdbcCapabilities caps = PjdbcCapabilities.parse(json);
        Optional<DriverCapability> driver = caps.findByPrefix("test");
        assertTrue(driver.isPresent());

        Optional<DriverCapability.Parameter> param = driver.get().getParameter("bignum");
        assertTrue(param.isPresent());
        assertEquals(Long.MAX_VALUE, param.get().max().longValue());
    }
}
