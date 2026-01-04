package org.pjdbc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a URL parameter accepted by a PJDBC proxy driver.
 *
 * <p>This annotation is repeatable, allowing multiple parameters to be
 * declared on a single driver class.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DriverCapability(prefix = "cache", description = "Caches query results")
 * @DriverParameter(name = "ttl", type = ParameterType.INTEGER,
 *     description = "Time-to-live in seconds", defaultValue = "60", min = 1)
 * @DriverParameter(name = "maxSize", type = ParameterType.INTEGER,
 *     description = "Maximum cached entries", defaultValue = "1000", min = 1)
 * @DriverParameter(name = "enabled", type = ParameterType.BOOLEAN,
 *     description = "Enable caching", defaultValue = "true")
 * public class CachingDriver extends AbstractProxyDriver {
 *     // ...
 * }
 * }</pre>
 *
 * @see DriverCapability
 * @see DriverParameters
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(DriverParameters.class)
public @interface DriverParameter {

    /**
     * The parameter name as it appears in the JDBC URL.
     * For example, "ttl" for jdbc:cache[ttl=60]:...
     *
     * @return the parameter name
     */
    String name();

    /**
     * The parameter type.
     *
     * @return the parameter type
     */
    ParameterType type() default ParameterType.STRING;

    /**
     * A description of what this parameter controls.
     *
     * @return the parameter description
     */
    String description() default "";

    /**
     * The default value if not specified in the URL.
     * Use empty string for no default (required parameter).
     *
     * @return the default value as a string
     */
    String defaultValue() default "";

    /**
     * Minimum value for numeric parameters.
     * Use Long.MIN_VALUE to indicate no minimum.
     *
     * @return the minimum value
     */
    long min() default Long.MIN_VALUE;

    /**
     * Maximum value for numeric parameters.
     * Use Long.MAX_VALUE to indicate no maximum.
     *
     * @return the maximum value
     */
    long max() default Long.MAX_VALUE;

    /**
     * Valid values for enum-like string parameters.
     * Empty array means any value is allowed.
     *
     * @return array of valid values
     */
    String[] enumValues() default {};

    /**
     * Whether this parameter is required.
     * If true and no default is provided, the driver will fail
     * if the parameter is not specified.
     *
     * @return true if the parameter is required
     */
    boolean required() default false;

    /**
     * Parameter types supported by driver parameters.
     */
    enum ParameterType {
        /** String parameter */
        STRING,
        /** Integer parameter (whole numbers) */
        INTEGER,
        /** Floating-point parameter */
        FLOAT,
        /** Boolean parameter (true/false) */
        BOOLEAN
    }
}
