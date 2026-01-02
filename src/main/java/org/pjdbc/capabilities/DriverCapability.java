package org.pjdbc.capabilities;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the capabilities of a PJDBC driver.
 *
 * @param name Human-readable driver name
 * @param prefix JDBC URL prefix (e.g., "pool", "log", "cache")
 * @param driverClass Fully qualified Java class name
 * @param description Brief description of driver purpose
 * @param capabilities List of capability tags
 * @param parameters Configurable URL parameters
 * @param dependencies Optional external dependencies
 * @param sideEffects Side effects this driver may produce
 * @param composable Whether driver can be chained with others
 * @param terminal Whether driver terminates the chain (no delegate)
 */
public record DriverCapability(
    String name,
    String prefix,
    String driverClass,
    String description,
    List<String> capabilities,
    List<Parameter> parameters,
    List<Dependency> dependencies,
    SideEffects sideEffects,
    boolean composable,
    boolean terminal
) {
    /**
     * Checks if this driver has the specified capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Gets a parameter by name.
     */
    public Optional<Parameter> getParameter(String name) {
        if (parameters == null) return Optional.empty();
        return parameters.stream()
            .filter(p -> p.name().equals(name))
            .findFirst();
    }

    /**
     * Returns the full JDBC URL prefix for this driver.
     */
    public String getUrlPrefix() {
        return "jdbc:" + prefix + ":";
    }

    /**
     * Represents a configurable driver parameter.
     */
    public record Parameter(
        String name,
        String type,
        String description,
        Object defaultValue,
        boolean required,
        Number min,
        Number max,
        List<String> enumValues
    ) {}

    /**
     * Represents an external dependency.
     */
    public record Dependency(
        String groupId,
        String artifactId,
        String version,
        boolean optional
    ) {
        /**
         * Returns Maven coordinates string.
         */
        public String toMavenCoordinates() {
            return groupId + ":" + artifactId + (version != null ? ":" + version : "");
        }
    }

    /**
     * Represents side effects a driver may produce.
     */
    public record SideEffects(
        boolean logging,
        boolean metrics,
        boolean network,
        boolean filesystem,
        boolean stateful
    ) {
        public static final SideEffects NONE = new SideEffects(false, false, false, false, false);
    }
}
