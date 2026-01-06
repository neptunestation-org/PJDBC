package org.pjdbc.jmx;

import org.pjdbc.drivers.CircuitBreakerDriver.CircuitBreaker;
import org.pjdbc.drivers.CircuitBreakerDriver.State;

/**
 * JMX MBean implementation that wraps a CircuitBreaker instance.
 */
public class CircuitBreakerMBeanImpl implements CircuitBreakerMBean {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerMBeanImpl(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String getState() {
        return circuitBreaker.getState().name();
    }

    @Override
    public String getName() {
        return circuitBreaker.getName();
    }

    @Override
    public int getFailureThreshold() {
        return circuitBreaker.getFailureThreshold();
    }

    @Override
    public int getSuccessThreshold() {
        return circuitBreaker.getSuccessThreshold();
    }

    @Override
    public long getResetTimeout() {
        return circuitBreaker.getResetTimeout();
    }

    @Override
    public int getFailureCount() {
        return circuitBreaker.getFailureCount();
    }

    @Override
    public int getSuccessCount() {
        return circuitBreaker.getSuccessCount();
    }

    @Override
    public long getTotalRequests() {
        return circuitBreaker.getTotalRequests();
    }

    @Override
    public long getTotalFailures() {
        return circuitBreaker.getTotalFailures();
    }

    @Override
    public long getTotalRejections() {
        return circuitBreaker.getTotalRejections();
    }

    @Override
    public double getFailureRatePercent() {
        long total = circuitBreaker.getTotalRequests();
        if (total == 0) return 0.0;
        return (circuitBreaker.getTotalFailures() * 100.0) / total;
    }

    @Override
    public void reset() {
        circuitBreaker.reset();
    }

    @Override
    public void forceOpen() {
        circuitBreaker.forceState(State.OPEN);
    }

    @Override
    public void forceClosed() {
        circuitBreaker.forceState(State.CLOSED);
    }
}
