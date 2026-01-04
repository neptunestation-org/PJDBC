package org.pjdbc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the capabilities and metadata of a PJDBC proxy driver.
 *
 * <p>This annotation is used to generate the pjdbc.capabilities.json manifest
 * at compile time, ensuring the manifest stays in sync with the actual driver
 * implementations.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DriverCapability(
 *     prefix = "cache",
 *     description = "Caches SELECT query results in memory",
 *     capabilities = {"caching"}
 * )
 * public class CachingDriver extends AbstractProxyDriver {
 *     // ...
 * }
 * }</pre>
 *
 * <p>The driver name is derived from the class name by default, but can be
 * overridden using the {@link #name()} attribute.</p>
 *
 * @see DriverParameter
 * @see DriverDependency
 * @see DriverSideEffects
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DriverCapability {

    /**
     * The URL prefix for this driver (e.g., "cache" for jdbc:cache:...).
     * This is required and must be unique across all drivers.
     *
     * @return the URL subprotocol prefix
     */
    String prefix();

    /**
     * A brief description of what this driver does.
     * This should be a single sentence suitable for display in documentation
     * and agent tooling.
     *
     * @return the driver description
     */
    String description();

    /**
     * The capability tags for this driver.
     * These are used for discovery and categorization.
     *
     * <p>Common capability tags include:</p>
     * <ul>
     *   <li>{@code caching} - Query result caching</li>
     *   <li>{@code pooling} - Connection pooling</li>
     *   <li>{@code logging} - SQL statement logging</li>
     *   <li>{@code tracing} - Distributed tracing</li>
     *   <li>{@code metrics} - Performance metrics</li>
     *   <li>{@code resilience} - Fault tolerance (retry, circuit breaker, etc.)</li>
     *   <li>{@code security} - Access control, masking, encryption</li>
     *   <li>{@code testing} - Test utilities (mock, chaos, sink)</li>
     *   <li>{@code transformation} - SQL modification</li>
     *   <li>{@code passthrough} - No modification to queries</li>
     * </ul>
     *
     * @return array of capability tags
     */
    String[] capabilities() default {};

    /**
     * Optional override for the driver name.
     * If not specified, the simple class name is used.
     *
     * @return the driver name, or empty string to use class name
     */
    String name() default "";

    /**
     * Whether this driver can be composed with other drivers in a chain.
     * Most drivers are composable. Set to false for drivers that must be
     * used standalone (e.g., MockDriver).
     *
     * @return true if the driver can be chained with others
     */
    boolean composable() default true;

    /**
     * Whether this driver is a terminal driver that doesn't delegate to
     * another JDBC driver. Terminal drivers (like SinkDriver, MockDriver)
     * don't require a nested JDBC URL.
     *
     * @return true if the driver doesn't delegate to another driver
     */
    boolean terminal() default false;
}
