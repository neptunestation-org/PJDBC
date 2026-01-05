package org.pjdbc.capabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.pjdbc.annotations.DriverDependency;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverSideEffects;

/**
 * Runtime introspection API for PJDBC driver capabilities.
 *
 * <p>This class provides access to the capabilities manifest that describes
 * all available PJDBC drivers, their parameters, dependencies, and side effects.
 *
 * <p>Example usage:
 * <pre>{@code
 * PjdbcCapabilities caps = PjdbcCapabilities.load();
 *
 * // Find all caching drivers
 * List<DriverCapability> cachingDrivers = caps.findByCapability("caching");
 *
 * // Get a specific driver by prefix
 * Optional<DriverCapability> pool = caps.findByPrefix("pool");
 *
 * // Check if a driver has optional dependencies
 * pool.ifPresent(d -> {
 *     if (!d.dependencies().isEmpty()) {
 *         System.out.println("Requires: " + d.dependencies());
 *     }
 * });
 * }</pre>
 */
public final class PjdbcCapabilities {

    private static final String MANIFEST_PATH = "pjdbc.capabilities.json";
    private static volatile PjdbcCapabilities instance;

    /**
     * Source from which capabilities were loaded.
     */
    public enum Source {
        /** Loaded from pjdbc.capabilities.json manifest file */
        MANIFEST,
        /** Loaded via runtime reflection on annotated classes */
        REFLECTION
    }

    private final String version;
    private final List<DriverCapability> drivers;
    private final Source source;

    private PjdbcCapabilities(String version, List<DriverCapability> drivers, Source source) {
        this.version = version;
        this.drivers = Collections.unmodifiableList(new ArrayList<>(drivers));
        this.source = source;
    }

    /**
     * Loads capabilities, trying manifest first then falling back to reflection.
     *
     * <p>This method first attempts to load from the pjdbc.capabilities.json
     * manifest. If the manifest is not found, it falls back to discovering
     * drivers via runtime reflection on annotated classes.</p>
     *
     * @return The loaded capabilities
     * @throws PjdbcCapabilitiesException if capabilities cannot be loaded from either source
     */
    public static PjdbcCapabilities load() {
        if (instance == null) {
            synchronized (PjdbcCapabilities.class) {
                if (instance == null) {
                    instance = loadWithFallback();
                }
            }
        }
        return instance;
    }

    /**
     * Forces loading from the manifest only.
     *
     * @return The loaded capabilities
     * @throws PjdbcCapabilitiesException if the manifest cannot be loaded or parsed
     */
    public static PjdbcCapabilities loadFromManifest() {
        synchronized (PjdbcCapabilities.class) {
            instance = loadFromClasspath();
        }
        return instance;
    }

    /**
     * Forces loading via runtime reflection on annotated driver classes.
     *
     * <p>This method scans known driver classes for @DriverCapability annotations
     * and builds the capabilities model from those annotations. Useful during
     * development when the manifest may not be up to date.</p>
     *
     * @return The loaded capabilities
     */
    public static PjdbcCapabilities loadFromReflection() {
        synchronized (PjdbcCapabilities.class) {
            instance = buildFromReflection();
        }
        return instance;
    }

    /**
     * Forces a reload of capabilities, trying manifest first then reflection.
     * Useful for testing or when the manifest may have changed.
     *
     * @return The reloaded capabilities
     */
    public static PjdbcCapabilities reload() {
        synchronized (PjdbcCapabilities.class) {
            instance = loadWithFallback();
        }
        return instance;
    }

    private static PjdbcCapabilities loadWithFallback() {
        try {
            return loadFromClasspath();
        } catch (PjdbcCapabilitiesException e) {
            // Manifest not found or invalid, fall back to reflection
            return buildFromReflection();
        }
    }

    private static PjdbcCapabilities loadFromClasspath() {
        try (InputStream is = PjdbcCapabilities.class.getClassLoader()
                .getResourceAsStream(MANIFEST_PATH)) {
            if (is == null) {
                throw new PjdbcCapabilitiesException(
                    "Capabilities manifest not found: " + MANIFEST_PATH);
            }
            String json;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                json = reader.lines().collect(Collectors.joining("\n"));
            }
            return parse(json);
        } catch (IOException e) {
            throw new PjdbcCapabilitiesException("Failed to load capabilities manifest", e);
        }
    }

    private static PjdbcCapabilities buildFromReflection() {
        List<DriverCapability> drivers = new ArrayList<>();

        // Known driver classes - scan all drivers in org.pjdbc.drivers package
        String[] driverNames = {
            "AuditDriver", "CachingDriver", "CatDriver", "ChaosDriver", "CircuitBreakerDriver",
            "DataMaskingDriver", "FederatingDriver", "FilterDriver", "HazelcastCachingDriver",
            "HikariPoolDriver", "LoadBalancingDriver", "LogDriver", "MemcachedCachingDriver",
            "MetricsDriver", "MockDriver", "NullDriver", "PoolDriver", "RateLimitDriver",
            "ReadonlyDriver", "RedisCachingDriver", "RetryDriver", "SchemaValidationDriver",
            "SinkDriver", "TeeDriver", "TimeoutDriver", "TracingDriver", "UserMapDriver", "VoidDriver"
        };

        for (String name : driverNames) {
            try {
                Class<?> clazz = Class.forName("org.pjdbc.drivers." + name);
                DriverCapability cap = buildDriverCapability(clazz);
                if (cap != null) {
                    drivers.add(cap);
                }
            } catch (ClassNotFoundException e) {
                // Skip if not found
            }
        }

        // Sort by prefix
        drivers.sort((a, b) -> a.prefix().compareTo(b.prefix()));

        return new PjdbcCapabilities("1.0-reflection", drivers, Source.REFLECTION);
    }

    private static DriverCapability buildDriverCapability(Class<?> clazz) {
        org.pjdbc.annotations.DriverCapability cap =
            clazz.getAnnotation(org.pjdbc.annotations.DriverCapability.class);

        if (cap == null) {
            return null;
        }

        String name = cap.name().isEmpty() ? clazz.getSimpleName() : cap.name();
        String prefix = cap.prefix();
        String driverClass = clazz.getName();
        String description = cap.description();
        List<String> capabilities = Arrays.asList(cap.capabilities());
        boolean composable = cap.composable();
        boolean terminal = cap.terminal();

        // Extract parameters
        List<DriverCapability.Parameter> parameters = new ArrayList<>();
        DriverParameter[] paramAnnotations = clazz.getAnnotationsByType(DriverParameter.class);
        for (DriverParameter param : paramAnnotations) {
            Object defaultValue = param.defaultValue().isEmpty() ? null : parseDefaultValue(param);
            Number min = param.min() == Long.MIN_VALUE ? null : param.min();
            Number max = param.max() == Long.MAX_VALUE ? null : param.max();
            List<String> enumValues = param.enumValues().length == 0 ? null :
                Arrays.asList(param.enumValues());

            parameters.add(new DriverCapability.Parameter(
                param.name(),
                param.type().name().toLowerCase(),
                param.description(),
                defaultValue,
                param.required(),
                min,
                max,
                enumValues
            ));
        }

        // Extract dependencies
        List<DriverCapability.Dependency> dependencies = new ArrayList<>();
        DriverDependency[] depAnnotations = clazz.getAnnotationsByType(DriverDependency.class);
        for (DriverDependency dep : depAnnotations) {
            dependencies.add(new DriverCapability.Dependency(
                dep.groupId(),
                dep.artifactId(),
                dep.version().isEmpty() ? null : dep.version(),
                dep.optional()
            ));
        }

        // Extract side effects
        DriverCapability.SideEffects sideEffects = null;
        DriverSideEffects seAnnotation = clazz.getAnnotation(DriverSideEffects.class);
        if (seAnnotation != null) {
            sideEffects = new DriverCapability.SideEffects(
                seAnnotation.logging(),
                seAnnotation.metrics(),
                seAnnotation.network(),
                seAnnotation.filesystem(),
                seAnnotation.stateful()
            );
        }

        return new DriverCapability(
            name,
            prefix,
            driverClass,
            description,
            capabilities.isEmpty() ? null : capabilities,
            parameters.isEmpty() ? null : parameters,
            dependencies.isEmpty() ? null : dependencies,
            sideEffects,
            composable,
            terminal
        );
    }

    private static Object parseDefaultValue(DriverParameter param) {
        String value = param.defaultValue();
        return switch (param.type()) {
            case INTEGER -> Long.parseLong(value);
            case FLOAT -> Double.parseDouble(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            case STRING -> value;
        };
    }

    /**
     * Parses capabilities from a JSON string.
     * Uses a simple parser to avoid external dependencies.
     */
    static PjdbcCapabilities parse(String json) {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        PjdbcCapabilities parsed = parser.parseCapabilities();
        // Re-wrap with MANIFEST source
        return new PjdbcCapabilities(parsed.version, parsed.drivers, Source.MANIFEST);
    }

    /**
     * Returns the schema version of the capabilities manifest.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the source from which capabilities were loaded.
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns all available drivers.
     */
    public List<DriverCapability> getAllDrivers() {
        return drivers;
    }

    /**
     * Finds a driver by its URL prefix.
     *
     * @param prefix The URL prefix (e.g., "pool", "log")
     * @return The driver if found
     */
    public Optional<DriverCapability> findByPrefix(String prefix) {
        return drivers.stream()
            .filter(d -> d.prefix().equals(prefix))
            .findFirst();
    }

    /**
     * Finds a driver by its class name.
     *
     * @param className The fully qualified class name
     * @return The driver if found
     */
    public Optional<DriverCapability> findByClass(String className) {
        return drivers.stream()
            .filter(d -> d.driverClass().equals(className))
            .findFirst();
    }

    /**
     * Finds all drivers with the specified capability.
     *
     * @param capability The capability tag (e.g., "caching", "pooling")
     * @return List of matching drivers
     */
    public List<DriverCapability> findByCapability(String capability) {
        return drivers.stream()
            .filter(d -> d.hasCapability(capability))
            .collect(Collectors.toList());
    }

    /**
     * Finds all drivers that are composable (can be chained).
     */
    public List<DriverCapability> findComposable() {
        return drivers.stream()
            .filter(DriverCapability::composable)
            .collect(Collectors.toList());
    }

    /**
     * Finds all terminal drivers (those that don't delegate).
     */
    public List<DriverCapability> findTerminal() {
        return drivers.stream()
            .filter(DriverCapability::terminal)
            .collect(Collectors.toList());
    }

    /**
     * Finds all drivers with external dependencies.
     */
    public List<DriverCapability> findWithDependencies() {
        return drivers.stream()
            .filter(d -> d.dependencies() != null && !d.dependencies().isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Finds all drivers that produce the specified side effect.
     *
     * @param sideEffect One of: "logging", "metrics", "network", "filesystem", "stateful"
     * @return List of matching drivers
     */
    public List<DriverCapability> findBySideEffect(String sideEffect) {
        return drivers.stream()
            .filter(d -> {
                if (d.sideEffects() == null) return false;
                return switch (sideEffect) {
                    case "logging" -> d.sideEffects().logging();
                    case "metrics" -> d.sideEffects().metrics();
                    case "network" -> d.sideEffects().network();
                    case "filesystem" -> d.sideEffects().filesystem();
                    case "stateful" -> d.sideEffects().stateful();
                    default -> false;
                };
            })
            .collect(Collectors.toList());
    }

    /**
     * Returns a list of all available capability tags across all drivers.
     */
    public List<String> getAllCapabilityTags() {
        return drivers.stream()
            .filter(d -> d.capabilities() != null)
            .flatMap(d -> d.capabilities().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Checks if a driver with the given prefix is available.
     */
    public boolean hasDriver(String prefix) {
        return findByPrefix(prefix).isPresent();
    }

    /**
     * Returns the number of available drivers.
     */
    public int getDriverCount() {
        return drivers.size();
    }

    @Override
    public String toString() {
        return "PjdbcCapabilities{version='" + version + "', source=" + source +
            ", drivers=" + drivers.size() + "}";
    }

    /**
     * Builder for creating PjdbcCapabilities instances programmatically.
     * Primarily useful for testing.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String version = "1.0";
        private Source source = Source.MANIFEST;
        private final List<DriverCapability> drivers = new ArrayList<>();

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Builder addDriver(DriverCapability driver) {
            this.drivers.add(driver);
            return this;
        }

        public PjdbcCapabilities build() {
            return new PjdbcCapabilities(version, drivers, source);
        }
    }
}
