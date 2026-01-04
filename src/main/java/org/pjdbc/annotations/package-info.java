/**
 * Annotations for declaring PJDBC driver capabilities.
 *
 * <p>These annotations are used to generate the {@code pjdbc.capabilities.json}
 * manifest at compile time, ensuring the manifest stays in sync with the
 * actual driver implementations.</p>
 *
 * <h2>Core Annotations</h2>
 * <ul>
 *   <li>{@link org.pjdbc.annotations.DriverCapability} - Declares driver metadata
 *       (prefix, description, capabilities)</li>
 *   <li>{@link org.pjdbc.annotations.DriverParameter} - Declares URL parameters
 *       accepted by the driver</li>
 *   <li>{@link org.pjdbc.annotations.DriverDependency} - Declares external
 *       dependencies (Maven artifacts)</li>
 *   <li>{@link org.pjdbc.annotations.DriverSideEffects} - Declares side effects
 *       (stateful, logging, network, etc.)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @DriverCapability(
 *     prefix = "cache",
 *     description = "Caches SELECT query results in memory",
 *     capabilities = {"caching"}
 * )
 * @DriverParameter(name = "ttl", type = ParameterType.INTEGER,
 *     description = "Time-to-live in seconds", defaultValue = "60")
 * @DriverParameter(name = "maxSize", type = ParameterType.INTEGER,
 *     description = "Maximum cached entries", defaultValue = "1000")
 * @DriverSideEffects(stateful = true)
 * public class CachingDriver extends AbstractProxyDriver {
 *     // ...
 * }
 * }</pre>
 *
 * <h2>Manifest Generation</h2>
 * <p>An annotation processor scans for these annotations during compilation
 * and generates the {@code pjdbc.capabilities.json} manifest. This ensures:</p>
 * <ul>
 *   <li>Single source of truth (the code itself)</li>
 *   <li>No manual manifest updates required</li>
 *   <li>Build fails if annotations are missing or invalid</li>
 * </ul>
 *
 * @see org.pjdbc.capabilities.PjdbcCapabilities
 */
package org.pjdbc.annotations;
