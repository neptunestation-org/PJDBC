package org.pjdbc.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;

@DisplayName("ParameterValidator")
class ParameterValidatorTest {

    // Test driver classes with various parameter configurations

    @DriverCapability(prefix = "test", description = "Test driver")
    @DriverParameter(name = "required", type = ParameterType.STRING, required = true)
    @DriverParameter(name = "optional", type = ParameterType.STRING)
    static class RequiredParamDriver {}

    @DriverCapability(prefix = "test", description = "Test driver")
    @DriverParameter(name = "count", type = ParameterType.INTEGER, min = 0, max = 100)
    @DriverParameter(name = "unbounded", type = ParameterType.INTEGER)
    static class IntegerParamDriver {}

    @DriverCapability(prefix = "test", description = "Test driver")
    @DriverParameter(name = "rate", type = ParameterType.FLOAT, min = 0, max = 1)
    static class FloatParamDriver {}

    @DriverCapability(prefix = "test", description = "Test driver")
    @DriverParameter(name = "enabled", type = ParameterType.BOOLEAN)
    static class BooleanParamDriver {}

    @DriverCapability(prefix = "test", description = "Test driver")
    @DriverParameter(name = "mode", type = ParameterType.STRING, enumValues = {"fast", "slow", "auto"})
    static class EnumParamDriver {}

    @DriverCapability(prefix = "test", description = "Test driver")
    static class NoParamDriver {}

    @Nested
    @DisplayName("Required Parameters")
    class RequiredParameterTests {

        @Test
        @DisplayName("passes when required parameter is present")
        void passesWhenRequiredParameterIsPresent() {
            Map<String, String> params = new HashMap<>();
            params.put("required", "value");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(RequiredParamDriver.class, params));
        }

        @Test
        @DisplayName("fails when required parameter is missing")
        void failsWhenRequiredParameterIsMissing() {
            Map<String, String> params = new HashMap<>();

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(RequiredParamDriver.class, params));

            assertTrue(ex.getMessage().contains("required"));
            assertTrue(ex.getMessage().contains("missing"));
        }

        @Test
        @DisplayName("fails when required parameter is empty")
        void failsWhenRequiredParameterIsEmpty() {
            Map<String, String> params = new HashMap<>();
            params.put("required", "");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(RequiredParamDriver.class, params));

            assertTrue(ex.getMessage().contains("required"));
        }

        @Test
        @DisplayName("passes when optional parameter is missing")
        void passesWhenOptionalParameterIsMissing() {
            Map<String, String> params = new HashMap<>();
            params.put("required", "value");
            // "optional" is not provided

            assertDoesNotThrow(() ->
                ParameterValidator.validate(RequiredParamDriver.class, params));
        }
    }

    @Nested
    @DisplayName("Integer Parameters")
    class IntegerParameterTests {

        @Test
        @DisplayName("passes for valid integer within bounds")
        void passesForValidIntegerWithinBounds() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "50");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(IntegerParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for integer at minimum bound")
        void passesForIntegerAtMinimumBound() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "0");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(IntegerParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for integer at maximum bound")
        void passesForIntegerAtMaximumBound() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "100");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(IntegerParamDriver.class, params));
        }

        @Test
        @DisplayName("fails for integer below minimum")
        void failsForIntegerBelowMinimum() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "-1");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(IntegerParamDriver.class, params));

            assertTrue(ex.getMessage().contains("count"));
            assertTrue(ex.getMessage().contains("less than minimum"));
        }

        @Test
        @DisplayName("fails for integer above maximum")
        void failsForIntegerAboveMaximum() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "101");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(IntegerParamDriver.class, params));

            assertTrue(ex.getMessage().contains("count"));
            assertTrue(ex.getMessage().contains("greater than maximum"));
        }

        @Test
        @DisplayName("fails for non-integer value")
        void failsForNonIntegerValue() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "abc");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(IntegerParamDriver.class, params));

            assertTrue(ex.getMessage().contains("count"));
            assertTrue(ex.getMessage().contains("not a valid integer"));
        }

        @Test
        @DisplayName("fails for float value in integer parameter")
        void failsForFloatValueInIntegerParameter() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "3.14");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(IntegerParamDriver.class, params));

            assertTrue(ex.getMessage().contains("not a valid integer"));
        }

        @Test
        @DisplayName("passes for unbounded integer with any value")
        void passesForUnboundedIntegerWithAnyValue() {
            Map<String, String> params = new HashMap<>();
            params.put("unbounded", "999999999");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(IntegerParamDriver.class, params));
        }
    }

    @Nested
    @DisplayName("Float Parameters")
    class FloatParameterTests {

        @Test
        @DisplayName("passes for valid float within bounds")
        void passesForValidFloatWithinBounds() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "0.5");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(FloatParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for float at minimum bound")
        void passesForFloatAtMinimumBound() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "0");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(FloatParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for float at maximum bound")
        void passesForFloatAtMaximumBound() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "1");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(FloatParamDriver.class, params));
        }

        @Test
        @DisplayName("fails for float below minimum")
        void failsForFloatBelowMinimum() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "-0.1");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(FloatParamDriver.class, params));

            assertTrue(ex.getMessage().contains("rate"));
            assertTrue(ex.getMessage().contains("less than minimum"));
        }

        @Test
        @DisplayName("fails for float above maximum")
        void failsForFloatAboveMaximum() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "1.5");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(FloatParamDriver.class, params));

            assertTrue(ex.getMessage().contains("rate"));
            assertTrue(ex.getMessage().contains("greater than maximum"));
        }

        @Test
        @DisplayName("fails for non-numeric value")
        void failsForNonNumericValue() {
            Map<String, String> params = new HashMap<>();
            params.put("rate", "fast");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(FloatParamDriver.class, params));

            assertTrue(ex.getMessage().contains("rate"));
            assertTrue(ex.getMessage().contains("not a valid number"));
        }
    }

    @Nested
    @DisplayName("Boolean Parameters")
    class BooleanParameterTests {

        @Test
        @DisplayName("passes for 'true'")
        void passesForTrue() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "true");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(BooleanParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for 'false'")
        void passesForFalse() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "false");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(BooleanParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for 'TRUE' (case insensitive)")
        void passesForTrueUppercase() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "TRUE");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(BooleanParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for 'False' (case insensitive)")
        void passesForFalseMixedCase() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "False");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(BooleanParamDriver.class, params));
        }

        @Test
        @DisplayName("fails for invalid boolean value")
        void failsForInvalidBooleanValue() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "yes");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(BooleanParamDriver.class, params));

            assertTrue(ex.getMessage().contains("enabled"));
            assertTrue(ex.getMessage().contains("not a valid boolean"));
        }

        @Test
        @DisplayName("fails for numeric boolean value")
        void failsForNumericBooleanValue() {
            Map<String, String> params = new HashMap<>();
            params.put("enabled", "1");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(BooleanParamDriver.class, params));

            assertTrue(ex.getMessage().contains("not a valid boolean"));
        }
    }

    @Nested
    @DisplayName("Enum Parameters")
    class EnumParameterTests {

        @Test
        @DisplayName("passes for valid enum value")
        void passesForValidEnumValue() {
            Map<String, String> params = new HashMap<>();
            params.put("mode", "fast");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(EnumParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for all valid enum values")
        void passesForAllValidEnumValues() {
            for (String mode : new String[]{"fast", "slow", "auto"}) {
                Map<String, String> params = new HashMap<>();
                params.put("mode", mode);

                assertDoesNotThrow(() ->
                    ParameterValidator.validate(EnumParamDriver.class, params),
                    "Should accept enum value: " + mode);
            }
        }

        @Test
        @DisplayName("fails for invalid enum value")
        void failsForInvalidEnumValue() {
            Map<String, String> params = new HashMap<>();
            params.put("mode", "medium");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(EnumParamDriver.class, params));

            assertTrue(ex.getMessage().contains("mode"));
            assertTrue(ex.getMessage().contains("medium"));
            assertTrue(ex.getMessage().contains("allowed values"));
        }

        @Test
        @DisplayName("enum validation is case sensitive")
        void enumValidationIsCaseSensitive() {
            Map<String, String> params = new HashMap<>();
            params.put("mode", "FAST");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(EnumParamDriver.class, params));

            assertTrue(ex.getMessage().contains("FAST"));
            assertTrue(ex.getMessage().contains("not valid"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("passes for driver with no parameters")
        void passesForDriverWithNoParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("unknown", "value");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(NoParamDriver.class, params));
        }

        @Test
        @DisplayName("passes for empty parameters map")
        void passesForEmptyParametersMap() {
            Map<String, String> params = new HashMap<>();

            assertDoesNotThrow(() ->
                ParameterValidator.validate(NoParamDriver.class, params));
        }

        @Test
        @DisplayName("ignores unknown parameters")
        void ignoresUnknownParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("count", "50");
            params.put("unknown", "ignored");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(IntegerParamDriver.class, params));
        }

        @Test
        @DisplayName("collects multiple validation errors")
        void collectsMultipleValidationErrors() {
            @DriverCapability(prefix = "test", description = "Test driver")
            @DriverParameter(name = "a", type = ParameterType.INTEGER, min = 0)
            @DriverParameter(name = "b", type = ParameterType.INTEGER, min = 0)
            class MultiParamDriver {}

            Map<String, String> params = new HashMap<>();
            params.put("a", "-1");
            params.put("b", "-2");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(MultiParamDriver.class, params));

            // Should report both errors
            assertTrue(ex.getMessage().contains("a") || ex.getMessage().contains("b"));
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("validateSingle returns valid for correct value")
        void validateSingleReturnsValidForCorrectValue() {
            ParameterValidator.ValidationResult result =
                ParameterValidator.validateSingle(IntegerParamDriver.class, "count", "50");

            assertTrue(result.isValid());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("validateSingle returns invalid for incorrect value")
        void validateSingleReturnsInvalidForIncorrectValue() {
            ParameterValidator.ValidationResult result =
                ParameterValidator.validateSingle(IntegerParamDriver.class, "count", "abc");

            assertFalse(result.isValid());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("not a valid integer"));
        }

        @Test
        @DisplayName("validateSingle returns valid for unknown parameter")
        void validateSingleReturnsValidForUnknownParameter() {
            ParameterValidator.ValidationResult result =
                ParameterValidator.validateSingle(IntegerParamDriver.class, "unknown", "anything");

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("Real Driver Validation")
    class RealDriverValidationTests {

        @Test
        @DisplayName("validates RetryDriver parameters")
        void validatesRetryDriverParameters() throws Exception {
            Class<?> retryDriver = Class.forName("org.pjdbc.drivers.RetryDriver");

            // Valid parameters
            Map<String, String> validParams = new HashMap<>();
            validParams.put("maxRetries", "5");
            validParams.put("initialDelay", "100");
            validParams.put("jitter", "true");

            assertDoesNotThrow(() ->
                ParameterValidator.validate(retryDriver, validParams));
        }

        @Test
        @DisplayName("rejects invalid RetryDriver maxRetries")
        void rejectsInvalidRetryDriverMaxRetries() throws Exception {
            Class<?> retryDriver = Class.forName("org.pjdbc.drivers.RetryDriver");

            Map<String, String> invalidParams = new HashMap<>();
            invalidParams.put("maxRetries", "-1");

            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(retryDriver, invalidParams));

            assertTrue(ex.getMessage().contains("maxRetries"));
        }

        @Test
        @DisplayName("validates RateLimitDriver mode enum")
        void validatesRateLimitDriverModeEnum() throws Exception {
            Class<?> rateLimitDriver = Class.forName("org.pjdbc.drivers.RateLimitDriver");

            // Valid mode
            Map<String, String> validParams = new HashMap<>();
            validParams.put("mode", "reject");
            assertDoesNotThrow(() ->
                ParameterValidator.validate(rateLimitDriver, validParams));

            // Invalid mode
            Map<String, String> invalidParams = new HashMap<>();
            invalidParams.put("mode", "invalid");
            SQLException ex = assertThrows(SQLException.class, () ->
                ParameterValidator.validate(rateLimitDriver, invalidParams));

            assertTrue(ex.getMessage().contains("mode"));
            assertTrue(ex.getMessage().contains("allowed values"));
        }
    }
}
