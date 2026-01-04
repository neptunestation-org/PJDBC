package org.pjdbc.validation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.pjdbc.annotations.DriverParameter;

/**
 * Validates URL parameters against {@link DriverParameter} annotations at runtime.
 *
 * <p>This class provides runtime validation of driver parameters using the same
 * constraints defined in the annotations. It validates:
 * <ul>
 *   <li>Required parameters are present</li>
 *   <li>Parameter values match the declared type (INTEGER, FLOAT, BOOLEAN, STRING)</li>
 *   <li>Numeric values are within min/max bounds</li>
 *   <li>String values are in the declared enum values (if specified)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Map<String, String> params = getUrlParameters(url);
 * ParameterValidator.validate(MyDriver.class, params);
 * }</pre>
 *
 * @see DriverParameter
 */
public final class ParameterValidator {

    private ParameterValidator() {
        // Utility class
    }

    /**
     * Validates parameters against the annotations on the given driver class.
     *
     * @param driverClass the driver class with @DriverParameter annotations
     * @param parameters the URL parameters to validate
     * @throws SQLException if validation fails
     */
    public static void validate(Class<?> driverClass, Map<String, String> parameters) throws SQLException {
        DriverParameter[] annotations = driverClass.getAnnotationsByType(DriverParameter.class);
        if (annotations.length == 0) {
            return; // No parameters declared, nothing to validate
        }

        List<String> errors = new ArrayList<>();

        for (DriverParameter param : annotations) {
            String value = parameters.get(param.name());
            validateParameter(param, value, errors);
        }

        if (!errors.isEmpty()) {
            throw new SQLException("Parameter validation failed: " + String.join("; ", errors));
        }
    }

    /**
     * Validates a single parameter value against its annotation constraints.
     *
     * @param param the parameter annotation
     * @param value the parameter value (may be null if not provided)
     * @param errors list to collect error messages
     */
    private static void validateParameter(DriverParameter param, String value, List<String> errors) {
        String name = param.name();

        // Check required
        if (value == null || value.isEmpty()) {
            if (param.required()) {
                errors.add("Required parameter '" + name + "' is missing");
            }
            return; // No value to validate further
        }

        // Validate by type
        switch (param.type()) {
            case INTEGER:
                validateInteger(name, value, param, errors);
                break;
            case FLOAT:
                validateFloat(name, value, param, errors);
                break;
            case BOOLEAN:
                validateBoolean(name, value, errors);
                break;
            case STRING:
                validateString(name, value, param, errors);
                break;
        }
    }

    private static void validateInteger(String name, String value, DriverParameter param, List<String> errors) {
        try {
            long longValue = Long.parseLong(value);

            if (param.min() != Long.MIN_VALUE && longValue < param.min()) {
                errors.add("Parameter '" + name + "' value " + longValue +
                    " is less than minimum " + param.min());
            }

            if (param.max() != Long.MAX_VALUE && longValue > param.max()) {
                errors.add("Parameter '" + name + "' value " + longValue +
                    " is greater than maximum " + param.max());
            }
        } catch (NumberFormatException e) {
            errors.add("Parameter '" + name + "' value '" + value + "' is not a valid integer");
        }
    }

    private static void validateFloat(String name, String value, DriverParameter param, List<String> errors) {
        try {
            double doubleValue = Double.parseDouble(value);

            if (param.min() != Long.MIN_VALUE && doubleValue < param.min()) {
                errors.add("Parameter '" + name + "' value " + doubleValue +
                    " is less than minimum " + param.min());
            }

            if (param.max() != Long.MAX_VALUE && doubleValue > param.max()) {
                errors.add("Parameter '" + name + "' value " + doubleValue +
                    " is greater than maximum " + param.max());
            }
        } catch (NumberFormatException e) {
            errors.add("Parameter '" + name + "' value '" + value + "' is not a valid number");
        }
    }

    private static void validateBoolean(String name, String value, List<String> errors) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            errors.add("Parameter '" + name + "' value '" + value +
                "' is not a valid boolean (expected 'true' or 'false')");
        }
    }

    private static void validateString(String name, String value, DriverParameter param, List<String> errors) {
        String[] enumValues = param.enumValues();
        if (enumValues.length > 0) {
            List<String> allowed = Arrays.asList(enumValues);
            if (!allowed.contains(value)) {
                errors.add("Parameter '" + name + "' value '" + value +
                    "' is not valid; allowed values: " + allowed);
            }
        }
    }

    /**
     * Validates a single parameter value and returns the validation result.
     *
     * @param driverClass the driver class with @DriverParameter annotations
     * @param paramName the parameter name
     * @param value the parameter value
     * @return validation result
     */
    public static ValidationResult validateSingle(Class<?> driverClass, String paramName, String value) {
        DriverParameter[] annotations = driverClass.getAnnotationsByType(DriverParameter.class);

        for (DriverParameter param : annotations) {
            if (param.name().equals(paramName)) {
                List<String> errors = new ArrayList<>();
                validateParameter(param, value, errors);
                if (errors.isEmpty()) {
                    return ValidationResult.valid();
                } else {
                    return ValidationResult.invalid(errors.get(0));
                }
            }
        }

        return ValidationResult.valid(); // Unknown parameter, let it pass
    }

    /**
     * Result of parameter validation.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }
}
