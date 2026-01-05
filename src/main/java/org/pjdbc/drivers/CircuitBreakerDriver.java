package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.sql.PjdbcListeners;

/**
 * CircuitBreakerDriver implements the circuit breaker pattern for fault tolerance.
 *
 * <p>The circuit breaker has three states:
 * <ul>
 *   <li>CLOSED - Normal operation, requests pass through to the database</li>
 *   <li>OPEN - Circuit is tripped, all requests fail fast without attempting the operation</li>
 *   <li>HALF_OPEN - Testing state, allows limited requests to check if backend has recovered</li>
 * </ul>
 *
 * <p>State transitions:
 * <ul>
 *   <li>CLOSED to OPEN: After failureThreshold consecutive failures</li>
 *   <li>OPEN to HALF_OPEN: After resetTimeout milliseconds have elapsed</li>
 *   <li>HALF_OPEN to CLOSED: After successThreshold consecutive successes</li>
 *   <li>HALF_OPEN to OPEN: On any failure</li>
 * </ul>
 *
 * <p>URL format: {@code jdbc:circuitbreaker[param=value,...]:jdbc:target:...}
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:circuitbreaker:jdbc:postgresql://localhost/mydb
 * jdbc:circuitbreaker[failureThreshold=3,resetTimeout=60000]:jdbc:postgresql://localhost/mydb
 * jdbc:circuitbreaker[name=primary-db,failureThreshold=10]:jdbc:mysql://localhost/db
 * </pre>
 */
@DriverCapability(
    prefix = "circuitbreaker",
    description = "Implements circuit breaker pattern for fault tolerance with CLOSED/OPEN/HALF_OPEN states",
    capabilities = {"resilience"}
)
@DriverParameter(name = "name", type = ParameterType.STRING,
    description = "Circuit breaker name for monitoring", defaultValue = "default")
@DriverParameter(name = "failureThreshold", type = ParameterType.INTEGER,
    description = "Failures before opening circuit", defaultValue = "5", min = 1)
@DriverParameter(name = "successThreshold", type = ParameterType.INTEGER,
    description = "Successes in half-open to close circuit", defaultValue = "1", min = 1)
@DriverParameter(name = "resetTimeout", type = ParameterType.INTEGER,
    description = "Time in ms before open -> half-open", defaultValue = "30000", min = 1)
@DriverSideEffects(stateful = true)
public class CircuitBreakerDriver extends AbstractProxyDriver {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerDriver.class.getName());

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    static {
        try {
            DriverManager.registerDriver(new CircuitBreakerDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "circuitbreaker".equals(subprotocol);
    }

    @Override
    protected boolean acceptsSubName(String subname) {
        return true;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        Connection delegate = DriverManager.getConnection(subname(url), info);
        return proxyConnection(delegate, url, info, this);
    }

    @Override
    protected Connection proxyConnection(Connection delegate, String url, Properties info, Driver driver) throws SQLException {
        return new CircuitBreakerConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        CircuitBreakerConnection cbConn = (CircuitBreakerConnection) conn;
        return new CircuitBreakerStatement(delegate, conn, cbConn.getCircuitBreaker());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        CircuitBreakerConnection cbConn = (CircuitBreakerConnection) conn;
        return new CircuitBreakerPreparedStatement(delegate, conn, cbConn.getCircuitBreaker());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        CircuitBreakerConnection cbConn = (CircuitBreakerConnection) conn;
        return new CircuitBreakerCallableStatement(delegate, conn, cbConn.getCircuitBreaker());
    }

    /**
     * Circuit breaker state machine implementation.
     * Thread-safe using atomic operations.
     */
    public static class CircuitBreaker {
        private final String name;
        private final int failureThreshold;
        private final int successThreshold;
        private final long resetTimeout;

        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);

        // Statistics
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalFailures = new AtomicLong(0);
        private final AtomicLong totalRejections = new AtomicLong(0);

        public CircuitBreaker(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.name = parser.getParameter("name", "default");
            this.failureThreshold = parseInt(parser.getParameter("failureThreshold", "5"));
            this.successThreshold = parseInt(parser.getParameter("successThreshold", "1"));
            this.resetTimeout = parseLong(parser.getParameter("resetTimeout", "30000"));
        }

        // Constructor for testing
        public CircuitBreaker(String name, int failureThreshold, int successThreshold, long resetTimeout) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.successThreshold = successThreshold;
            this.resetTimeout = resetTimeout;
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 5; }
        }

        private static long parseLong(String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 30000; }
        }

        /**
         * Check if the circuit allows requests to pass through.
         * May transition from OPEN to HALF_OPEN if reset timeout has elapsed.
         */
        public boolean allowRequest() {
            State currentState = state.get();

            if (currentState == State.CLOSED) {
                return true;
            }

            if (currentState == State.OPEN) {
                long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();
                if (timeSinceFailure >= resetTimeout) {
                    // Transition to half-open and allow a test request
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0);
                        LOG.fine(() -> String.format("CircuitBreaker[%s]: State transition OPEN -> HALF_OPEN " +
                            "after %dms timeout", name, timeSinceFailure));
                        PjdbcListeners.fireCircuitBreakerStateChange(name, "OPEN", "HALF_OPEN");
                        return true;
                    }
                }
                return false;
            }

            // HALF_OPEN - allow requests for testing
            return true;
        }

        /**
         * Record a successful operation.
         */
        public void recordSuccess() {
            totalRequests.incrementAndGet();
            State currentState = state.get();

            if (currentState == State.CLOSED) {
                failureCount.set(0);
            } else if (currentState == State.HALF_OPEN) {
                int successes = successCount.incrementAndGet();
                if (successes >= successThreshold) {
                    // Enough successes, close the circuit
                    if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                        failureCount.set(0);
                        successCount.set(0);
                        LOG.fine(() -> String.format("CircuitBreaker[%s]: State transition HALF_OPEN -> CLOSED " +
                            "after %d successful requests", name, successThreshold));
                        PjdbcListeners.fireCircuitBreakerStateChange(name, "HALF_OPEN", "CLOSED");
                    }
                }
            }
        }

        /**
         * Record a failed operation.
         */
        public void recordFailure() {
            totalRequests.incrementAndGet();
            totalFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            State currentState = state.get();

            if (currentState == State.CLOSED) {
                int failures = failureCount.incrementAndGet();
                if (failures >= failureThreshold) {
                    // Too many failures, open the circuit
                    if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                        LOG.fine(() -> String.format("CircuitBreaker[%s]: State transition CLOSED -> OPEN " +
                            "after %d consecutive failures", name, failureThreshold));
                        PjdbcListeners.fireCircuitBreakerStateChange(name, "CLOSED", "OPEN");
                    }
                }
            } else if (currentState == State.HALF_OPEN) {
                // Any failure in half-open opens the circuit immediately
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    LOG.fine(() -> String.format("CircuitBreaker[%s]: State transition HALF_OPEN -> OPEN " +
                        "after failure during test request", name));
                    PjdbcListeners.fireCircuitBreakerStateChange(name, "HALF_OPEN", "OPEN");
                }
                successCount.set(0);
            }
        }

        /**
         * Record a rejected request (circuit was open).
         */
        public void recordRejection() {
            totalRequests.incrementAndGet();
            totalRejections.incrementAndGet();
        }

        // Getters for state and statistics
        public String getName() { return name; }
        public State getState() { return state.get(); }
        public int getFailureCount() { return failureCount.get(); }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailureThreshold() { return failureThreshold; }
        public int getSuccessThreshold() { return successThreshold; }
        public long getResetTimeout() { return resetTimeout; }
        public long getTotalRequests() { return totalRequests.get(); }
        public long getTotalFailures() { return totalFailures.get(); }
        public long getTotalRejections() { return totalRejections.get(); }

        /**
         * Force the circuit to a specific state (for testing).
         */
        public void forceState(State newState) {
            state.set(newState);
            if (newState == State.CLOSED) {
                failureCount.set(0);
                successCount.set(0);
            } else if (newState == State.OPEN) {
                // Set lastFailureTime so circuit doesn't immediately transition to HALF_OPEN
                lastFailureTime.set(System.currentTimeMillis());
            }
        }

        /**
         * Reset all counters and statistics.
         */
        public void reset() {
            state.set(State.CLOSED);
            failureCount.set(0);
            successCount.set(0);
            lastFailureTime.set(0);
            totalRequests.set(0);
            totalFailures.set(0);
            totalRejections.set(0);
        }

        @Override
        public String toString() {
            return String.format("CircuitBreaker[name=%s, state=%s, failures=%d/%d, successes=%d/%d]",
                name, state.get(), failureCount.get(), failureThreshold,
                successCount.get(), successThreshold);
        }
    }

    /**
     * Connection wrapper that holds the circuit breaker instance.
     */
    private class CircuitBreakerConnection extends AbstractConnection {
        private final CircuitBreaker circuitBreaker;

        public CircuitBreakerConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.circuitBreaker = new CircuitBreaker(url);
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        @Override
        public Statement createStatement() throws SQLException {
            return proxyStatement(getDelegate().createStatement(), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, autoGeneratedKeys), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnIndexes), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnNames), this);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return proxyCallableStatement(getDelegate().prepareCall(sql), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }
    }

    /**
     * Functional interface for circuit breaker protected operations.
     */
    @FunctionalInterface
    private interface ProtectedOperation<T> {
        T execute() throws SQLException;
    }

    /**
     * Execute an operation with circuit breaker protection.
     */
    private static <T> T executeWithCircuitBreaker(CircuitBreaker cb, ProtectedOperation<T> operation) throws SQLException {
        if (!cb.allowRequest()) {
            cb.recordRejection();
            LOG.fine(() -> String.format("CircuitBreaker[%s]: Request rejected - circuit is OPEN " +
                "(total rejections: %d)", cb.getName(), cb.getTotalRejections()));
            PjdbcListeners.fireCircuitBreakerRejection(cb.getName(), cb.getState().name());
            throw new SQLException(
                String.format("CircuitBreaker '%s' is OPEN - failing fast. State: %s", cb.getName(), cb),
                "08000"  // Connection exception SQL state
            );
        }

        try {
            T result = operation.execute();
            cb.recordSuccess();
            return result;
        } catch (SQLException e) {
            cb.recordFailure();
            throw e;
        }
    }

    /**
     * Statement wrapper with circuit breaker protection.
     */
    private static class CircuitBreakerStatement extends AbstractStatement {
        private final CircuitBreaker circuitBreaker;

        public CircuitBreakerStatement(Statement delegate, Connection conn, CircuitBreaker cb) throws SQLException {
            super(delegate, conn);
            this.circuitBreaker = cb;
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            Driver driver = ((AbstractConnection)getConnection()).getDriver();
            return ((AbstractProxyDriver)driver).proxyResultSet(this, r);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.execute(sql));
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeQuery(sql));
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeUpdate(sql));
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeBatch());
        }
    }

    /**
     * PreparedStatement wrapper with circuit breaker protection.
     */
    private static class CircuitBreakerPreparedStatement extends AbstractPreparedStatement {
        private final CircuitBreaker circuitBreaker;

        public CircuitBreakerPreparedStatement(PreparedStatement delegate, Connection conn, CircuitBreaker cb) throws SQLException {
            super(delegate, conn);
            this.circuitBreaker = cb;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.execute());
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeQuery());
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeUpdate());
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeBatch());
        }
    }

    /**
     * CallableStatement wrapper with circuit breaker protection.
     */
    private static class CircuitBreakerCallableStatement extends AbstractCallableStatement {
        private final CircuitBreaker circuitBreaker;

        public CircuitBreakerCallableStatement(CallableStatement delegate, Connection conn, CircuitBreaker cb) throws SQLException {
            super(delegate, conn);
            this.circuitBreaker = cb;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.execute());
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeQuery());
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeUpdate());
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithCircuitBreaker(circuitBreaker, () -> super.executeBatch());
        }
    }

    /**
     * Get the circuit breaker instance for a connection.
     * Useful for monitoring circuit state.
     */
    public static CircuitBreaker getCircuitBreaker(Connection conn) {
        if (conn instanceof CircuitBreakerConnection) {
            return ((CircuitBreakerConnection) conn).getCircuitBreaker();
        }
        return null;
    }
}
