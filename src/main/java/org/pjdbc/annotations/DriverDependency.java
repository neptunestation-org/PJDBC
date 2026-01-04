package org.pjdbc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an external dependency required by a PJDBC proxy driver.
 *
 * <p>This annotation is repeatable, allowing multiple dependencies to be
 * declared on a single driver class. Dependencies are typically optional
 * Maven artifacts that must be present at runtime for the driver to function.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DriverCapability(prefix = "hikaricp", description = "HikariCP connection pooling")
 * @DriverDependency(
 *     groupId = "com.zaxxer",
 *     artifactId = "HikariCP",
 *     version = "5.1.0"
 * )
 * public class HikariPoolDriver extends AbstractProxyDriver {
 *     // ...
 * }
 * }</pre>
 *
 * @see DriverCapability
 * @see DriverDependencies
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(DriverDependencies.class)
public @interface DriverDependency {

    /**
     * The Maven group ID of the dependency.
     *
     * @return the group ID (e.g., "com.zaxxer")
     */
    String groupId();

    /**
     * The Maven artifact ID of the dependency.
     *
     * @return the artifact ID (e.g., "HikariCP")
     */
    String artifactId();

    /**
     * The recommended version of the dependency.
     * This is informational and may not reflect the exact version used.
     *
     * @return the version (e.g., "5.1.0")
     */
    String version() default "";

    /**
     * Whether this dependency is optional.
     * Optional dependencies are not required at runtime but enable
     * additional functionality when present.
     *
     * @return true if the dependency is optional
     */
    boolean optional() default true;

    /**
     * A description of why this dependency is needed.
     *
     * @return the dependency description
     */
    String description() default "";
}
