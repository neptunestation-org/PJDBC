package org.pjdbc.capabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private final String version;
    private final List<DriverCapability> drivers;

    private PjdbcCapabilities(String version, List<DriverCapability> drivers) {
        this.version = version;
        this.drivers = Collections.unmodifiableList(new ArrayList<>(drivers));
    }

    /**
     * Loads the capabilities manifest from the classpath.
     *
     * @return The loaded capabilities
     * @throws PjdbcCapabilitiesException if the manifest cannot be loaded or parsed
     */
    public static PjdbcCapabilities load() {
        if (instance == null) {
            synchronized (PjdbcCapabilities.class) {
                if (instance == null) {
                    instance = loadFromClasspath();
                }
            }
        }
        return instance;
    }

    /**
     * Forces a reload of the capabilities manifest.
     * Useful for testing or when the manifest may have changed.
     *
     * @return The reloaded capabilities
     */
    public static PjdbcCapabilities reload() {
        synchronized (PjdbcCapabilities.class) {
            instance = loadFromClasspath();
        }
        return instance;
    }

    private static PjdbcCapabilities loadFromClasspath() {
        try (InputStream is = PjdbcCapabilities.class.getClassLoader()
                .getResourceAsStream(MANIFEST_PATH)) {
            if (is == null) {
                throw new PjdbcCapabilitiesException(
                    "Capabilities manifest not found: " + MANIFEST_PATH);
            }
            String json = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
            return parse(json);
        } catch (IOException e) {
            throw new PjdbcCapabilitiesException("Failed to load capabilities manifest", e);
        }
    }

    /**
     * Parses capabilities from a JSON string.
     * Uses a simple parser to avoid external dependencies.
     */
    static PjdbcCapabilities parse(String json) {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        return parser.parseCapabilities();
    }

    /**
     * Returns the schema version of the capabilities manifest.
     */
    public String getVersion() {
        return version;
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
        return "PjdbcCapabilities{version='" + version + "', drivers=" + drivers.size() + "}";
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
        private final List<DriverCapability> drivers = new ArrayList<>();

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder addDriver(DriverCapability driver) {
            this.drivers.add(driver);
            return this;
        }

        public PjdbcCapabilities build() {
            return new PjdbcCapabilities(version, drivers);
        }
    }
}
