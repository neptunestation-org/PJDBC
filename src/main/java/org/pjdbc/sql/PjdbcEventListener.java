package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.List;

/**
 * Listener interface for PJDBC driver events.
 *
 * <p>Implementations can monitor driver behavior without modifying driver code.
 * Register listeners via:
 * <ul>
 *   <li>{@link PjdbcListeners#register(PjdbcEventListener)} for programmatic registration</li>
 *   <li>ServiceLoader via META-INF/services/org.pjdbc.sql.PjdbcEventListener</li>
 * </ul>
 *
 * <p>All callback methods have default empty implementations so listeners
 * only need to override the events they care about.
 *
 * <p>Example usage:
 * <pre>{@code
 * PjdbcListeners.register(new PjdbcEventListener() {
 *     @Override
 *     public void onRetry(String sql, SQLException cause, int attempt, long delayMs) {
 *         logger.warn("Retry attempt {} after {}ms: {}", attempt, delayMs, cause.getMessage());
 *     }
 * });
 * }</pre>
 *
 * @since 2.2.0
 */
public interface PjdbcEventListener {

    /**
     * Called when a retry is about to be attempted.
     *
     * @param sql the SQL statement being retried (may be null for non-SQL operations)
     * @param cause the exception that triggered the retry
     * @param attempt the retry attempt number (1 for first retry, 2 for second, etc.)
     * @param delayMs the delay in milliseconds before the retry will be attempted
     */
    default void onRetry(String sql, SQLException cause, int attempt, long delayMs) {}

    /**
     * Called when a circuit breaker changes state.
     *
     * @param name the circuit breaker name
     * @param oldState the previous state (CLOSED, OPEN, or HALF_OPEN)
     * @param newState the new state
     */
    default void onCircuitBreakerStateChange(String name, String oldState, String newState) {}

    /**
     * Called when SQL is transformed by a filter driver.
     *
     * @param original the original SQL statement
     * @param transformed the transformed SQL statement
     */
    default void onSqlTransformed(String original, String transformed) {}

    /**
     * Called when a query is executed against multiple federated databases.
     *
     * @param sql the SQL statement being executed
     * @param targetUrls the JDBC URLs of the target databases
     */
    default void onFederatedQuery(String sql, List<String> targetUrls) {}

    /**
     * Called when a request is rejected by a circuit breaker.
     *
     * @param name the circuit breaker name
     * @param state the current circuit breaker state
     */
    default void onCircuitBreakerRejection(String name, String state) {}

    /**
     * Called when chaos injection occurs (for ChaosDriver).
     *
     * @param type the type of chaos injected (e.g., "delay", "exception", "timeout")
     * @param sql the SQL statement affected (may be null)
     * @param details additional details about the chaos injection
     */
    default void onChaosInjected(String type, String sql, String details) {}
}
