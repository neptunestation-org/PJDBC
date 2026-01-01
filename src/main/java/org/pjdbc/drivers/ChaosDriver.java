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
import java.util.Random;

import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractResultSet;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.JdbcUrlParser;

/**
 * ChaosDriver injects configurable failures and latency for resilience testing.
 *
 * URL format: jdbc:chaos[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   failureRate     - Probability (0.0-1.0) of throwing SQLException on query (default: 0.0)
 *   latency         - Fixed delay in milliseconds before each query (default: 0)
 *   latencyVariance - Random additional delay up to this value in ms (default: 0)
 *   connectionDropRate - Probability of closing connection unexpectedly (default: 0.0)
 *   resultSetLatency - Delay in ms for each ResultSet.next() call (default: 0)
 *   exceptionMessage - Custom exception message (default: "ChaosDriver: Induced failure")
 *
 * Example URLs:
 *   jdbc:chaos[failureRate=0.1]:jdbc:postgresql://localhost/mydb
 *   jdbc:chaos[latency=100,latencyVariance=50]:jdbc:h2:mem:test
 *   jdbc:chaos[failureRate=0.05,connectionDropRate=0.01]:jdbc:mysql://localhost/db
 */
public class ChaosDriver extends AbstractProxyDriver {

    static {
        try {
            DriverManager.registerDriver(new ChaosDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "chaos".equals(subprotocol);
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
        return new ChaosConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        ChaosConnection chaosConn = (ChaosConnection) conn;
        return new ChaosStatement(delegate, conn, chaosConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        ChaosConnection chaosConn = (ChaosConnection) conn;
        return new ChaosPreparedStatement(delegate, conn, chaosConn.getConfig());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        ChaosConnection chaosConn = (ChaosConnection) conn;
        return new ChaosCallableStatement(delegate, conn, chaosConn.getConfig());
    }

    @Override
    protected ResultSet proxyResultSet(Statement stmt, ResultSet delegate) throws SQLException {
        Connection conn = stmt.getConnection();
        if (conn instanceof ChaosConnection) {
            ChaosConnection chaosConn = (ChaosConnection) conn;
            return new ChaosResultSet(stmt, delegate, chaosConn.getConfig());
        }
        return delegate;
    }

    /**
     * Configuration holder for chaos parameters, parsed once at connection time.
     */
    public static class ChaosConfig {
        private final double failureRate;
        private final int latency;
        private final int latencyVariance;
        private final double connectionDropRate;
        private final int resultSetLatency;
        private final String exceptionMessage;
        private final Random random;

        public ChaosConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.failureRate = parseDouble(parser.getParameter("failureRate", "0.0"));
            this.latency = parseInt(parser.getParameter("latency", "0"));
            this.latencyVariance = parseInt(parser.getParameter("latencyVariance", "0"));
            this.connectionDropRate = parseDouble(parser.getParameter("connectionDropRate", "0.0"));
            this.resultSetLatency = parseInt(parser.getParameter("resultSetLatency", "0"));
            this.exceptionMessage = parser.getParameter("exceptionMessage", "ChaosDriver: Induced failure");
            this.random = new Random();
        }

        private static double parseDouble(String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }

        public double getFailureRate() { return failureRate; }
        public int getLatency() { return latency; }
        public int getLatencyVariance() { return latencyVariance; }
        public double getConnectionDropRate() { return connectionDropRate; }
        public int getResultSetLatency() { return resultSetLatency; }
        public String getExceptionMessage() { return exceptionMessage; }
        public Random getRandom() { return random; }
    }

    /**
     * Connection wrapper that holds cached chaos configuration.
     */
    private class ChaosConnection extends AbstractConnection {
        private final ChaosConfig config;

        public ChaosConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new ChaosConfig(url);
        }

        public ChaosConfig getConfig() {
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
     * Base chaos behavior shared by all statement types.
     */
    private static void introduceChaos(ChaosConfig config, Connection conn) throws SQLException {
        Random random = config.getRandom();

        // Check for connection drop
        if (config.getConnectionDropRate() > 0 && random.nextDouble() < config.getConnectionDropRate()) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
            throw new SQLException("ChaosDriver: Connection dropped unexpectedly");
        }

        // Check for failure
        if (config.getFailureRate() > 0 && random.nextDouble() < config.getFailureRate()) {
            throw new SQLException(config.getExceptionMessage());
        }

        // Introduce latency
        int totalLatency = config.getLatency();
        if (config.getLatencyVariance() > 0) {
            totalLatency += random.nextInt(config.getLatencyVariance());
        }
        if (totalLatency > 0) {
            try {
                Thread.sleep(totalLatency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("ChaosDriver: Interrupted during latency injection", e);
            }
        }
    }

    /**
     * Statement wrapper that injects chaos on execute methods.
     */
    private static class ChaosStatement extends AbstractStatement {
        private final ChaosConfig config;

        public ChaosStatement(Statement delegate, Connection conn, ChaosConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            introduceChaos(config, getConnection());
            return super.execute(sql);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            introduceChaos(config, getConnection());
            ResultSet rs = super.executeQuery(sql);
            return new ChaosResultSet(this, rs, config);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeUpdate(sql);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeBatch();
        }
    }

    /**
     * PreparedStatement wrapper that injects chaos on execute methods.
     */
    private static class ChaosPreparedStatement extends AbstractPreparedStatement {
        private final ChaosConfig config;

        public ChaosPreparedStatement(PreparedStatement delegate, Connection conn, ChaosConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        public boolean execute() throws SQLException {
            introduceChaos(config, getConnection());
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            introduceChaos(config, getConnection());
            ResultSet rs = super.executeQuery();
            return new ChaosResultSet(this, rs, config);
        }

        @Override
        public int executeUpdate() throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeBatch();
        }
    }

    /**
     * CallableStatement wrapper that injects chaos on execute methods.
     */
    private static class ChaosCallableStatement extends AbstractCallableStatement {
        private final ChaosConfig config;

        public ChaosCallableStatement(CallableStatement delegate, Connection conn, ChaosConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        @Override
        public boolean execute() throws SQLException {
            introduceChaos(config, getConnection());
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            introduceChaos(config, getConnection());
            ResultSet rs = super.executeQuery();
            return new ChaosResultSet(this, rs, config);
        }

        @Override
        public int executeUpdate() throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            introduceChaos(config, getConnection());
            return super.executeBatch();
        }
    }

    /**
     * ResultSet wrapper that can inject latency on iteration.
     */
    private static class ChaosResultSet extends AbstractResultSet {
        private final ChaosConfig config;

        public ChaosResultSet(Statement stmt, ResultSet delegate, ChaosConfig config) throws SQLException {
            super(stmt, delegate);
            this.config = config;
        }

        @Override
        public boolean next() throws SQLException {
            if (config.getResultSetLatency() > 0) {
                try {
                    Thread.sleep(config.getResultSetLatency());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("ChaosDriver: Interrupted during result set iteration", e);
                }
            }
            return super.next();
        }
    }
}
