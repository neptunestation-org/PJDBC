package org.pjdbc.conformance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.pjdbc.capabilities.DriverCapability;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for side effect declarations across all PJDBC drivers.
 *
 * <p>Verifies that all drivers:
 * <ul>
 *   <li>Declare side effects consistently with their capabilities</li>
 *   <li>Network-using drivers declare network side effect</li>
 *   <li>Logging drivers declare logging side effect</li>
 *   <li>Stateful drivers declare stateful side effect</li>
 * </ul>
 */
@DisplayName("Side Effect Conformance")
public class SideEffectConformanceTest extends DriverConformanceTest {

    @Test
    @DisplayName("Logging capability implies logging side effect")
    void loggingCapabilityImpliesLoggingSideEffect() {
        List<DriverCapability> loggingDrivers = capabilities.findByCapability("logging");

        for (DriverCapability driver : loggingDrivers) {
            // LogDriver has logging capability and should declare logging side effect
            if ("log".equals(driver.prefix())) {
                assertTrue(driver.sideEffects() != null && driver.sideEffects().logging(),
                    "Driver " + driver.name() + " with logging capability should declare logging side effect");
            }
        }
    }

    @Test
    @DisplayName("Caching drivers with external stores declare network side effect")
    void distributedCachingDriversDeclareNetworkSideEffect() {
        List<String> networkCachePrefixes = List.of("rediscache", "memcache", "hazelcast");

        for (String prefix : networkCachePrefixes) {
            capabilities.findByPrefix(prefix).ifPresent(driver -> {
                assertTrue(driver.sideEffects() != null && driver.sideEffects().network(),
                    "Distributed caching driver " + driver.name() + " should declare network side effect");
            });
        }
    }

    @Test
    @DisplayName("In-memory caching driver does not declare network side effect")
    void inMemoryCachingDriverNoNetworkSideEffect() {
        capabilities.findByPrefix("cache").ifPresent(driver -> {
            assertFalse(driver.sideEffects() != null && driver.sideEffects().network(),
                "In-memory CachingDriver should not declare network side effect");
        });
    }

    @Test
    @DisplayName("Pooling drivers declare stateful side effect")
    void poolingDriversDeclareStatefulSideEffect() {
        List<DriverCapability> poolingDrivers = capabilities.findByCapability("pooling");

        for (DriverCapability driver : poolingDrivers) {
            assertTrue(driver.sideEffects() != null && driver.sideEffects().stateful(),
                "Pooling driver " + driver.name() + " should declare stateful side effect");
        }
    }

    @Test
    @DisplayName("Metrics drivers declare stateful and metrics side effects")
    void metricsDriversDeclareStatefulAndMetricsSideEffects() {
        List<DriverCapability> metricsDrivers = capabilities.findByCapability("metrics");

        for (DriverCapability driver : metricsDrivers) {
            assertNotNull(driver.sideEffects(),
                "Metrics driver " + driver.name() + " should have side effects declared");
            assertTrue(driver.sideEffects().stateful(),
                "Metrics driver " + driver.name() + " should declare stateful side effect");
            assertTrue(driver.sideEffects().metrics(),
                "Metrics driver " + driver.name() + " should declare metrics side effect");
        }
    }

    @Test
    @DisplayName("Passthrough drivers have minimal side effects")
    void passthroughDriversHaveMinimalSideEffects() {
        capabilities.findByPrefix("cat").ifPresent(driver -> {
            if (driver.sideEffects() != null) {
                assertFalse(driver.sideEffects().logging(), "CatDriver should not log");
                assertFalse(driver.sideEffects().network(), "CatDriver should not use network");
                assertFalse(driver.sideEffects().filesystem(), "CatDriver should not use filesystem");
                assertFalse(driver.sideEffects().stateful(), "CatDriver should not be stateful");
                assertFalse(driver.sideEffects().metrics(), "CatDriver should not collect metrics");
            }
        });
    }

    @Test
    @DisplayName("UserMapDriver declares filesystem side effect")
    void userMapDriverDeclaresFilesystemSideEffect() {
        capabilities.findByPrefix("mapuser").ifPresent(driver -> {
            assertTrue(driver.sideEffects() != null && driver.sideEffects().filesystem(),
                "UserMapDriver should declare filesystem side effect (reads properties file)");
        });
    }

    @Test
    @DisplayName("Testing drivers have appropriate side effects")
    void testingDriversHaveAppropriateSideEffects() {
        // SinkDriver - no side effects (discards everything)
        capabilities.findByPrefix("sink").ifPresent(driver -> {
            if (driver.sideEffects() != null) {
                assertFalse(driver.sideEffects().stateful(), "SinkDriver should not be stateful");
            }
        });

        // MockDriver - stateful (records operations)
        capabilities.findByPrefix("mock").ifPresent(driver -> {
            assertTrue(driver.sideEffects() != null && driver.sideEffects().stateful(),
                "MockDriver should be stateful (records operations)");
        });
    }

    @Test
    @DisplayName("Tracing drivers declare stateful side effect")
    void tracingDriversDeclareStatefulSideEffect() {
        List<DriverCapability> tracingDrivers = capabilities.findByCapability("tracing");

        for (DriverCapability driver : tracingDrivers) {
            assertTrue(driver.sideEffects() != null && driver.sideEffects().stateful(),
                "Tracing driver " + driver.name() + " should declare stateful side effect");
        }
    }

    @Test
    @DisplayName("Side effects are consistent within capability groups")
    void sideEffectsConsistentWithinCapabilityGroups() {
        // All distributed caching drivers should have the same side effect pattern
        List<String> distributedCachePrefixes = List.of("rediscache", "memcache", "hazelcast");
        Boolean expectedNetwork = null;
        Boolean expectedStateful = null;

        for (String prefix : distributedCachePrefixes) {
            var driverOpt = capabilities.findByPrefix(prefix);
            if (driverOpt.isPresent()) {
                var driver = driverOpt.get();
                if (driver.sideEffects() != null) {
                    if (expectedNetwork == null) {
                        expectedNetwork = driver.sideEffects().network();
                        expectedStateful = driver.sideEffects().stateful();
                    } else {
                        assertEquals(expectedNetwork, driver.sideEffects().network(),
                            "Distributed cache drivers should have consistent network side effect");
                        assertEquals(expectedStateful, driver.sideEffects().stateful(),
                            "Distributed cache drivers should have consistent stateful side effect");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Drivers with dependencies that require network declare network side effect")
    void driversWithNetworkDependenciesDeclareNetwork() {
        List<DriverCapability> driversWithDeps = capabilities.findWithDependencies();

        for (DriverCapability driver : driversWithDeps) {
            // Check if any dependency is a network client library
            boolean hasNetworkDependency = driver.dependencies().stream()
                .anyMatch(dep ->
                    dep.artifactId().contains("jedis") ||
                    dep.artifactId().contains("memcached") ||
                    dep.artifactId().contains("hazelcast"));

            if (hasNetworkDependency) {
                assertTrue(driver.sideEffects() != null && driver.sideEffects().network(),
                    "Driver " + driver.name() + " with network dependency should declare network side effect");
            }
        }
    }
}
