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

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.sql.ProxyConnection;

/**
 * TimeoutDriver enforces query timeout limits on all statements.
 *
 * <p>This driver wraps statements and applies a configurable query timeout
 * using JDBC's {@code Statement.setQueryTimeout()} method. When a query exceeds
 * the timeout, the database driver throws a SQLException.
 *
 * <p>URL format: {@code jdbc:timeout[param=value,...]:jdbc:target:...}
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:timeout:jdbc:postgresql://localhost/mydb
 * jdbc:timeout[queryTimeout=60]:jdbc:postgresql://localhost/mydb
 * jdbc:timeout[queryTimeout=10,cancelOnTimeout=false]:jdbc:mysql://localhost/db
 * </pre>
 *
 * <p>Notes:
 * <ul>
 *   <li>Timeout of 0 means no timeout (unlimited)</li>
 *   <li>The actual timeout behavior depends on the underlying JDBC driver</li>
 *   <li>Some databases may not support query cancellation</li>
 * </ul>
 */
@DriverCapability(
    prefix = "timeout",
    description = "Enforces query timeout limits on all statements",
    capabilities = {"resilience"}
)
@DriverParameter(name = "queryTimeout", type = ParameterType.INTEGER,
    description = "Query timeout in seconds (0 = no timeout)", defaultValue = "30", min = 0)
@DriverParameter(name = "cancelOnTimeout", type = ParameterType.BOOLEAN,
    description = "Whether to attempt cancellation on timeout", defaultValue = "true")
public class TimeoutDriver extends AbstractProxyDriver {

    static {
        try {
            DriverManager.registerDriver(new TimeoutDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "timeout".equals(subprotocol);
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
        return new TimeoutConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        TimeoutConnection timeoutConn = (TimeoutConnection) conn;
        return new TimeoutStatement(delegate, conn, timeoutConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        TimeoutConnection timeoutConn = (TimeoutConnection) conn;
        return new TimeoutPreparedStatement(delegate, conn, timeoutConn.getConfig());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        TimeoutConnection timeoutConn = (TimeoutConnection) conn;
        return new TimeoutCallableStatement(delegate, conn, timeoutConn.getConfig());
    }

    /**
     * Configuration holder for timeout parameters.
     */
    public static class TimeoutConfig {
        private final int queryTimeout;
        private final boolean cancelOnTimeout;

        public TimeoutConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.queryTimeout = parseInt(parser.getParameter("queryTimeout", "30"));
            this.cancelOnTimeout = parseBoolean(parser.getParameter("cancelOnTimeout", "true"));
        }

        // Constructor for testing
        public TimeoutConfig(int queryTimeout, boolean cancelOnTimeout) {
            this.queryTimeout = queryTimeout;
            this.cancelOnTimeout = cancelOnTimeout;
        }

        private static int parseInt(String s) {
            try {
                int val = Integer.parseInt(s);
                return val >= 0 ? val : 30;
            } catch (NumberFormatException e) {
                return 30;
            }
        }

        private static boolean parseBoolean(String s) {
            return !"false".equalsIgnoreCase(s);
        }

        public int getQueryTimeout() { return queryTimeout; }
        public boolean isCancelOnTimeout() { return cancelOnTimeout; }

        @Override
        public String toString() {
            return String.format("TimeoutConfig[queryTimeout=%ds, cancelOnTimeout=%s]",
                queryTimeout, cancelOnTimeout);
        }
    }

    /**
     * Connection wrapper that holds timeout configuration.
     * Uses ProxyConnection to eliminate boilerplate statement creation overrides.
     */
    private class TimeoutConnection extends ProxyConnection {
        private final TimeoutConfig config;

        public TimeoutConnection(Connection conn, TimeoutDriver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new TimeoutConfig(url);
        }

        public TimeoutConfig getConfig() {
            return config;
        }
    }

    /**
     * Statement wrapper that enforces query timeout.
     */
    private static class TimeoutStatement extends AbstractStatement {
        private final TimeoutConfig config;
        private int customTimeout = -1; // -1 means use config default

        public TimeoutStatement(Statement delegate, Connection conn, TimeoutConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
            // Apply default timeout immediately
            applyTimeout();
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            Driver driver = ((AbstractConnection)getConnection()).getDriver();
            return ((AbstractProxyDriver)driver).proxyResultSet(this, r);
        }

        private Statement delegate() {
            return getStatements().get(0);
        }

        private void applyTimeout() throws SQLException {
            int timeout = customTimeout >= 0 ? customTimeout : config.getQueryTimeout();
            delegate().setQueryTimeout(timeout);
        }

        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            // Allow override of the default timeout
            this.customTimeout = seconds;
            delegate().setQueryTimeout(seconds);
        }

        @Override
        public int getQueryTimeout() throws SQLException {
            return delegate().getQueryTimeout();
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            applyTimeout();
            return super.execute(sql);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            applyTimeout();
            return super.executeQuery(sql);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            applyTimeout();
            return super.executeUpdate(sql);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            applyTimeout();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate(String sql) throws SQLException {
            applyTimeout();
            return super.executeLargeUpdate(sql);
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            applyTimeout();
            return super.executeLargeBatch();
        }
    }

    /**
     * PreparedStatement wrapper that enforces query timeout.
     */
    private static class TimeoutPreparedStatement extends AbstractPreparedStatement {
        private final TimeoutConfig config;
        private int customTimeout = -1;

        public TimeoutPreparedStatement(PreparedStatement delegate, Connection conn, TimeoutConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
            applyTimeout();
        }

        private void applyTimeout() throws SQLException {
            int timeout = customTimeout >= 0 ? customTimeout : config.getQueryTimeout();
            getDelegate().setQueryTimeout(timeout);
        }

        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            this.customTimeout = seconds;
            getDelegate().setQueryTimeout(seconds);
        }

        @Override
        public int getQueryTimeout() throws SQLException {
            return getDelegate().getQueryTimeout();
        }

        @Override
        public boolean execute() throws SQLException {
            applyTimeout();
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            applyTimeout();
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            applyTimeout();
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            applyTimeout();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate() throws SQLException {
            applyTimeout();
            return super.executeLargeUpdate();
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            applyTimeout();
            return super.executeLargeBatch();
        }
    }

    /**
     * CallableStatement wrapper that enforces query timeout.
     */
    private static class TimeoutCallableStatement extends AbstractCallableStatement {
        private final TimeoutConfig config;
        private int customTimeout = -1;

        public TimeoutCallableStatement(CallableStatement delegate, Connection conn, TimeoutConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
            applyTimeout();
        }

        private void applyTimeout() throws SQLException {
            int timeout = customTimeout >= 0 ? customTimeout : config.getQueryTimeout();
            getDelegate().setQueryTimeout(timeout);
        }

        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            this.customTimeout = seconds;
            getDelegate().setQueryTimeout(seconds);
        }

        @Override
        public int getQueryTimeout() throws SQLException {
            return getDelegate().getQueryTimeout();
        }

        @Override
        public boolean execute() throws SQLException {
            applyTimeout();
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            applyTimeout();
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            applyTimeout();
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            applyTimeout();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate() throws SQLException {
            applyTimeout();
            return super.executeLargeUpdate();
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            applyTimeout();
            return super.executeLargeBatch();
        }
    }

    /**
     * Get the timeout configuration for a connection.
     * Returns null if connection is not a TimeoutDriver connection.
     */
    public static TimeoutConfig getTimeoutConfig(Connection conn) {
        if (conn instanceof TimeoutConnection) {
            return ((TimeoutConnection) conn).getConfig();
        }
        return null;
    }
}
