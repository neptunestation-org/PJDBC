package org.pjdbc.jmx;

/**
 * JMX MBean interface for monitoring CircuitBreaker state.
 *
 * <p>Exposes circuit breaker state, configuration, and statistics
 * for operational monitoring via JMX.
 */
public interface CircuitBreakerMBean {

    // State
    /**
     * Get current circuit breaker state: CLOSED, OPEN, or HALF_OPEN.
     */
    String getState();

    /**
     * Get the circuit breaker name.
     */
    String getName();

    // Configuration
    /**
     * Get failure threshold before opening circuit.
     */
    int getFailureThreshold();

    /**
     * Get success threshold to close circuit from half-open.
     */
    int getSuccessThreshold();

    /**
     * Get reset timeout in milliseconds (OPEN to HALF_OPEN).
     */
    long getResetTimeout();

    // Current counters
    /**
     * Get current consecutive failure count.
     */
    int getFailureCount();

    /**
     * Get current consecutive success count (in HALF_OPEN state).
     */
    int getSuccessCount();

    // Statistics
    /**
     * Get total requests processed.
     */
    long getTotalRequests();

    /**
     * Get total failed requests.
     */
    long getTotalFailures();

    /**
     * Get total rejected requests (circuit was open).
     */
    long getTotalRejections();

    /**
     * Get failure rate as percentage (0-100).
     */
    double getFailureRatePercent();

    // Operations
    /**
     * Reset the circuit breaker to CLOSED state and clear all counters.
     */
    void reset();

    /**
     * Force circuit to OPEN state.
     */
    void forceOpen();

    /**
     * Force circuit to CLOSED state.
     */
    void forceClosed();
}
