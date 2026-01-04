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
import java.util.concurrent.atomic.AtomicLong;

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

/**
 * RateLimitDriver limits the rate of database queries using a token bucket algorithm.
 *
 * This driver prevents overwhelming the database by enforcing a maximum number
 * of requests per time window. It can either reject excess requests immediately
 * or block until capacity becomes available.
 *
 * URL format: jdbc:ratelimit[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   maxRequests  - Maximum requests per window (default: 100)
 *   windowMs     - Time window in milliseconds (default: 1000)
 *   mode         - "reject" to fail fast, "wait" to block (default: reject)
 *   burstSize    - Maximum burst capacity (default: maxRequests)
 *
 * Example URLs:
 *   jdbc:ratelimit:jdbc:postgresql://localhost/mydb
 *   jdbc:ratelimit[maxRequests=50,windowMs=1000]:jdbc:postgresql://localhost/mydb
 *   jdbc:ratelimit[maxRequests=10,mode=wait]:jdbc:mysql://localhost/db
 *
 * Note: Rate limiting is per-connection. For application-wide limiting,
 * use a shared RateLimiter instance.
 */
@DriverCapability(
    prefix = "ratelimit",
    description = "Limits query rate using token bucket algorithm",
    capabilities = {"resilience"}
)
@DriverParameter(name = "maxRequests", type = ParameterType.INTEGER,
    description = "Maximum requests per window", defaultValue = "100", min = 1)
@DriverParameter(name = "windowMs", type = ParameterType.INTEGER,
    description = "Time window in milliseconds", defaultValue = "1000", min = 1)
@DriverParameter(name = "mode", type = ParameterType.STRING,
    description = "Behavior when limit exceeded: reject or wait", defaultValue = "reject",
    enumValues = {"reject", "wait"})
@DriverParameter(name = "burstSize", type = ParameterType.INTEGER,
    description = "Maximum burst capacity", defaultValue = "100", min = 1)
@DriverSideEffects(stateful = true)
public class RateLimitDriver extends AbstractProxyDriver {

    public enum Mode {
        REJECT,  // Fail immediately when rate limit exceeded
        WAIT     // Block until capacity is available
    }

    static {
        try {
            DriverManager.registerDriver(new RateLimitDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "ratelimit".equals(subprotocol);
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
        return new RateLimitConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        RateLimitConnection rlConn = (RateLimitConnection) conn;
        return new RateLimitStatement(delegate, conn, rlConn.getRateLimiter());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        RateLimitConnection rlConn = (RateLimitConnection) conn;
        return new RateLimitPreparedStatement(delegate, conn, rlConn.getRateLimiter());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        RateLimitConnection rlConn = (RateLimitConnection) conn;
        return new RateLimitCallableStatement(delegate, conn, rlConn.getRateLimiter());
    }

    /**
     * Token bucket rate limiter implementation.
     * Thread-safe using atomic operations.
     */
    public static class RateLimiter {
        private final int maxRequests;
        private final long windowMs;
        private final Mode mode;
        private final int burstSize;

        private final AtomicLong tokens;
        private final AtomicLong lastRefillTime;

        // Statistics
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong rejectedRequests = new AtomicLong(0);
        private final AtomicLong waitedRequests = new AtomicLong(0);

        public RateLimiter(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.maxRequests = parseInt(parser.getParameter("maxRequests", "100"));
            this.windowMs = parseLong(parser.getParameter("windowMs", "1000"));
            this.mode = parseMode(parser.getParameter("mode", "reject"));
            int configuredBurst = parseInt(parser.getParameter("burstSize", String.valueOf(maxRequests)));
            this.burstSize = Math.max(configuredBurst, maxRequests);

            this.tokens = new AtomicLong(burstSize);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        // Constructor for testing
        public RateLimiter(int maxRequests, long windowMs, Mode mode, int burstSize) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
            this.mode = mode;
            this.burstSize = Math.max(burstSize, maxRequests);

            this.tokens = new AtomicLong(this.burstSize);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        private static int parseInt(String s) {
            try {
                int val = Integer.parseInt(s);
                return val > 0 ? val : 100;
            } catch (NumberFormatException e) {
                return 100;
            }
        }

        private static long parseLong(String s) {
            try {
                long val = Long.parseLong(s);
                return val > 0 ? val : 1000;
            } catch (NumberFormatException e) {
                return 1000;
            }
        }

        private static Mode parseMode(String s) {
            if ("wait".equalsIgnoreCase(s)) {
                return Mode.WAIT;
            }
            return Mode.REJECT;
        }

        /**
         * Refill tokens based on elapsed time.
         */
        private void refill() {
            long now = System.currentTimeMillis();
            long last = lastRefillTime.get();
            long elapsed = now - last;

            if (elapsed >= windowMs) {
                // Calculate how many windows have passed
                long windows = elapsed / windowMs;
                long tokensToAdd = windows * maxRequests;

                // Try to update atomically
                if (lastRefillTime.compareAndSet(last, last + (windows * windowMs))) {
                    long currentTokens = tokens.get();
                    long newTokens = Math.min(currentTokens + tokensToAdd, burstSize);
                    tokens.set(newTokens);
                }
            }
        }

        /**
         * Try to acquire a permit. Returns true if acquired, false if rate limit exceeded.
         */
        public boolean tryAcquire() {
            refill();

            while (true) {
                long current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                // CAS failed, retry
            }
        }

        /**
         * Acquire a permit, potentially blocking or throwing based on mode.
         */
        public void acquire() throws SQLException {
            totalRequests.incrementAndGet();

            if (tryAcquire()) {
                return;
            }

            if (mode == Mode.REJECT) {
                rejectedRequests.incrementAndGet();
                throw new SQLException(
                    String.format("Rate limit exceeded: max %d requests per %dms. " +
                                  "Total: %d, Rejected: %d",
                                  maxRequests, windowMs,
                                  totalRequests.get(), rejectedRequests.get()),
                    "08000"  // Connection exception
                );
            }

            // WAIT mode - block until we can acquire
            waitedRequests.incrementAndGet();
            while (!tryAcquire()) {
                try {
                    // Wait for a fraction of the window before retrying
                    long waitTime = Math.max(1, windowMs / maxRequests);
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("RateLimitDriver: Interrupted while waiting for rate limit", e);
                }
            }
        }

        // Getters for configuration and statistics
        public int getMaxRequests() { return maxRequests; }
        public long getWindowMs() { return windowMs; }
        public Mode getMode() { return mode; }
        public int getBurstSize() { return burstSize; }
        public long getAvailableTokens() { refill(); return tokens.get(); }
        public long getTotalRequests() { return totalRequests.get(); }
        public long getRejectedRequests() { return rejectedRequests.get(); }
        public long getWaitedRequests() { return waitedRequests.get(); }

        /**
         * Reset the rate limiter to initial state.
         */
        public void reset() {
            tokens.set(burstSize);
            lastRefillTime.set(System.currentTimeMillis());
            totalRequests.set(0);
            rejectedRequests.set(0);
            waitedRequests.set(0);
        }

        @Override
        public String toString() {
            return String.format("RateLimiter[max=%d/%dms, mode=%s, burst=%d, tokens=%d]",
                maxRequests, windowMs, mode, burstSize, tokens.get());
        }
    }

    /**
     * Connection wrapper that holds the rate limiter.
     */
    private class RateLimitConnection extends AbstractConnection {
        private final RateLimiter rateLimiter;

        public RateLimitConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.rateLimiter = new RateLimiter(url);
        }

        public RateLimiter getRateLimiter() {
            return rateLimiter;
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
     * Statement wrapper that enforces rate limiting.
     */
    private static class RateLimitStatement extends AbstractStatement {
        private final RateLimiter rateLimiter;

        public RateLimitStatement(Statement delegate, Connection conn, RateLimiter rateLimiter) throws SQLException {
            super(delegate, conn);
            this.rateLimiter = rateLimiter;
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            Driver driver = ((AbstractConnection)getConnection()).getDriver();
            return ((AbstractProxyDriver)driver).proxyResultSet(this, r);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            rateLimiter.acquire();
            return super.execute(sql);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            rateLimiter.acquire();
            return super.executeQuery(sql);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            rateLimiter.acquire();
            return super.executeUpdate(sql);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate(String sql) throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeUpdate(sql);
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeBatch();
        }
    }

    /**
     * PreparedStatement wrapper that enforces rate limiting.
     */
    private static class RateLimitPreparedStatement extends AbstractPreparedStatement {
        private final RateLimiter rateLimiter;

        public RateLimitPreparedStatement(PreparedStatement delegate, Connection conn, RateLimiter rateLimiter) throws SQLException {
            super(delegate, conn);
            this.rateLimiter = rateLimiter;
        }

        @Override
        public boolean execute() throws SQLException {
            rateLimiter.acquire();
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            rateLimiter.acquire();
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            rateLimiter.acquire();
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate() throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeUpdate();
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeBatch();
        }
    }

    /**
     * CallableStatement wrapper that enforces rate limiting.
     */
    private static class RateLimitCallableStatement extends AbstractCallableStatement {
        private final RateLimiter rateLimiter;

        public RateLimitCallableStatement(CallableStatement delegate, Connection conn, RateLimiter rateLimiter) throws SQLException {
            super(delegate, conn);
            this.rateLimiter = rateLimiter;
        }

        @Override
        public boolean execute() throws SQLException {
            rateLimiter.acquire();
            return super.execute();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            rateLimiter.acquire();
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            rateLimiter.acquire();
            return super.executeUpdate();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeBatch();
        }

        @Override
        public long executeLargeUpdate() throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeUpdate();
        }

        @Override
        public long[] executeLargeBatch() throws SQLException {
            rateLimiter.acquire();
            return super.executeLargeBatch();
        }
    }

    /**
     * Get the rate limiter for a connection.
     * Returns null if connection is not a RateLimitDriver connection.
     */
    public static RateLimiter getRateLimiter(Connection conn) {
        if (conn instanceof RateLimitConnection) {
            return ((RateLimitConnection) conn).getRateLimiter();
        }
        return null;
    }
}
