package org.pjdbc.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.capabilities.PjdbcCapabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the generated capabilities manifest is complete and consistent.
 *
 * <p>This test ensures:
 * <ul>
 *   <li>All drivers in the services file have @DriverCapability annotations</li>
 *   <li>The generated manifest contains all annotated drivers</li>
 *   <li>No orphaned entries exist in the manifest</li>
 *   <li>The services file and manifest are in sync</li>
 * </ul>
 *
 * <p>These tests fail the build if the manifest is stale or incomplete.
 */
@DisplayName("Manifest Validation")
public class ManifestValidationTest {

    private static final String SERVICES_FILE = "META-INF/services/java.sql.Driver";

    private static Set<String> servicesFileDrivers;
    private static Set<String> manifestDriverClasses;
    private static PjdbcCapabilities capabilities;

    @BeforeAll
    static void loadResources() throws IOException {
        servicesFileDrivers = loadServicesFile();
        capabilities = PjdbcCapabilities.load();
        manifestDriverClasses = capabilities.getAllDrivers().stream()
            .map(d -> d.driverClass())
            .collect(Collectors.toSet());
    }

    private static Set<String> loadServicesFile() throws IOException {
        Set<String> drivers = new HashSet<>();
        try (InputStream is = ManifestValidationTest.class.getClassLoader()
                .getResourceAsStream(SERVICES_FILE)) {
            if (is == null) {
                fail("Services file not found: " + SERVICES_FILE);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        drivers.add(line);
                    }
                }
            }
        }
        return drivers;
    }

    @Test
    @DisplayName("All drivers in services file have @DriverCapability annotation")
    void allServiceDriversHaveCapabilityAnnotation() {
        Set<String> missingAnnotation = new HashSet<>();

        for (String className : servicesFileDrivers) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!clazz.isAnnotationPresent(DriverCapability.class)) {
                    missingAnnotation.add(className);
                }
            } catch (ClassNotFoundException e) {
                fail("Driver class not found: " + className);
            }
        }

        assertTrue(missingAnnotation.isEmpty(),
            "Drivers in services file missing @DriverCapability annotation: " + missingAnnotation);
    }

    @Test
    @DisplayName("All drivers in services file are in the manifest")
    void allServiceDriversAreInManifest() {
        Set<String> missingFromManifest = new HashSet<>(servicesFileDrivers);
        missingFromManifest.removeAll(manifestDriverClasses);

        assertTrue(missingFromManifest.isEmpty(),
            "Drivers in services file but not in manifest: " + missingFromManifest +
            "\nRun 'mvn compile' to regenerate the manifest.");
    }

    @Test
    @DisplayName("All manifest entries correspond to real driver classes")
    void allManifestEntriesHaveRealClasses() {
        Set<String> invalidClasses = new HashSet<>();

        for (String className : manifestDriverClasses) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException e) {
                invalidClasses.add(className);
            }
        }

        assertTrue(invalidClasses.isEmpty(),
            "Manifest contains entries for non-existent classes: " + invalidClasses);
    }

    @Test
    @DisplayName("Manifest and services file have same driver count")
    void manifestAndServicesFileHaveSameCount() {
        assertEquals(servicesFileDrivers.size(), manifestDriverClasses.size(),
            "Services file has " + servicesFileDrivers.size() + " drivers, " +
            "manifest has " + manifestDriverClasses.size() + " drivers. " +
            "Services: " + servicesFileDrivers + ", Manifest: " + manifestDriverClasses);
    }

    @Test
    @DisplayName("No orphaned manifest entries (manifest matches services file)")
    void noOrphanedManifestEntries() {
        Set<String> orphaned = new HashSet<>(manifestDriverClasses);
        orphaned.removeAll(servicesFileDrivers);

        assertTrue(orphaned.isEmpty(),
            "Manifest contains drivers not in services file: " + orphaned +
            "\nAdd these to " + SERVICES_FILE + " or remove the @DriverCapability annotation.");
    }

    @Test
    @DisplayName("All manifest entries have required fields")
    void allManifestEntriesHaveRequiredFields() {
        for (var driver : capabilities.getAllDrivers()) {
            assertNotNull(driver.name(), "Driver missing name: " + driver);
            assertFalse(driver.name().isEmpty(), "Driver has empty name: " + driver);

            assertNotNull(driver.prefix(), "Driver missing prefix: " + driver);
            assertFalse(driver.prefix().isEmpty(), "Driver has empty prefix: " + driver);

            assertNotNull(driver.driverClass(), "Driver missing class: " + driver);
            assertFalse(driver.driverClass().isEmpty(), "Driver has empty class: " + driver);

            assertNotNull(driver.description(), "Driver missing description: " + driver.name());
            assertFalse(driver.description().isEmpty(), "Driver has empty description: " + driver.name());
        }
    }

    @Test
    @DisplayName("All driver prefixes are unique")
    void allDriverPrefixesAreUnique() {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (var driver : capabilities.getAllDrivers()) {
            if (!seen.add(driver.prefix())) {
                duplicates.add(driver.prefix());
            }
        }

        assertTrue(duplicates.isEmpty(),
            "Duplicate driver prefixes found: " + duplicates);
    }

    @Test
    @DisplayName("Driver prefixes match annotation values")
    void driverPrefixesMatchAnnotations() {
        for (var driver : capabilities.getAllDrivers()) {
            try {
                Class<?> clazz = Class.forName(driver.driverClass());
                DriverCapability annotation = clazz.getAnnotation(DriverCapability.class);

                assertNotNull(annotation,
                    "Driver class missing @DriverCapability: " + driver.driverClass());

                assertEquals(annotation.prefix(), driver.prefix(),
                    "Manifest prefix doesn't match annotation for " + driver.name());

            } catch (ClassNotFoundException e) {
                fail("Driver class not found: " + driver.driverClass());
            }
        }
    }

    @Test
    @DisplayName("Expected core drivers are present")
    void expectedCoreDriversArePresent() {
        // Core drivers after PJDBC 2.0 rescope
        String[] coreDrivers = {
            "cat", "retry", "timeout", "tee", "sink", "filter",
            "readonly", "chaos", "mask", "circuitbreaker"
        };

        for (String prefix : coreDrivers) {
            assertTrue(capabilities.hasDriver(prefix),
                "Expected core driver missing: " + prefix);
        }
    }

    @Test
    @DisplayName("Manifest version is set")
    void manifestVersionIsSet() {
        assertNotNull(capabilities.getVersion(), "Manifest version should not be null");
        assertFalse(capabilities.getVersion().isEmpty(), "Manifest version should not be empty");
    }
}
