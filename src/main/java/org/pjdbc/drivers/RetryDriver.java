package org.pjdbc.drivers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.pjdbc.sql.ProxyConnection;

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
 * <h2>PreparedStatement Parameter Tracking</h2>
 * <p>This driver tracks all parameter bindings (setXxx calls) on PreparedStatements.
 * When a connection failure occurs (SQL states starting with "08"), the driver
 * automatically reconnects, recreates the PreparedStatement, and replays all
 * parameter bindings before retrying the operation.</p>
 *
 * <p><strong>Stream parameters</strong> (InputStream, Reader) are buffered into
 * memory when set, enabling replay after reconnection. This adds memory overhead
 * proportional to stream size.</p>
 *
 * <p><strong>Note:</strong> CallableStatement retry after connection failure is not
 * yet supported due to the additional complexity of OUT parameter handling.</p>
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
        // Fallback without SQL - can't recreate on connection failure
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryPreparedStatement(delegate, conn, retryConn.getConfig(), null);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn, String sql) throws SQLException {
        // With SQL - can recreate on connection failure
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryPreparedStatement(delegate, conn, retryConn.getConfig(), sql);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        // Fallback without SQL - can't recreate on connection failure
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryCallableStatement(delegate, conn, retryConn.getConfig(), null);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn, String sql) throws SQLException {
        // With SQL - can recreate on connection failure
        RetryConnection retryConn = (RetryConnection) conn;
        return new RetryCallableStatement(delegate, conn, retryConn.getConfig(), sql);
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
         * Check if an exception indicates a connection failure.
         * Connection errors have SQL states starting with "08".
         */
        public boolean isConnectionError(SQLException e) {
            String sqlState = e.getSQLState();
            if (sqlState == null) return false;
            return sqlState.startsWith("08");
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
     * Uses ProxyConnection to eliminate boilerplate statement creation overrides.
     */
    private class RetryConnection extends ProxyConnection {
        private final RetryConfig config;
        private final String targetUrl;
        private final Properties connectionInfo;

        public RetryConnection(Connection conn, RetryDriver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new RetryConfig(url);
            // Store the target URL for reconnection
            this.targetUrl = driver.subname(url);
            this.connectionInfo = info != null ? (Properties) info.clone() : new Properties();
        }

        public RetryConfig getConfig() {
            return config;
        }

        /**
         * Get the underlying delegate connection.
         * Public accessor for use by statement classes during reconnection.
         */
        public Connection getDelegateConnection() {
            return getConnections().get(0);
        }

        /**
         * Reconnect to the database after a connection failure.
         * Creates a new underlying connection to replace the dead one.
         *
         * @throws SQLException if reconnection fails
         */
        public void reconnect() throws SQLException {
            LOG.fine(() -> "RetryDriver: Reconnecting to " + targetUrl);

            // Close old connection (ignore errors since it's likely already dead)
            try {
                Connection oldConn = getDelegate();
                if (oldConn != null && !oldConn.isClosed()) {
                    oldConn.close();
                }
            } catch (SQLException e) {
                LOG.fine(() -> "RetryDriver: Error closing old connection (expected): " + e.getMessage());
            }

            // Create new connection
            Connection newConn = DriverManager.getConnection(targetUrl, connectionInfo);

            // Replace the delegate connection
            getConnections().clear();
            getConnections().add(newConn);

            LOG.fine(() -> "RetryDriver: Successfully reconnected");
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
     * Automatically reconnects and recreates the statement on connection failures.
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

        /**
         * Recreate this statement after a connection failure.
         * Reconnects the connection and creates a fresh statement.
         */
        private void recreateStatement() throws SQLException {
            RetryConnection conn = (RetryConnection) getConnection();
            conn.reconnect();

            // Create new statement from reconnected connection
            Statement newStmt = conn.getDelegateConnection().createStatement();

            // Replace our delegate
            getStatements().clear();
            getStatements().add(newStmt);
        }

        /**
         * Execute with retry, recreating statement on connection errors.
         */
        private <T> T executeWithReconnect(String sql, StatementOperation<T> operation) throws SQLException {
            SQLException lastException = null;

            for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
                final int attemptNum = attempt;
                try {
                    Statement stmt = getStatements().get(0);
                    T result = operation.execute(stmt, sql);
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

                    PjdbcListeners.fireRetry(sql, e, attemptNum + 1, delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("RetryDriver: Interrupted during retry delay", ie);
                    }

                    // If this is a connection error, reconnect before next attempt
                    if (config.isConnectionError(e)) {
                        LOG.fine(() -> "RetryDriver: Connection error detected, recreating statement");
                        recreateStatement();
                    }
                }
            }

            throw lastException != null ? lastException : new SQLException("RetryDriver: Unknown error");
        }

        @FunctionalInterface
        private interface StatementOperation<T> {
            T execute(Statement stmt, String sql) throws SQLException;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            return executeWithReconnect(sql, (stmt, s) -> stmt.execute(s));
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            return wrap(executeWithReconnect(sql, (stmt, s) -> stmt.executeQuery(s)));
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            return executeWithReconnect(sql, (stmt, s) -> stmt.executeUpdate(s));
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // For batch, we can't easily recreate after connection failure
            // because the batch contents are lost. Just retry on the same statement.
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }

    /**
     * PreparedStatement wrapper that retries on transient errors.
     * Automatically reconnects, recreates the statement, and replays parameter
     * bindings on connection failures if SQL was provided at construction time.
     *
     * <p>Parameter bindings are tracked via a functional interface that captures
     * the parameter values at set time. Stream parameters (InputStream, Reader)
     * are buffered into memory to enable replay. This adds memory overhead but
     * enables transparent retry across connection failures.</p>
     */
    private static class RetryPreparedStatement extends AbstractPreparedStatement {
        private final RetryConfig config;
        private final String sql;

        /**
         * Functional interface for replaying a parameter binding.
         */
        @FunctionalInterface
        private interface ParameterSetter {
            void apply(PreparedStatement ps) throws SQLException;
        }

        /**
         * Tracks parameter bindings for replay after statement recreation.
         * Uses LinkedHashMap to preserve insertion order (useful for debugging).
         */
        private final Map<Integer, ParameterSetter> parameterBindings = new LinkedHashMap<>();

        public RetryPreparedStatement(PreparedStatement delegate, Connection conn, RetryConfig config, String sql) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
        }

        // ========== Stream Buffering Helpers ==========

        private byte[] bufferInputStream(InputStream x) throws SQLException {
            if (x == null) return null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = x.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                throw new SQLException("RetryDriver: Failed to buffer InputStream for retry", e);
            }
        }

        private String bufferReader(Reader r) throws SQLException {
            if (r == null) return null;
            try {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
                return sb.toString();
            } catch (IOException e) {
                throw new SQLException("RetryDriver: Failed to buffer Reader for retry", e);
            }
        }

        // ========== Statement Recreation ==========

        /**
         * Recreate this statement after a connection failure.
         * Reconnects the connection, creates a fresh PreparedStatement,
         * and replays all parameter bindings.
         *
         * @throws SQLException if SQL is not available or recreation fails
         */
        private void recreateStatement() throws SQLException {
            if (sql == null) {
                throw new SQLException(
                    "RetryDriver: Cannot retry PreparedStatement after connection failure. " +
                    "SQL was not available for statement recreation. " +
                    "Use application-level retry or plain Statement for retryable operations.",
                    "08006");
            }

            RetryConnection conn = (RetryConnection) getConnection();
            conn.reconnect();

            // Create new PreparedStatement from reconnected connection
            PreparedStatement newStmt = conn.getDelegateConnection().prepareStatement(sql);

            // Replace our delegate
            getPreparedStatements().clear();
            getPreparedStatements().add(newStmt);

            // Replay all parameter bindings
            replayBindings(newStmt);

            LOG.fine(() -> String.format("RetryDriver: Recreated PreparedStatement with %d parameter bindings",
                parameterBindings.size()));
        }

        /**
         * Replay all tracked parameter bindings to a new statement.
         */
        private void replayBindings(PreparedStatement ps) throws SQLException {
            for (ParameterSetter setter : parameterBindings.values()) {
                setter.apply(ps);
            }
        }

        // ========== Parameter Tracking Overrides ==========

        @Override
        public void clearParameters() throws SQLException {
            parameterBindings.clear();
            super.clearParameters();
        }

        @Override
        public void setNull(int parameterIndex, int sqlType) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setNull(parameterIndex, sqlType));
            super.setNull(parameterIndex, sqlType);
        }

        @Override
        public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setNull(parameterIndex, sqlType, typeName));
            super.setNull(parameterIndex, sqlType, typeName);
        }

        @Override
        public void setBoolean(int parameterIndex, boolean x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setBoolean(parameterIndex, x));
            super.setBoolean(parameterIndex, x);
        }

        @Override
        public void setByte(int parameterIndex, byte x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setByte(parameterIndex, x));
            super.setByte(parameterIndex, x);
        }

        @Override
        public void setShort(int parameterIndex, short x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setShort(parameterIndex, x));
            super.setShort(parameterIndex, x);
        }

        @Override
        public void setInt(int parameterIndex, int x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setInt(parameterIndex, x));
            super.setInt(parameterIndex, x);
        }

        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setLong(parameterIndex, x));
            super.setLong(parameterIndex, x);
        }

        @Override
        public void setFloat(int parameterIndex, float x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setFloat(parameterIndex, x));
            super.setFloat(parameterIndex, x);
        }

        @Override
        public void setDouble(int parameterIndex, double x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setDouble(parameterIndex, x));
            super.setDouble(parameterIndex, x);
        }

        @Override
        public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setBigDecimal(parameterIndex, x));
            super.setBigDecimal(parameterIndex, x);
        }

        @Override
        public void setString(int parameterIndex, String x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setString(parameterIndex, x));
            super.setString(parameterIndex, x);
        }

        @Override
        public void setNString(int parameterIndex, String value) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setNString(parameterIndex, value));
            super.setNString(parameterIndex, value);
        }

        @Override
        public void setBytes(int parameterIndex, byte[] x) throws SQLException {
            byte[] copy = x != null ? x.clone() : null;
            parameterBindings.put(parameterIndex, ps -> ps.setBytes(parameterIndex, copy));
            super.setBytes(parameterIndex, x);
        }

        @Override
        public void setDate(int parameterIndex, Date x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setDate(parameterIndex, x));
            super.setDate(parameterIndex, x);
        }

        @Override
        public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
            Calendar calCopy = cal != null ? (Calendar) cal.clone() : null;
            parameterBindings.put(parameterIndex, ps -> ps.setDate(parameterIndex, x, calCopy));
            super.setDate(parameterIndex, x, cal);
        }

        @Override
        public void setTime(int parameterIndex, Time x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setTime(parameterIndex, x));
            super.setTime(parameterIndex, x);
        }

        @Override
        public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
            Calendar calCopy = cal != null ? (Calendar) cal.clone() : null;
            parameterBindings.put(parameterIndex, ps -> ps.setTime(parameterIndex, x, calCopy));
            super.setTime(parameterIndex, x, cal);
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setTimestamp(parameterIndex, x));
            super.setTimestamp(parameterIndex, x);
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
            Calendar calCopy = cal != null ? (Calendar) cal.clone() : null;
            parameterBindings.put(parameterIndex, ps -> ps.setTimestamp(parameterIndex, x, calCopy));
            super.setTimestamp(parameterIndex, x, cal);
        }

        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setObject(parameterIndex, x));
            super.setObject(parameterIndex, x);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setObject(parameterIndex, x, targetSqlType));
            super.setObject(parameterIndex, x, targetSqlType);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setObject(parameterIndex, x, targetSqlType, scaleOrLength));
            super.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        }

        @Override
        public void setRef(int parameterIndex, Ref x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setRef(parameterIndex, x));
            super.setRef(parameterIndex, x);
        }

        @Override
        public void setBlob(int parameterIndex, Blob x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setBlob(parameterIndex, x));
            super.setBlob(parameterIndex, x);
        }

        @Override
        public void setClob(int parameterIndex, Clob x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setClob(parameterIndex, x));
            super.setClob(parameterIndex, x);
        }

        @Override
        public void setNClob(int parameterIndex, NClob value) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setNClob(parameterIndex, value));
            super.setNClob(parameterIndex, value);
        }

        @Override
        public void setArray(int parameterIndex, Array x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setArray(parameterIndex, x));
            super.setArray(parameterIndex, x);
        }

        @Override
        public void setURL(int parameterIndex, URL x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setURL(parameterIndex, x));
            super.setURL(parameterIndex, x);
        }

        @Override
        public void setRowId(int parameterIndex, RowId x) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setRowId(parameterIndex, x));
            super.setRowId(parameterIndex, x);
        }

        @Override
        public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
            parameterBindings.put(parameterIndex, ps -> ps.setSQLXML(parameterIndex, xmlObject));
            super.setSQLXML(parameterIndex, xmlObject);
        }

        // ========== Stream-based Parameters (buffered for replay) ==========

        @Override
        public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setAsciiStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null));
            super.setAsciiStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null);
        }

        @Override
        public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setAsciiStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setAsciiStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setAsciiStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setAsciiStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setUnicodeStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setUnicodeStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setBinaryStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null));
            super.setBinaryStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null);
        }

        @Override
        public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setBinaryStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setBinaryStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
            byte[] data = bufferInputStream(x);
            parameterBindings.put(parameterIndex, ps -> ps.setBinaryStream(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setBinaryStream(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setCharacterStream(parameterIndex,
                data != null ? new StringReader(data) : null));
            super.setCharacterStream(parameterIndex, data != null ? new StringReader(data) : null);
        }

        @Override
        public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setCharacterStream(parameterIndex,
                data != null ? new StringReader(data) : null, length));
            super.setCharacterStream(parameterIndex, data != null ? new StringReader(data) : null, length);
        }

        @Override
        public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setCharacterStream(parameterIndex,
                data != null ? new StringReader(data) : null, length));
            super.setCharacterStream(parameterIndex, data != null ? new StringReader(data) : null, length);
        }

        @Override
        public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
            String data = bufferReader(value);
            parameterBindings.put(parameterIndex, ps -> ps.setNCharacterStream(parameterIndex,
                data != null ? new StringReader(data) : null));
            super.setNCharacterStream(parameterIndex, data != null ? new StringReader(data) : null);
        }

        @Override
        public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
            String data = bufferReader(value);
            parameterBindings.put(parameterIndex, ps -> ps.setNCharacterStream(parameterIndex,
                data != null ? new StringReader(data) : null, length));
            super.setNCharacterStream(parameterIndex, data != null ? new StringReader(data) : null, length);
        }

        @Override
        public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
            byte[] data = bufferInputStream(inputStream);
            parameterBindings.put(parameterIndex, ps -> ps.setBlob(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null));
            super.setBlob(parameterIndex, data != null ? new ByteArrayInputStream(data) : null);
        }

        @Override
        public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
            byte[] data = bufferInputStream(inputStream);
            parameterBindings.put(parameterIndex, ps -> ps.setBlob(parameterIndex,
                data != null ? new ByteArrayInputStream(data) : null, length));
            super.setBlob(parameterIndex, data != null ? new ByteArrayInputStream(data) : null, length);
        }

        @Override
        public void setClob(int parameterIndex, Reader reader) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setClob(parameterIndex,
                data != null ? new StringReader(data) : null));
            super.setClob(parameterIndex, data != null ? new StringReader(data) : null);
        }

        @Override
        public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setClob(parameterIndex,
                data != null ? new StringReader(data) : null, length));
            super.setClob(parameterIndex, data != null ? new StringReader(data) : null, length);
        }

        @Override
        public void setNClob(int parameterIndex, Reader reader) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setNClob(parameterIndex,
                data != null ? new StringReader(data) : null));
            super.setNClob(parameterIndex, data != null ? new StringReader(data) : null);
        }

        @Override
        public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
            String data = bufferReader(reader);
            parameterBindings.put(parameterIndex, ps -> ps.setNClob(parameterIndex,
                data != null ? new StringReader(data) : null, length));
            super.setNClob(parameterIndex, data != null ? new StringReader(data) : null, length);
        }

        // ========== Execution with Retry ==========

        /**
         * Execute with retry, recreating statement on connection errors.
         */
        private <T> T executeWithReconnect(PreparedStatementOperation<T> operation) throws SQLException {
            SQLException lastException = null;

            for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
                final int attemptNum = attempt;
                try {
                    PreparedStatement stmt = getDelegate();
                    T result = operation.execute(stmt);
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

                    PjdbcListeners.fireRetry(sql, e, attemptNum + 1, delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("RetryDriver: Interrupted during retry delay", ie);
                    }

                    // If this is a connection error and we have SQL, reconnect before next attempt
                    if (config.isConnectionError(e) && sql != null) {
                        LOG.fine(() -> "RetryDriver: Connection error detected, recreating PreparedStatement");
                        recreateStatement();
                    }
                }
            }

            throw lastException != null ? lastException : new SQLException("RetryDriver: Unknown error");
        }

        @FunctionalInterface
        private interface PreparedStatementOperation<T> {
            T execute(PreparedStatement stmt) throws SQLException;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithReconnect(PreparedStatement::execute);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithReconnect(PreparedStatement::executeQuery);
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithReconnect(PreparedStatement::executeUpdate);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // For batch, we can't easily recreate after connection failure
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }

    /**
     * CallableStatement wrapper that retries on transient errors.
     * Automatically reconnects and recreates the statement on connection failures
     * if SQL was provided at construction time.
     *
     * <p><strong>Warning:</strong> On connection failure, parameter bindings are lost
     * and must be re-applied by the caller.
     */
    private static class RetryCallableStatement extends AbstractCallableStatement {
        private final RetryConfig config;
        private final String sql;

        public RetryCallableStatement(CallableStatement delegate, Connection conn, RetryConfig config, String sql) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
        }

        /**
         * Recreate this statement after a connection failure.
         *
         * <p><strong>This method always throws SQLException.</strong> CallableStatement
         * retry after connection failure is not supported because parameter bindings
         * cannot be preserved across reconnection. Silently proceeding would cause
         * queries to execute with NULL/default parameters, risking data corruption.</p>
         *
         * @throws SQLException always, explaining that retry is not possible
         */
        private void recreateStatement() throws SQLException {
            if (sql == null) {
                throw new SQLException(
                    "RetryDriver: Cannot retry CallableStatement after connection failure. " +
                    "SQL was not available for statement recreation. " +
                    "Use application-level retry or plain Statement for retryable operations.",
                    "08006"); // Connection failure SQL state
            }

            // Even with SQL available, we cannot preserve parameter bindings.
            // Proceeding would execute the query with NULL/default parameters,
            // which could cause silent data corruption.
            throw new SQLException(
                "RetryDriver: Cannot retry CallableStatement after connection failure. " +
                "Parameter bindings cannot be preserved across reconnection. " +
                "Use application-level retry that re-binds parameters.",
                "08006"); // Connection failure SQL state
        }

        /**
         * Execute with retry, recreating statement on connection errors.
         */
        private <T> T executeWithReconnect(CallableStatementOperation<T> operation) throws SQLException {
            SQLException lastException = null;

            for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
                final int attemptNum = attempt;
                try {
                    CallableStatement stmt = (CallableStatement) getDelegate();
                    T result = operation.execute(stmt);
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

                    PjdbcListeners.fireRetry(sql, e, attemptNum + 1, delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("RetryDriver: Interrupted during retry delay", ie);
                    }

                    // If this is a connection error and we have SQL, reconnect before next attempt
                    if (config.isConnectionError(e) && sql != null) {
                        LOG.fine(() -> "RetryDriver: Connection error detected, recreating CallableStatement");
                        recreateStatement();
                    }
                }
            }

            throw lastException != null ? lastException : new SQLException("RetryDriver: Unknown error");
        }

        @FunctionalInterface
        private interface CallableStatementOperation<T> {
            T execute(CallableStatement stmt) throws SQLException;
        }

        @Override
        public boolean execute() throws SQLException {
            return executeWithReconnect(CallableStatement::execute);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return executeWithReconnect(CallableStatement::executeQuery);
        }

        @Override
        public int executeUpdate() throws SQLException {
            return executeWithReconnect(CallableStatement::executeUpdate);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // For batch, we can't easily recreate after connection failure
            return executeWithRetry(config, () -> super.executeBatch());
        }
    }
}
