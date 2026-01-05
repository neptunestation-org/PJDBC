package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.sql.PjdbcListeners;

/**
 * RetryDriver automatically retries failed queries on transient errors.
 *
 * <h2>WARNING: Idempotency Required</h2>
 * <p><strong>This driver should only be used with idempotent operations.</strong>
 * Retrying non-idempotent operations (INSERT, UPDATE, DELETE) can cause data
 * corruption, duplicate records, or inconsistent state.</p>
 *
 * <h3>Safe to retry:</h3>
 * <ul>
 *   <li>SELECT queries (read-only)</li>
 *   <li>Idempotent writes (e.g., UPDATE with WHERE clause on unique key)</li>
 *   <li>Operations wrapped in application-level idempotency checks</li>
 * </ul>
 *
 * <h3>NOT safe to retry without additional safeguards:</h3>
 * <ul>
 *   <li>INSERT statements (may create duplicates)</li>
 *   <li>UPDATE without unique key constraint (may apply multiple times)</li>
 *   <li>DELETE statements (usually safe but verify business logic)</li>
 *   <li>Statements with side effects (triggers, sequences)</li>
 * </ul>
 *
 * <p>For non-idempotent operations, consider using {@code ReadonlyDriver} in
 * combination with RetryDriver, or implement application-level idempotency
 * using idempotency keys or optimistic locking.</p>
 *
 * <h2>Default Retryable SQL States</h2>
 * <ul>
 *   <li>08001, 08003, 08004, 08006, 08007 - Connection errors</li>
 *   <li>40001, 40P01 - Deadlock/serialization failures</li>
 *   <li>57P01 - Admin shutdown</li>
 *   <li>HYT00, HYT01 - Timeout errors</li>
 * </ul>
 *
 * <h2>Example URLs</h2>
 * <pre>
 * jdbc:retry:jdbc:postgresql://localhost/mydb
 * jdbc:retry[maxRetries=5,initialDelay=200]:jdbc:postgresql://localhost/mydb
 * jdbc:retry[retryOnSqlStates=40001;08006]:jdbc:mysql://localhost/db
 * </pre>
 *
 * @see ReadonlyDriver
 */
@DriverCapability(
    prefix = "retry",
    description = "Automatically retries failed queries on transient errors",
    capabilities = {"resilience"}
)
@DriverParameter(name = "maxRetries", type = ParameterType.INTEGER,
    description = "Maximum retry attempts", defaultValue = "3", min = 0)
@DriverParameter(name = "initialDelay", type = ParameterType.INTEGER,
    description = "Initial delay in ms before first retry", defaultValue = "100", min = 0)
@DriverParameter(name = "maxDelay", type = ParameterType.INTEGER,
    description = "Maximum delay cap in ms", defaultValue = "5000", min = 0)
@DriverParameter(name = "backoffMultiplier", type = ParameterType.FLOAT,
    description = "Multiplier for exponential backoff", defaultValue = "2.0")
@DriverParameter(name = "jitter", type = ParameterType.BOOLEAN,
    description = "Add random jitter to delays", defaultValue = "true")
@DriverParameter(name = "retryOnSqlStates", type = ParameterType.STRING,
    description = "Semicolon-separated SQL states to retry on")
public class RetryDriver extends AbstractProxyDriver {

    private static final Logger LOG = Logger.getLogger(RetryDriver.class.getName());

    /** Default SQL states that indicate transient/retryable errors */
    private static final Set<String> DEFAULT_RETRYABLE_STATES = new HashSet<>(Arrays.asList(
        "08001",  // SQL client unable to establish connection
        "08003",  // Connection does not exist
        "08004",  // Server rejected connection
        "08006",  // Connection failure
        "08007",  // Transaction resolution unknown
        "40001",  // Serialization failure (deadlock)
        "40P01",  // PostgreSQL deadlock detected
        "57P01",  // PostgreSQL admin shutdown
        "HYT00",  // Timeout expired
        "HYT01"   // Connection timeout
    ));

    static {
        try {
            DriverManager.registerDriver(new RetryDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "retry".equals(subprotocol);
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
        return new RetryConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryStatement(delegate, conn, retryConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryPreparedStatement(delegate, conn, retryConn.getConfig());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryCallableStatement(delegate, conn, retryConn.getConfig());
    }

    /**
     * Configuration holder for retry parameters, parsed once at connection time.
     */
    public static class RetryConfig {
        private final int maxRetries;
        private final long initialDelay;
        private final long maxDelay;
        private final double backoffMultiplier;
        private final boolean jitter;
        private final Set<String> retryableSqlStates;

        public RetryConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.maxRetries = parseInt(parser.getParameter("maxRetries", "3"));
            this.initialDelay = parseLong(parser.getParameter("initialDelay", "100"));
            this.maxDelay = parseLong(parser.getParameter("maxDelay", "5000"));
            this.backoffMultiplier = parseDouble(parser.getParameter("backoffMultiplier", "2.0"));
            this.jitter = parseBoolean(parser.getParameter("jitter", "true"));
            this.retryableSqlStates = parseSqlStates(parser.getParameter("retryOnSqlStates", null));
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 3; }
        }

        private static long parseLong(String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 100; }
        }

        private static double parseDouble(String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 2.0; }
        }

        private static boolean parseBoolean(String s) {
            return !"false".equalsIgnoreCase(s);
        }

        private static Set<String> parseSqlStates(String s) {
            if (s == null || s.trim().isEmpty()) {
                return DEFAULT_RETRYABLE_STATES;
            }
            Set<String> states = new HashSet<>();
            for (String state : s.split(";")) {
                String trimmed = state.trim().toUpperCase();
                if (!trimmed.isEmpty()) {
                    states.add(trimmed);
                }
            }
            return states;
        }

        public int getMaxRetries() { return maxRetries; }
        public long getInitialDelay() { return initialDelay; }
        public long getMaxDelay() { return maxDelay; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public boolean hasJitter() { return jitter; }
        public Set<String> getRetryableSqlStates() { return retryableSqlStates; }

        /**
         * Check if an exception is retryable based on SQL state.
         */
        public boolean isRetryable(SQLException e) {
            String sqlState = e.getSQLState();
            if (sqlState == null) return false;
            return retryableSqlStates.contains(sqlState.toUpperCase());
        }

        /**
         * Calculate delay for a given retry attempt (0-indexed).
         * Uses ThreadLocalRandom for thread-safe jitter generation.
         */
        public long calculateDelay(int attempt) {
            long delay = (long) (initialDelay * Math.pow(backoffMultiplier, attempt));
            delay = Math.min(delay, maxDelay);
            if (jitter) {
                // Add up to 25% jitter using thread-safe random
                delay = delay + ThreadLocalRandom.current().nextInt((int) Math.max(1, delay / 4));
            }
            return delay;
        }
    }

    /**
     * Connection wrapper that holds cached retry configuration.
     */
    private class RetryConnection extends AbstractConnection {
        private final RetryConfig config;

        public RetryConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new RetryConfig(url);
        }

        public RetryConfig getConfig() {
            return config;
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
     * Functional interface for retryable operations.
     */
    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws SQLException;
    }

    /**
     * Execute an operation with retry logic.
     */
    private static <T> T executeWithRetry(RetryConfig config, RetryableOperation<T> operation) throws SQLException {
        SQLException lastException = null;

        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            final int attemptNum = attempt;
            try {
                T result = operation.execute();
                if (attemptNum > 0) {
                    LOG.fine(() -> String.format("RetryDriver: Operation succeeded on attempt %d/%d",
                        attemptNum + 1, config.getMaxRetries() + 1));
                }
                return result;
            } catch (SQLException e) {
                lastException = e;

                if (!config.isRetryable(e) || attemptNum >= config.getMaxRetries()) {
                    if (attemptNum > 0) {
                        LOG.fine(() -> String.format("RetryDriver: Exhausted retries (%d attempts), " +
                            "final error: SQLState=%s, message=%s",
                            attemptNum + 1, e.getSQLState(), e.getMessage()));
                    }
                    throw e;
                }

                long delay = config.calculateDelay(attemptNum);
                LOG.fine(() -> String.format("RetryDriver: Retry attempt %d/%d after %dms delay, " +
                    "SQLState=%s, message=%s",
                    attemptNum + 1, config.getMaxRetries(), delay, e.getSQLState(), e.getMessage()));

                // Fire event listener notification
                PjdbcListeners.fireRetry(null, e, attemptNum + 1, delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("RetryDriver: Interrupted during retry delay", ie);
                }
            }
        }

        // Should not reach here, but just in case
        throw lastException != null ? lastException : new SQLException("RetryDriver: Unknown error");
    }

    /**
     * Statement wrapper that retries on transient errors.
     */
    private static class RetryStatement extends AbstractStatement {
        private final RetryConfig config;

        public RetryStatement(Statement delegate, Connection conn, RetryConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            Driver driver = ((AbstractConnection)getConnection()).getDriver();
            return ((AbstractProxyDriver)driver).proxyResultSet(this, r);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            return executeWithRetry(config, () -> super.execute(sql));
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            return executeWithRetry(config, () -> super.executeQuery(sql));
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            return executeWithRetry(config, () -> super.executeUpdate(sql));
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }

    /**
     * PreparedStatement wrapper that retries on transient errors.
     */
    private static class RetryPreparedStatement extends AbstractPreparedStatement {
        private final RetryConfig config;

        public RetryPreparedStatement(PreparedStatement delegate, Connection conn, RetryConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithRetry(config, () -> super.execute());
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithRetry(config, () -> super.executeQuery());
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithRetry(config, () -> super.executeUpdate());
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }

    /**
     * CallableStatement wrapper that retries on transient errors.
     */
    private static class RetryCallableStatement extends AbstractCallableStatement {
        private final RetryConfig config;

        public RetryCallableStatement(CallableStatement delegate, Connection conn, RetryConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithRetry(config, () -> super.execute());
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithRetry(config, () -> super.executeQuery());
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithRetry(config, () -> super.executeUpdate());
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }
}
