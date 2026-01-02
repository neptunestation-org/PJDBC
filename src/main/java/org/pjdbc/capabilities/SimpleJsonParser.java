package org.pjdbc.capabilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSON parser for the capabilities manifest.
 * Avoids external dependencies by implementing minimal JSON parsing.
 */
class SimpleJsonParser {
    private final String json;
    private int pos = 0;

    SimpleJsonParser(String json) {
        this.json = json;
    }

    PjdbcCapabilities parseCapabilities() {
        skipWhitespace();
        expect('{');

        String version = "1.0";
        List<DriverCapability> drivers = new ArrayList<>();

        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                break;
            }

            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            if ("version".equals(key)) {
                version = parseString();
            } else if ("drivers".equals(key)) {
                drivers = parseDriverArray();
            } else {
                skipValue();
            }

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        PjdbcCapabilities.Builder builder = PjdbcCapabilities.builder().version(version);
        for (DriverCapability driver : drivers) {
            builder.addDriver(driver);
        }
        return builder.build();
    }

    private List<DriverCapability> parseDriverArray() {
        List<DriverCapability> drivers = new ArrayList<>();
        expect('[');

        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                break;
            }

            drivers.add(parseDriver());

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return drivers;
    }

    private DriverCapability parseDriver() {
        skipWhitespace();
        expect('{');

        String name = null;
        String prefix = null;
        String driverClass = null;
        String description = null;
        List<String> capabilities = new ArrayList<>();
        List<DriverCapability.Parameter> parameters = new ArrayList<>();
        List<DriverCapability.Dependency> dependencies = new ArrayList<>();
        DriverCapability.SideEffects sideEffects = DriverCapability.SideEffects.NONE;
        boolean composable = true;
        boolean terminal = false;

        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                break;
            }

            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            switch (key) {
                case "name" -> name = parseString();
                case "prefix" -> prefix = parseString();
                case "class" -> driverClass = parseString();
                case "description" -> description = parseString();
                case "capabilities" -> capabilities = parseStringArray();
                case "parameters" -> parameters = parseParameterArray();
                case "dependencies" -> dependencies = parseDependencyArray();
                case "sideEffects" -> sideEffects = parseSideEffects();
                case "composable" -> composable = parseBoolean();
                case "terminal" -> terminal = parseBoolean();
                default -> skipValue();
            }

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return new DriverCapability(
            name, prefix, driverClass, description, capabilities,
            parameters, dependencies, sideEffects, composable, terminal
        );
    }

    private List<DriverCapability.Parameter> parseParameterArray() {
        List<DriverCapability.Parameter> params = new ArrayList<>();
        expect('[');

        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                break;
            }

            params.add(parseParameter());

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return params;
    }

    private DriverCapability.Parameter parseParameter() {
        skipWhitespace();
        expect('{');

        String name = null;
        String type = null;
        String description = null;
        Object defaultValue = null;
        boolean required = false;
        Number min = null;
        Number max = null;
        List<String> enumValues = null;

        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                break;
            }

            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            switch (key) {
                case "name" -> name = parseString();
                case "type" -> type = parseString();
                case "description" -> description = parseString();
                case "default" -> defaultValue = parseValue();
                case "required" -> required = parseBoolean();
                case "min" -> min = parseNumber();
                case "max" -> max = parseNumber();
                case "enum" -> enumValues = parseStringArray();
                default -> skipValue();
            }

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return new DriverCapability.Parameter(
            name, type, description, defaultValue, required, min, max, enumValues
        );
    }

    private List<DriverCapability.Dependency> parseDependencyArray() {
        List<DriverCapability.Dependency> deps = new ArrayList<>();
        expect('[');

        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                break;
            }

            deps.add(parseDependency());

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return deps;
    }

    private DriverCapability.Dependency parseDependency() {
        skipWhitespace();
        expect('{');

        String groupId = null;
        String artifactId = null;
        String version = null;
        boolean optional = true;

        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                break;
            }

            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            switch (key) {
                case "groupId" -> groupId = parseString();
                case "artifactId" -> artifactId = parseString();
                case "version" -> version = parseString();
                case "optional" -> optional = parseBoolean();
                default -> skipValue();
            }

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return new DriverCapability.Dependency(groupId, artifactId, version, optional);
    }

    private DriverCapability.SideEffects parseSideEffects() {
        skipWhitespace();
        expect('{');

        boolean logging = false;
        boolean metrics = false;
        boolean network = false;
        boolean filesystem = false;
        boolean stateful = false;

        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                break;
            }

            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            switch (key) {
                case "logging" -> logging = parseBoolean();
                case "metrics" -> metrics = parseBoolean();
                case "network" -> network = parseBoolean();
                case "filesystem" -> filesystem = parseBoolean();
                case "stateful" -> stateful = parseBoolean();
                default -> skipValue();
            }

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return new DriverCapability.SideEffects(logging, metrics, network, filesystem, stateful);
    }

    private List<String> parseStringArray() {
        List<String> items = new ArrayList<>();
        expect('[');

        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                break;
            }

            items.add(parseString());

            skipWhitespace();
            if (peek() == ',') {
                pos++;
            }
        }

        return items;
    }

    private String parseString() {
        skipWhitespace();
        expect('"');

        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\' && pos < json.length()) {
                char escaped = json.charAt(pos++);
                switch (escaped) {
                    case '"', '\\', '/' -> sb.append(escaped);
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
        }
        throw new PjdbcCapabilitiesException("Unterminated string at position " + pos);
    }

    private boolean parseBoolean() {
        skipWhitespace();
        if (json.regionMatches(pos, "true", 0, 4)) {
            pos += 4;
            return true;
        } else if (json.regionMatches(pos, "false", 0, 5)) {
            pos += 5;
            return false;
        }
        throw new PjdbcCapabilitiesException("Expected boolean at position " + pos);
    }

    private Number parseNumber() {
        skipWhitespace();
        int start = pos;
        boolean isFloat = false;

        if (peek() == '-') pos++;

        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '.') {
                isFloat = true;
                pos++;
            } else if (Character.isDigit(c)) {
                pos++;
            } else {
                break;
            }
        }

        String numStr = json.substring(start, pos);
        return isFloat ? Double.parseDouble(numStr) : Long.parseLong(numStr);
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();

        if (c == '"') {
            return parseString();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            if (json.regionMatches(pos, "null", 0, 4)) {
                pos += 4;
                return null;
            }
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumber();
        } else if (c == '[') {
            return parseStringArray();
        } else if (c == '{') {
            skipObject();
            return null;
        }

        throw new PjdbcCapabilitiesException("Unexpected character at position " + pos + ": " + c);
    }

    private void skipValue() {
        skipWhitespace();
        char c = peek();

        if (c == '"') {
            parseString();
        } else if (c == 't') {
            pos += 4; // true
        } else if (c == 'f') {
            pos += 5; // false
        } else if (c == 'n') {
            pos += 4; // null
        } else if (c == '-' || Character.isDigit(c)) {
            parseNumber();
        } else if (c == '[') {
            skipArray();
        } else if (c == '{') {
            skipObject();
        }
    }

    private void skipArray() {
        expect('[');
        int depth = 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos++);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '"') {
                // Skip string content
                while (pos < json.length()) {
                    c = json.charAt(pos++);
                    if (c == '"') break;
                    if (c == '\\') pos++;
                }
            }
        }
    }

    private void skipObject() {
        expect('{');
        int depth = 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '"') {
                // Skip string content
                while (pos < json.length()) {
                    c = json.charAt(pos++);
                    if (c == '"') break;
                    if (c == '\\') pos++;
                }
            }
        }
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= json.length()) {
            throw new PjdbcCapabilitiesException("Unexpected end of JSON");
        }
        return json.charAt(pos);
    }

    private void expect(char expected) {
        skipWhitespace();
        if (pos >= json.length() || json.charAt(pos) != expected) {
            throw new PjdbcCapabilitiesException(
                "Expected '" + expected + "' at position " + pos +
                (pos < json.length() ? " but found '" + json.charAt(pos) + "'" : " but reached end"));
        }
        pos++;
    }
}
