package org.pjdbc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the side effects of a PJDBC proxy driver.
 *
 * <p>Side effects help AI agents and tooling understand what a driver does
 * beyond its primary function, enabling better decision-making about
 * driver selection and composition.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DriverCapability(prefix = "redis", description = "Redis-backed query caching")
 * @DriverSideEffects(network = true, stateful = true)
 * public class RedisCachingDriver extends AbstractProxyDriver {
 *     // ...
 * }
 * }</pre>
 *
 * @see DriverCapability
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DriverSideEffects {

    /**
     * Whether the driver maintains internal state across operations.
     * Stateful drivers (like caching, pooling, circuit breakers) retain
     * information between calls that affects behavior.
     *
     * @return true if the driver maintains state
     */
    boolean stateful() default false;

    /**
     * Whether the driver performs logging operations.
     * This includes writing to java.util.logging, SLF4J, or any other
     * logging framework.
     *
     * @return true if the driver logs
     */
    boolean logging() default false;

    /**
     * Whether the driver makes network calls beyond the database connection.
     * This includes connections to Redis, Memcached, Hazelcast, or external
     * tracing/metrics systems.
     *
     * @return true if the driver makes additional network calls
     */
    boolean network() default false;

    /**
     * Whether the driver accesses the filesystem.
     * This includes reading configuration files, writing logs to files,
     * or any other filesystem I/O.
     *
     * @return true if the driver accesses the filesystem
     */
    boolean filesystem() default false;

    /**
     * Whether the driver emits metrics.
     * This includes exposing JMX beans, publishing to Prometheus,
     * or any other metrics collection.
     *
     * @return true if the driver emits metrics
     */
    boolean metrics() default false;

    /**
     * Whether the driver performs tracing.
     * This includes creating spans for distributed tracing systems
     * like OpenTelemetry or Jaeger.
     *
     * @return true if the driver performs tracing
     */
    boolean tracing() default false;

    /**
     * Whether the driver can modify queries.
     * Drivers that transform SQL or inject additional clauses
     * should set this to true.
     *
     * @return true if the driver can modify queries
     */
    boolean modifiesQueries() default false;

    /**
     * Whether the driver can modify results.
     * Drivers that mask data, transform values, or filter rows
     * should set this to true.
     *
     * @return true if the driver can modify results
     */
    boolean modifiesResults() default false;
}
