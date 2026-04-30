package org.pjdbc.conformance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.pjdbc.capabilities.DriverCapability;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for parameter defaults across all PJDBC drivers.
 *
 * <p>Verifies that all drivers:
 * <ul>
 *   <li>Document default values for optional parameters</li>
 *   <li>Have valid min/max ranges where specified</li>
 *   <li>Have consistent parameter type declarations</li>
 *   <li>Enum parameters list valid options</li>
 * </ul>
 */
@DisplayName("Parameter Default Conformance")
public class ParameterDefaultConformanceTest extends DriverConformanceTest {

    @Test
    @DisplayName("All optional non-string parameters have default values")
    void optionalParametersHaveDefaults() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                // String parameters may legitimately have no default (user-provided values)
                if (!param.required() && !"string".equals(param.type())) {
                    assertNotNull(param.defaultValue(),
                        "Optional non-string parameter '" + param.name() + "' in " + driver.name() +
                        " should have a default value");
                }
            }
        }
    }

    @Test
    @DisplayName("Numeric parameters have valid min/max ranges")
    void numericParametersHaveValidRanges() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                if (!"integer".equals(param.type()) && !"float".equals(param.type())) {
                    continue;
                }

                if (param.min() != null && param.max() != null) {
                    assertTrue(param.min().doubleValue() <= param.max().doubleValue(),
                        "Parameter '" + param.name() + "' in " + driver.name() +
                        " has min > max: " + param.min() + " > " + param.max());
                }

                if (param.defaultValue() != null && param.defaultValue() instanceof Number) {
                    double defaultVal = ((Number) param.defaultValue()).doubleValue();

                    if (param.min() != null) {
                        assertTrue(defaultVal >= param.min().doubleValue(),
                            "Default value for '" + param.name() + "' in " + driver.name() +
                            " is below min: " + defaultVal + " < " + param.min());
                    }

                    if (param.max() != null) {
                        assertTrue(defaultVal <= param.max().doubleValue(),
                            "Default value for '" + param.name() + "' in " + driver.name() +
                            " is above max: " + defaultVal + " > " + param.max());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Default values match declared type")
    void defaultValuesMatchDeclaredType() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                if (param.defaultValue() == null) continue;

                boolean typeMatch = switch (param.type()) {
                    case "integer" -> param.defaultValue() instanceof Number;
                    case "float" -> param.defaultValue() instanceof Number;
                    case "boolean" -> param.defaultValue() instanceof Boolean;
                    case "string" -> param.defaultValue() instanceof String;
                    case "duration" -> param.defaultValue() instanceof Number;
                    default -> true;
                };

                assertTrue(typeMatch,
                    "Default value type mismatch for '" + param.name() + "' in " + driver.name() +
                    ": expected " + param.type() + " but got " + param.defaultValue().getClass().getSimpleName());
            }
        }
    }

    @Test
    @DisplayName("Enum parameters have at least two options")
    void enumParametersHaveOptions() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                if (param.enumValues() != null) {
                    assertTrue(param.enumValues().size() >= 2,
                        "Enum parameter '" + param.name() + "' in " + driver.name() +
                        " should have at least 2 options");

                    // Default should be one of the enum values
                    if (param.defaultValue() != null) {
                        assertTrue(param.enumValues().contains(param.defaultValue().toString()),
                            "Default value for enum parameter '" + param.name() + "' in " + driver.name() +
                            " should be one of: " + param.enumValues());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Parameter names are valid identifiers or wildcard patterns")
    void parameterNamesAreValidIdentifiers() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                assertNotNull(param.name(), "Parameter name should not be null in " + driver.name());
                assertFalse(param.name().isEmpty(), "Parameter name should not be empty in " + driver.name());
                // Support both normal identifiers and wildcard patterns like rename.*
                assertTrue(param.name().matches("[a-zA-Z][a-zA-Z0-9_]*(\\.\\*)?"),
                    "Parameter name '" + param.name() + "' in " + driver.name() +
                    " should be a valid identifier or wildcard pattern (e.g., 'param' or 'param.*')");
            }
        }
    }

    @Test
    @DisplayName("Boolean parameters default to true or false")
    void booleanParametersHaveBooleanDefaults() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                if (!"boolean".equals(param.type())) continue;

                if (param.defaultValue() != null) {
                    assertTrue(param.defaultValue() instanceof Boolean,
                        "Boolean parameter '" + param.name() + "' in " + driver.name() +
                        " should have Boolean default, got: " + param.defaultValue().getClass());
                }
            }
        }
    }

    @Test
    @DisplayName("All parameters have descriptions")
    void allParametersHaveDescriptions() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            for (DriverCapability.Parameter param : driver.parameters()) {
                assertNotNull(param.description(),
                    "Parameter '" + param.name() + "' in " + driver.name() + " should have a description");
                assertFalse(param.description().isEmpty(),
                    "Parameter '" + param.name() + "' in " + driver.name() + " should have non-empty description");
            }
        }
    }

    @Test
    @DisplayName("No duplicate parameter names within a driver")
    void noDuplicateParameterNames() {
        for (DriverCapability driver : capabilities.getAllDrivers()) {
            if (driver.parameters() == null) continue;

            var names = driver.parameters().stream()
                .map(DriverCapability.Parameter::name)
                .toList();

            var uniqueNames = names.stream().distinct().toList();

            assertEquals(names.size(), uniqueNames.size(),
                "Driver " + driver.name() + " has duplicate parameter names");
        }
    }
}
