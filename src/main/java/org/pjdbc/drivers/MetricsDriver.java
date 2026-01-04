package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.JdbcUrlParser;

/**
 * MetricsDriver collects detailed performance metrics for JDBC operations.
 *
 * URL format: jdbc:metrics[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   slowThreshold  - Threshold in ms for slow queries (default: 1000)
 *   enabled        - Enable metrics collection (default: true)
 *   trackByType    - Track metrics by operation type (default: true)
 *
 * Metrics collected:
 *   - Total query/update/execute count
 *   - Execution time (min, max, avg, total)
 *   - Error count
 *   - Slow query count
 *   - Rows affected (for updates)
 *   - Active connections
 *
 * Example URLs:
 *   jdbc:metrics:jdbc:postgresql://localhost/mydb
 *   jdbc:metrics[slowThreshold=500]:jdbc:postgresql://localhost/mydb
 *   jdbc:metrics[trackByType=true]:jdbc:mysql://localhost/db
 */
@DriverCapability(
    prefix = "metrics",
    description = "Collects performance metrics for JDBC operations",
    capabilities = {"metrics"}
)
@DriverParameter(name = "enabled", type = ParameterType.BOOLEAN,
    description = "Enable metrics collection", defaultValue = "true")
@DriverParameter(name = "slowThreshold", type = ParameterType.INTEGER,
    description = "Threshold in ms for slow query detection", defaultValue = "1000", min = 0)
@DriverParameter(name = "trackByType", type = ParameterType.BOOLEAN,
    description = "Track metrics by operation type", defaultValue = "true")
@DriverSideEffects(metrics = true, stateful = true)
public class MetricsDriver extends AbstractProxyDriver {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "^\\s*INSERT\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
        "^\\s*UPDATE\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DELETE_PATTERN = Pattern.compile(
        "^\\s*DELETE\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Metrics globalMetrics = new Metrics();

    static {
        try {
            DriverManager.registerDriver(new MetricsDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get global metrics across all connections.
     */
    public static Metrics getGlobalMetrics() {
        return globalMetrics;
    }

    /**
     * Get metrics for a specific connection.
     * Returns null if the connection is not a metrics connection.
     */
    public static Metrics getMetrics(Connection conn) {
        if (conn instanceof MetricsConnection mc) {
            return mc.getMetrics();
        }
        return null;
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "metrics".equals(subprotocol);
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
        return new MetricsConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        MetricsConnection metricsConn = (MetricsConnection) conn;
        return new MetricsStatement(delegate, conn, metricsConn.getMetrics(), metricsConn.getConfig(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        MetricsConnection metricsConn = (MetricsConnection) conn;
        return new MetricsPreparedStatement(delegate, conn, metricsConn.getMetrics(), metricsConn.getConfig(), metricsConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        MetricsConnection metricsConn = (MetricsConnection) conn;
        return new MetricsCallableStatement(delegate, conn, metricsConn.getMetrics(), metricsConn.getConfig(), this);
    }

    /**
     * Operation type for categorization.
     */
    public enum OperationType {
        SELECT, INSERT, UPDATE, DELETE, OTHER, CALL, BATCH
    }

    /**
     * Configuration for metrics collection.
     */
    public static class MetricsConfig {
        private final long slowThresholdMs;
        private final boolean enabled;
        private final boolean trackByType;

        public MetricsConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.slowThresholdMs = parseLong(parser.getParameter("slowThreshold", "1000"));
            this.enabled = parseBoolean(parser.getParameter("enabled", "true"));
            this.trackByType = parseBoolean(parser.getParameter("trackByType", "true"));
        }

        private static long parseLong(String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 1000; }
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public long getSlowThresholdMs() { return slowThresholdMs; }
        public boolean isEnabled() { return enabled; }
        public boolean isTrackByType() { return trackByType; }
    }

    /**
     * Metrics data container.
     */
    public static class Metrics {
        // Counters
        private final AtomicLong totalQueries = new AtomicLong(0);
        private final AtomicLong totalUpdates = new AtomicLong(0);
        private final AtomicLong totalExecutes = new AtomicLong(0);
        private final AtomicLong totalBatches = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong slowQueries = new AtomicLong(0);
        private final AtomicLong totalRowsAffected = new AtomicLong(0);

        // Timing
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);

        // By type metrics
        private final Map<OperationType, TypeMetrics> byType = new ConcurrentHashMap<>();

        // Connections
        private final AtomicLong activeConnections = new AtomicLong(0);
        private final AtomicLong totalConnections = new AtomicLong(0);

        public Metrics() {
            for (OperationType type : OperationType.values()) {
                byType.put(type, new TypeMetrics());
            }
        }

        public void recordQuery(long durationNanos, boolean slow, OperationType type) {
            totalQueries.incrementAndGet();
            recordTiming(durationNanos, slow);
            if (type != null) {
                byType.get(type).recordOperation(durationNanos);
            }
        }

        public void recordUpdate(long durationNanos, int rowsAffected, boolean slow, OperationType type) {
            totalUpdates.incrementAndGet();
            totalRowsAffected.addAndGet(rowsAffected);
            recordTiming(durationNanos, slow);
            if (type != null) {
                byType.get(type).recordOperation(durationNanos);
                byType.get(type).addRowsAffected(rowsAffected);
            }
        }

        public void recordExecute(long durationNanos, boolean slow, OperationType type) {
            totalExecutes.incrementAndGet();
            recordTiming(durationNanos, slow);
            if (type != null) {
                byType.get(type).recordOperation(durationNanos);
            }
        }

        public void recordBatch(long durationNanos, int[] results, boolean slow) {
            totalBatches.incrementAndGet();
            int total = 0;
            for (int r : results) {
                if (r >= 0) total += r;
            }
            totalRowsAffected.addAndGet(total);
            recordTiming(durationNanos, slow);
            byType.get(OperationType.BATCH).recordOperation(durationNanos);
            byType.get(OperationType.BATCH).addRowsAffected(total);
        }

        public void recordError() {
            totalErrors.incrementAndGet();
        }

        private void recordTiming(long durationNanos, boolean slow) {
            totalTimeNanos.addAndGet(durationNanos);
            if (slow) slowQueries.incrementAndGet();

            // Update min
            long currentMin;
            do {
                currentMin = minTimeNanos.get();
                if (durationNanos >= currentMin) break;
            } while (!minTimeNanos.compareAndSet(currentMin, durationNanos));

            // Update max
            long currentMax;
            do {
                currentMax = maxTimeNanos.get();
                if (durationNanos <= currentMax) break;
            } while (!maxTimeNanos.compareAndSet(currentMax, durationNanos));
        }

        public void connectionOpened() {
            activeConnections.incrementAndGet();
            totalConnections.incrementAndGet();
        }

        public void connectionClosed() {
            activeConnections.decrementAndGet();
        }

        // Getters
        public long getTotalQueries() { return totalQueries.get(); }
        public long getTotalUpdates() { return totalUpdates.get(); }
        public long getTotalExecutes() { return totalExecutes.get(); }
        public long getTotalBatches() { return totalBatches.get(); }
        public long getTotalOperations() {
            return totalQueries.get() + totalUpdates.get() + totalExecutes.get() + totalBatches.get();
        }
        public long getTotalErrors() { return totalErrors.get(); }
        public long getSlowQueries() { return slowQueries.get(); }
        public long getTotalRowsAffected() { return totalRowsAffected.get(); }

        public long getTotalTimeMs() { return totalTimeNanos.get() / 1_000_000; }
        public long getMinTimeMs() {
            long min = minTimeNanos.get();
            return min == Long.MAX_VALUE ? 0 : min / 1_000_000;
        }
        public long getMaxTimeMs() { return maxTimeNanos.get() / 1_000_000; }
        public double getAvgTimeMs() {
            long total = getTotalOperations();
            return total == 0 ? 0.0 : (double) totalTimeNanos.get() / total / 1_000_000;
        }

        public long getActiveConnections() { return activeConnections.get(); }
        public long getTotalConnections() { return totalConnections.get(); }

        public double getErrorRate() {
            long total = getTotalOperations();
            return total == 0 ? 0.0 : (double) totalErrors.get() / total;
        }

        public double getSlowQueryRate() {
            long total = getTotalOperations();
            return total == 0 ? 0.0 : (double) slowQueries.get() / total;
        }

        public TypeMetrics getTypeMetrics(OperationType type) {
            return byType.get(type);
        }

        public void reset() {
            totalQueries.set(0);
            totalUpdates.set(0);
            totalExecutes.set(0);
            totalBatches.set(0);
            totalErrors.set(0);
            slowQueries.set(0);
            totalRowsAffected.set(0);
            totalTimeNanos.set(0);
            minTimeNanos.set(Long.MAX_VALUE);
            maxTimeNanos.set(0);
            for (TypeMetrics tm : byType.values()) {
                tm.reset();
            }
        }

        @Override
        public String toString() {
            return String.format(
                "Metrics{ops=%d, queries=%d, updates=%d, errors=%d, slow=%d, avgMs=%.2f, maxMs=%d}",
                getTotalOperations(), totalQueries.get(), totalUpdates.get(),
                totalErrors.get(), slowQueries.get(), getAvgTimeMs(), getMaxTimeMs()
            );
        }
    }

    /**
     * Metrics for a specific operation type.
     */
    public static class TypeMetrics {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong rowsAffected = new AtomicLong(0);

        public void recordOperation(long durationNanos) {
            count.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);
        }

        public void addRowsAffected(int rows) {
            rowsAffected.addAndGet(rows);
        }

        public long getCount() { return count.get(); }
        public long getTotalTimeMs() { return totalTimeNanos.get() / 1_000_000; }
        public double getAvgTimeMs() {
            long c = count.get();
            return c == 0 ? 0.0 : (double) totalTimeNanos.get() / c / 1_000_000;
        }
        public long getRowsAffected() { return rowsAffected.get(); }

        public void reset() {
            count.set(0);
            totalTimeNanos.set(0);
            rowsAffected.set(0);
        }
    }

    /**
     * Determine operation type from SQL.
     */
    private static OperationType getOperationType(String sql) {
        if (sql == null) return OperationType.OTHER;
        if (SELECT_PATTERN.matcher(sql).find()) return OperationType.SELECT;
        if (INSERT_PATTERN.matcher(sql).find()) return OperationType.INSERT;
        if (UPDATE_PATTERN.matcher(sql).find()) return OperationType.UPDATE;
        if (DELETE_PATTERN.matcher(sql).find()) return OperationType.DELETE;
        return OperationType.OTHER;
    }

    /**
     * Connection wrapper that holds metrics.
     */
    private class MetricsConnection extends AbstractConnection {
        private final Metrics metrics;
        private final MetricsConfig config;
        private String currentSql;

        public MetricsConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new MetricsConfig(url);
            this.metrics = new Metrics();
            this.metrics.connectionOpened();
            globalMetrics.connectionOpened();
        }

        public Metrics getMetrics() { return metrics; }
        public MetricsConfig getConfig() { return config; }

        public String getCurrentSql() { return currentSql; }
        public void setCurrentSql(String sql) { this.currentSql = sql; }

        @Override
        public void close() throws SQLException {
            metrics.connectionClosed();
            globalMetrics.connectionClosed();
            super.close();
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
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, autoGeneratedKeys), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnIndexes), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnNames), this);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }
    }

    /**
     * Statement wrapper that collects metrics.
     */
    private class MetricsStatement extends AbstractStatement {
        private final Metrics metrics;
        private final MetricsConfig config;
        private final MetricsDriver driver;

        public MetricsStatement(Statement delegate, Connection conn, Metrics metrics, MetricsConfig config, MetricsDriver driver) throws SQLException {
            super(delegate, conn);
            this.metrics = metrics;
            this.config = config;
            this.driver = driver;
        }

        private void record(long startNanos, String sql, boolean isQuery, int rowsAffected) {
            if (!config.isEnabled()) return;
            long duration = System.nanoTime() - startNanos;
            boolean slow = duration / 1_000_000 >= config.getSlowThresholdMs();
            OperationType type = config.isTrackByType() ? getOperationType(sql) : null;

            if (isQuery) {
                metrics.recordQuery(duration, slow, type);
                globalMetrics.recordQuery(duration, slow, type);
            } else if (rowsAffected >= 0) {
                metrics.recordUpdate(duration, rowsAffected, slow, type);
                globalMetrics.recordUpdate(duration, rowsAffected, slow, type);
            } else {
                metrics.recordExecute(duration, slow, type);
                globalMetrics.recordExecute(duration, slow, type);
            }
        }

        private void recordError() {
            if (!config.isEnabled()) return;
            metrics.recordError();
            globalMetrics.recordError();
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            long start = System.nanoTime();
            try {
                ResultSet rs = super.executeQuery(sql);
                record(start, sql, true, -1);
                return rs;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate(sql);
                record(start, sql, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate(sql, autoGeneratedKeys);
                record(start, sql, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate(sql, columnIndexes);
                record(start, sql, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate(sql, columnNames);
                record(start, sql, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute(sql);
                record(start, sql, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute(sql, autoGeneratedKeys);
                record(start, sql, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute(sql, columnIndexes);
                record(start, sql, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute(sql, columnNames);
                record(start, sql, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            long start = System.nanoTime();
            try {
                int[] results = super.executeBatch();
                if (config.isEnabled()) {
                    long duration = System.nanoTime() - start;
                    boolean slow = duration / 1_000_000 >= config.getSlowThresholdMs();
                    metrics.recordBatch(duration, results, slow);
                    globalMetrics.recordBatch(duration, results, slow);
                }
                return results;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }
    }

    /**
     * PreparedStatement wrapper that collects metrics.
     */
    private class MetricsPreparedStatement extends AbstractPreparedStatement {
        private final Metrics metrics;
        private final MetricsConfig config;
        private final String sql;
        private final MetricsDriver driver;

        public MetricsPreparedStatement(PreparedStatement delegate, Connection conn, Metrics metrics, MetricsConfig config, String sql, MetricsDriver driver) throws SQLException {
            super(delegate, conn);
            this.metrics = metrics;
            this.config = config;
            this.sql = sql;
            this.driver = driver;
        }

        private void record(long startNanos, boolean isQuery, int rowsAffected) {
            if (!config.isEnabled()) return;
            long duration = System.nanoTime() - startNanos;
            boolean slow = duration / 1_000_000 >= config.getSlowThresholdMs();
            OperationType type = config.isTrackByType() ? getOperationType(sql) : null;

            if (isQuery) {
                metrics.recordQuery(duration, slow, type);
                globalMetrics.recordQuery(duration, slow, type);
            } else if (rowsAffected >= 0) {
                metrics.recordUpdate(duration, rowsAffected, slow, type);
                globalMetrics.recordUpdate(duration, rowsAffected, slow, type);
            } else {
                metrics.recordExecute(duration, slow, type);
                globalMetrics.recordExecute(duration, slow, type);
            }
        }

        private void recordError() {
            if (!config.isEnabled()) return;
            metrics.recordError();
            globalMetrics.recordError();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.nanoTime();
            try {
                ResultSet rs = super.executeQuery();
                record(start, true, -1);
                return rs;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate();
                record(start, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute();
                record(start, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            long start = System.nanoTime();
            try {
                int[] results = super.executeBatch();
                if (config.isEnabled()) {
                    long duration = System.nanoTime() - start;
                    boolean slow = duration / 1_000_000 >= config.getSlowThresholdMs();
                    metrics.recordBatch(duration, results, slow);
                    globalMetrics.recordBatch(duration, results, slow);
                }
                return results;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }
    }

    /**
     * CallableStatement wrapper that collects metrics.
     */
    private class MetricsCallableStatement extends AbstractCallableStatement {
        private final Metrics metrics;
        private final MetricsConfig config;
        private final MetricsDriver driver;

        public MetricsCallableStatement(CallableStatement delegate, Connection conn, Metrics metrics, MetricsConfig config, MetricsDriver driver) throws SQLException {
            super(delegate, conn);
            this.metrics = metrics;
            this.config = config;
            this.driver = driver;
        }

        private void record(long startNanos, boolean isQuery, int rowsAffected) {
            if (!config.isEnabled()) return;
            long duration = System.nanoTime() - startNanos;
            boolean slow = duration / 1_000_000 >= config.getSlowThresholdMs();

            if (isQuery) {
                metrics.recordQuery(duration, slow, OperationType.CALL);
                globalMetrics.recordQuery(duration, slow, OperationType.CALL);
            } else if (rowsAffected >= 0) {
                metrics.recordUpdate(duration, rowsAffected, slow, OperationType.CALL);
                globalMetrics.recordUpdate(duration, rowsAffected, slow, OperationType.CALL);
            } else {
                metrics.recordExecute(duration, slow, OperationType.CALL);
                globalMetrics.recordExecute(duration, slow, OperationType.CALL);
            }
        }

        private void recordError() {
            if (!config.isEnabled()) return;
            metrics.recordError();
            globalMetrics.recordError();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.nanoTime();
            try {
                ResultSet rs = super.executeQuery();
                record(start, true, -1);
                return rs;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.nanoTime();
            try {
                int rows = super.executeUpdate();
                record(start, false, rows);
                return rows;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.nanoTime();
            try {
                boolean result = super.execute();
                record(start, false, -1);
                return result;
            } catch (SQLException e) {
                recordError();
                throw e;
            }
        }
    }
}
