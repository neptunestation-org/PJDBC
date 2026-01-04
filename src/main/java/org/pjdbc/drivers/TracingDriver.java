package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * TracingDriver provides distributed tracing for JDBC operations.
 *
 * <p>URL format: {@code jdbc:trace[param=value,...]:jdbc:target:...}
 *
 * <p>The driver uses a pluggable JdbcTracer interface. Register a tracer using:
 * {@code TracingDriver.setTracer("mytracer", myTracerInstance);}
 *
 * <p>A default in-memory tracer is available for testing via:
 * {@code TracingDriver.getDefaultTracer().getSpans();}
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:trace:jdbc:postgresql://localhost/mydb
 * jdbc:trace[tracerName=myapp,includeSql=true]:jdbc:postgresql://localhost/mydb
 * jdbc:trace[spanPrefix=sql.,includeParams=true]:jdbc:mysql://localhost/db
 * </pre>
 */
@DriverCapability(
    prefix = "trace",
    description = "Provides distributed tracing for JDBC operations",
    capabilities = {"tracing"}
)
@DriverParameter(name = "tracerName", type = ParameterType.STRING,
    description = "Registered tracer name", defaultValue = "jdbc")
@DriverParameter(name = "spanPrefix", type = ParameterType.STRING,
    description = "Prefix for span names", defaultValue = "db.")
@DriverParameter(name = "includeSql", type = ParameterType.BOOLEAN,
    description = "Include SQL in spans", defaultValue = "true")
@DriverParameter(name = "includeParams", type = ParameterType.BOOLEAN,
    description = "Include parameter values", defaultValue = "false")
@DriverParameter(name = "includeRowCount", type = ParameterType.BOOLEAN,
    description = "Include row counts", defaultValue = "true")
@DriverSideEffects(stateful = true)
public class TracingDriver extends AbstractProxyDriver {

    private static final Map<String, JdbcTracer> tracers = new ConcurrentHashMap<>();
    private static final InMemoryTracer defaultTracer = new InMemoryTracer();

    static {
        try {
            DriverManager.registerDriver(new TracingDriver());
            tracers.put("jdbc", defaultTracer);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Register a custom tracer implementation.
     */
    public static void setTracer(String name, JdbcTracer tracer) {
        tracers.put(name, tracer);
    }

    /**
     * Get the default in-memory tracer for testing.
     */
    public static InMemoryTracer getDefaultTracer() {
        return defaultTracer;
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "trace".equals(subprotocol);
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
        return new TracingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        TracingConnection traceConn = (TracingConnection) conn;
        return new TracingStatement(delegate, conn, traceConn.getConfig(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        TracingConnection traceConn = (TracingConnection) conn;
        return new TracingPreparedStatement(delegate, conn, traceConn.getConfig(), traceConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        TracingConnection traceConn = (TracingConnection) conn;
        return new TracingCallableStatement(delegate, conn, traceConn.getConfig(), traceConn.getCurrentSql(), this);
    }

    /**
     * Interface for tracing implementations.
     */
    public interface JdbcTracer {
        /**
         * Start a new span for a database operation.
         * @param operationName The operation name (e.g., "db.query", "db.update")
         * @return A span that should be closed when the operation completes
         */
        Span startSpan(String operationName);
    }

    /**
     * Represents a tracing span.
     */
    public interface Span {
        /** Set an attribute on the span */
        Span setAttribute(String key, String value);
        /** Set a numeric attribute on the span */
        Span setAttribute(String key, long value);
        /** Mark the span as failed with an exception */
        Span setError(Throwable t);
        /** End the span */
        void end();
    }

    /**
     * Configuration holder for tracing parameters.
     */
    public static class TracingConfig {
        private final String tracerName;
        private final String spanPrefix;
        private final boolean includeSql;
        private final boolean includeParams;
        private final boolean includeRowCount;

        public TracingConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.tracerName = parser.getParameter("tracerName", "jdbc");
            this.spanPrefix = parser.getParameter("spanPrefix", "db.");
            this.includeSql = parseBoolean(parser.getParameter("includeSql", "true"));
            this.includeParams = parseBoolean(parser.getParameter("includeParams", "false"));
            this.includeRowCount = parseBoolean(parser.getParameter("includeRowCount", "true"));
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public String getTracerName() { return tracerName; }
        public String getSpanPrefix() { return spanPrefix; }
        public boolean isIncludeSql() { return includeSql; }
        public boolean isIncludeParams() { return includeParams; }
        public boolean isIncludeRowCount() { return includeRowCount; }

        public JdbcTracer getTracer() {
            JdbcTracer tracer = tracers.get(tracerName);
            return tracer != null ? tracer : defaultTracer;
        }
    }

    /**
     * In-memory tracer for testing and debugging.
     */
    public static class InMemoryTracer implements JdbcTracer {
        private final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public Span startSpan(String operationName) {
            return new InMemorySpan(operationName, spans);
        }

        public List<SpanData> getSpans() {
            return Collections.unmodifiableList(new ArrayList<>(spans));
        }

        public void clear() {
            spans.clear();
        }

        public int getSpanCount() {
            return spans.size();
        }
    }

    /**
     * Span data captured by InMemoryTracer.
     */
    public static class SpanData {
        private final String operationName;
        private final Map<String, Object> attributes;
        private final long startTimeNanos;
        private final long endTimeNanos;
        private final long durationMs;
        private final Throwable error;

        public SpanData(String operationName, Map<String, Object> attributes,
                       long startTimeNanos, long endTimeNanos, Throwable error) {
            this.operationName = operationName;
            this.attributes = Collections.unmodifiableMap(attributes);
            this.startTimeNanos = startTimeNanos;
            this.endTimeNanos = endTimeNanos;
            this.durationMs = (endTimeNanos - startTimeNanos) / 1_000_000;
            this.error = error;
        }

        public String getOperationName() { return operationName; }
        public Map<String, Object> getAttributes() { return attributes; }
        public long getStartTimeNanos() { return startTimeNanos; }
        public long getEndTimeNanos() { return endTimeNanos; }
        public long getDurationMs() { return durationMs; }
        public Throwable getError() { return error; }
        public boolean hasError() { return error != null; }

        public String getAttribute(String key) {
            Object val = attributes.get(key);
            return val != null ? val.toString() : null;
        }
    }

    /**
     * In-memory span implementation.
     */
    private static class InMemorySpan implements Span {
        private final String operationName;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final long startTimeNanos;
        private final List<SpanData> spans;
        private Throwable error;

        public InMemorySpan(String operationName, List<SpanData> spans) {
            this.operationName = operationName;
            this.startTimeNanos = System.nanoTime();
            this.spans = spans;
        }

        @Override
        public Span setAttribute(String key, String value) {
            if (value != null) attributes.put(key, value);
            return this;
        }

        @Override
        public Span setAttribute(String key, long value) {
            attributes.put(key, value);
            return this;
        }

        @Override
        public Span setError(Throwable t) {
            this.error = t;
            attributes.put("error", "true");
            if (t != null) {
                attributes.put("error.type", t.getClass().getName());
                attributes.put("error.message", t.getMessage());
            }
            return this;
        }

        @Override
        public void end() {
            long endTimeNanos = System.nanoTime();
            spans.add(new SpanData(operationName, attributes, startTimeNanos, endTimeNanos, error));
        }
    }

    /**
     * Connection wrapper that holds tracing configuration.
     */
    private class TracingConnection extends AbstractConnection {
        private final TracingConfig config;
        private String currentSql;

        public TracingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new TracingConfig(url);
        }

        public TracingConfig getConfig() { return config; }

        public String getCurrentSql() { return currentSql; }
        public void setCurrentSql(String sql) { this.currentSql = sql; }

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
     * Statement wrapper that traces query execution.
     */
    private class TracingStatement extends AbstractStatement {
        private final TracingConfig config;
        private final TracingDriver driver;

        public TracingStatement(Statement delegate, Connection conn, TracingConfig config, TracingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.driver = driver;
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            return driver.proxyResultSet(this, r);
        }

        private Span startSpan(String operation, String sql) {
            Span span = config.getTracer().startSpan(config.getSpanPrefix() + operation);
            span.setAttribute("db.system", "jdbc");
            span.setAttribute("db.operation", operation);
            if (config.isIncludeSql() && sql != null) {
                span.setAttribute("db.statement", sql);
            }
            return span;
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            Span span = startSpan("query", sql);
            try {
                ResultSet rs = super.executeQuery(sql);
                return rs;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            Span span = startSpan("update", sql);
            try {
                int rows = super.executeUpdate(sql);
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            Span span = startSpan("update", sql);
            try {
                int rows = super.executeUpdate(sql, autoGeneratedKeys);
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            Span span = startSpan("update", sql);
            try {
                int rows = super.executeUpdate(sql, columnIndexes);
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            Span span = startSpan("update", sql);
            try {
                int rows = super.executeUpdate(sql, columnNames);
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            Span span = startSpan("execute", sql);
            try {
                return super.execute(sql);
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            Span span = startSpan("execute", sql);
            try {
                return super.execute(sql, autoGeneratedKeys);
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            Span span = startSpan("execute", sql);
            try {
                return super.execute(sql, columnIndexes);
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            Span span = startSpan("execute", sql);
            try {
                return super.execute(sql, columnNames);
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            Span span = startSpan("batch", null);
            try {
                int[] results = super.executeBatch();
                if (config.isIncludeRowCount()) {
                    int total = 0;
                    for (int r : results) {
                        if (r >= 0) total += r;
                    }
                    span.setAttribute("db.batch_size", results.length);
                    span.setAttribute("db.rows_affected", total);
                }
                return results;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }
    }

    /**
     * PreparedStatement wrapper that traces execution with parameter support.
     */
    private class TracingPreparedStatement extends AbstractPreparedStatement {
        private final TracingConfig config;
        private final String sql;
        private final TracingDriver driver;
        private final Map<Integer, Object> parameters = new ConcurrentHashMap<>();

        public TracingPreparedStatement(PreparedStatement delegate, Connection conn, TracingConfig config, String sql, TracingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
            this.driver = driver;
        }

        private Span startSpan(String operation) {
            Span span = config.getTracer().startSpan(config.getSpanPrefix() + operation);
            span.setAttribute("db.system", "jdbc");
            span.setAttribute("db.operation", operation);
            if (config.isIncludeSql() && sql != null) {
                span.setAttribute("db.statement", sql);
            }
            if (config.isIncludeParams() && !parameters.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Integer, Object> e : parameters.entrySet()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
                span.setAttribute("db.parameters", sb.toString());
            }
            return span;
        }

        // Track parameters for tracing
        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setObject(parameterIndex, x);
        }

        @Override
        public void setString(int parameterIndex, String x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setString(parameterIndex, x);
        }

        @Override
        public void setInt(int parameterIndex, int x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setInt(parameterIndex, x);
        }

        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setLong(parameterIndex, x);
        }

        @Override
        public void clearParameters() throws SQLException {
            parameters.clear();
            super.clearParameters();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            Span span = startSpan("query");
            try {
                return super.executeQuery();
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            Span span = startSpan("update");
            try {
                int rows = super.executeUpdate();
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute() throws SQLException {
            Span span = startSpan("execute");
            try {
                return super.execute();
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            Span span = startSpan("batch");
            try {
                int[] results = super.executeBatch();
                if (config.isIncludeRowCount()) {
                    int total = 0;
                    for (int r : results) {
                        if (r >= 0) total += r;
                    }
                    span.setAttribute("db.batch_size", results.length);
                    span.setAttribute("db.rows_affected", total);
                }
                return results;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }
    }

    /**
     * CallableStatement wrapper that traces stored procedure execution.
     */
    private class TracingCallableStatement extends AbstractCallableStatement {
        private final TracingConfig config;
        private final String sql;
        private final TracingDriver driver;

        public TracingCallableStatement(CallableStatement delegate, Connection conn, TracingConfig config, String sql, TracingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
            this.driver = driver;
        }

        private Span startSpan(String operation) {
            Span span = config.getTracer().startSpan(config.getSpanPrefix() + operation);
            span.setAttribute("db.system", "jdbc");
            span.setAttribute("db.operation", operation);
            if (config.isIncludeSql() && sql != null) {
                span.setAttribute("db.statement", sql);
            }
            return span;
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            Span span = startSpan("call");
            try {
                return super.executeQuery();
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            Span span = startSpan("call");
            try {
                int rows = super.executeUpdate();
                if (config.isIncludeRowCount()) {
                    span.setAttribute("db.rows_affected", rows);
                }
                return rows;
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }

        @Override
        public boolean execute() throws SQLException {
            Span span = startSpan("call");
            try {
                return super.execute();
            } catch (SQLException e) {
                span.setError(e);
                throw e;
            } finally {
                span.end();
            }
        }
    }
}
