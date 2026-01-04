package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
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

/**
 * AuditDriver provides compliance audit logging for database operations.
 *
 * This driver logs detailed information about all SQL operations including:
 * - Timestamp of execution
 * - User/connection information
 * - SQL statement executed
 * - Execution time
 * - Rows affected
 * - Success/failure status
 *
 * Useful for compliance requirements (SOX, HIPAA, PCI-DSS, GDPR, etc.)
 *
 * URL format: jdbc:audit[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   logLevel     - Logging level: all, success, failure (default: all)
 *   includeSql   - Include SQL in audit log: true/false (default: true)
 *   includeTime  - Include execution time: true/false (default: true)
 *   includeRows  - Include row counts: true/false (default: true)
 *   name         - Audit log name for identification (default: "audit")
 *
 * Example URLs:
 *   jdbc:audit:jdbc:postgresql://localhost/mydb
 *   jdbc:audit[logLevel=failure]:jdbc:postgresql://localhost/mydb
 *   jdbc:audit[name=payments-db,includeSql=false]:jdbc:mysql://localhost/db
 *
 * Custom audit handlers can be registered via:
 *   AuditDriver.setAuditHandler("myhandler", event -> { ... });
 */
@DriverCapability(
    prefix = "audit",
    description = "Compliance audit logging for database operations",
    capabilities = {"logging", "security"}
)
@DriverParameter(name = "name", type = ParameterType.STRING,
    description = "Audit log name for identification", defaultValue = "audit")
@DriverParameter(name = "logLevel", type = ParameterType.STRING,
    description = "What to log: all, success, or failure", defaultValue = "all",
    enumValues = {"all", "success", "failure"})
@DriverParameter(name = "includeSql", type = ParameterType.BOOLEAN,
    description = "Include SQL statements in audit log", defaultValue = "true")
@DriverParameter(name = "includeTime", type = ParameterType.BOOLEAN,
    description = "Include execution time in audit log", defaultValue = "true")
@DriverParameter(name = "includeRows", type = ParameterType.BOOLEAN,
    description = "Include row counts in audit log", defaultValue = "true")
@DriverSideEffects(logging = true, stateful = true)
public class AuditDriver extends AbstractProxyDriver {

    private static final Logger DEFAULT_LOGGER = Logger.getLogger("org.pjdbc.audit");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                         .withZone(ZoneId.systemDefault());

    // Global audit event handlers
    private static final CopyOnWriteArrayList<Consumer<AuditEvent>> globalHandlers = new CopyOnWriteArrayList<>();

    // In-memory audit log for testing/debugging (limited size)
    private static final List<AuditEvent> auditLog = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LOG_SIZE = 10000;

    public enum LogLevel {
        ALL,      // Log all operations
        SUCCESS,  // Only log successful operations
        FAILURE   // Only log failed operations
    }

    public enum OperationType {
        QUERY,
        UPDATE,
        BATCH,
        EXECUTE
    }

    static {
        try {
            DriverManager.registerDriver(new AuditDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "audit".equals(subprotocol);
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
        return new AuditConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        AuditConnection auditConn = (AuditConnection) conn;
        return new AuditStatement(delegate, conn, auditConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        AuditConnection auditConn = (AuditConnection) conn;
        return new AuditPreparedStatement(delegate, conn, auditConn.getConfig(), null);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        AuditConnection auditConn = (AuditConnection) conn;
        return new AuditCallableStatement(delegate, conn, auditConn.getConfig(), null);
    }

    // Package-private for creating PreparedStatement with SQL
    PreparedStatement proxyPreparedStatementWithSql(PreparedStatement delegate, Connection conn, String sql) throws SQLException {
        AuditConnection auditConn = (AuditConnection) conn;
        return new AuditPreparedStatement(delegate, conn, auditConn.getConfig(), sql);
    }

    CallableStatement proxyCallableStatementWithSql(CallableStatement delegate, Connection conn, String sql) throws SQLException {
        AuditConnection auditConn = (AuditConnection) conn;
        return new AuditCallableStatement(delegate, conn, auditConn.getConfig(), sql);
    }

    /**
     * Audit event record containing all audit information.
     */
    public static class AuditEvent {
        private final long id;
        private final Instant timestamp;
        private final String auditName;
        private final String connectionId;
        private final String user;
        private final OperationType operationType;
        private final String sql;
        private final long executionTimeMs;
        private final int rowsAffected;
        private final boolean success;
        private final String errorMessage;
        private final String errorSqlState;

        private static final AtomicLong idGenerator = new AtomicLong(0);

        public AuditEvent(String auditName, String connectionId, String user,
                         OperationType operationType, String sql,
                         long executionTimeMs, int rowsAffected,
                         boolean success, String errorMessage, String errorSqlState) {
            this.id = idGenerator.incrementAndGet();
            this.timestamp = Instant.now();
            this.auditName = auditName;
            this.connectionId = connectionId;
            this.user = user;
            this.operationType = operationType;
            this.sql = sql;
            this.executionTimeMs = executionTimeMs;
            this.rowsAffected = rowsAffected;
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorSqlState = errorSqlState;
        }

        // Getters
        public long getId() { return id; }
        public Instant getTimestamp() { return timestamp; }
        public String getAuditName() { return auditName; }
        public String getConnectionId() { return connectionId; }
        public String getUser() { return user; }
        public OperationType getOperationType() { return operationType; }
        public String getSql() { return sql; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public int getRowsAffected() { return rowsAffected; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorSqlState() { return errorSqlState; }

        public String toLogString(AuditConfig config) {
            StringBuilder sb = new StringBuilder();
            sb.append("[AUDIT-").append(id).append("] ");
            sb.append(TIMESTAMP_FORMAT.format(timestamp));
            sb.append(" | ").append(auditName);
            sb.append(" | ").append(user != null ? user : "unknown");
            sb.append(" | ").append(operationType);

            if (config.isIncludeSql() && sql != null) {
                String safeSql = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
                sb.append(" | SQL: ").append(safeSql.replace("\n", " "));
            }

            if (config.isIncludeTime()) {
                sb.append(" | ").append(executionTimeMs).append("ms");
            }

            if (config.isIncludeRows() && rowsAffected >= 0) {
                sb.append(" | rows: ").append(rowsAffected);
            }

            sb.append(" | ").append(success ? "SUCCESS" : "FAILURE");

            if (!success && errorMessage != null) {
                sb.append(" | error: ").append(errorMessage);
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("AuditEvent[id=%d, time=%s, name=%s, user=%s, type=%s, success=%s]",
                id, timestamp, auditName, user, operationType, success);
        }
    }

    /**
     * Configuration for audit logging.
     */
    public static class AuditConfig {
        private final String name;
        private final LogLevel logLevel;
        private final boolean includeSql;
        private final boolean includeTime;
        private final boolean includeRows;

        public AuditConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.name = parser.getParameter("name", "audit");
            this.logLevel = parseLogLevel(parser.getParameter("logLevel", "all"));
            this.includeSql = parseBoolean(parser.getParameter("includeSql", "true"));
            this.includeTime = parseBoolean(parser.getParameter("includeTime", "true"));
            this.includeRows = parseBoolean(parser.getParameter("includeRows", "true"));
        }

        // Constructor for testing
        public AuditConfig(String name, LogLevel logLevel, boolean includeSql,
                          boolean includeTime, boolean includeRows) {
            this.name = name;
            this.logLevel = logLevel;
            this.includeSql = includeSql;
            this.includeTime = includeTime;
            this.includeRows = includeRows;
        }

        private static LogLevel parseLogLevel(String s) {
            if ("success".equalsIgnoreCase(s)) return LogLevel.SUCCESS;
            if ("failure".equalsIgnoreCase(s)) return LogLevel.FAILURE;
            return LogLevel.ALL;
        }

        private static boolean parseBoolean(String s) {
            return !"false".equalsIgnoreCase(s);
        }

        public String getName() { return name; }
        public LogLevel getLogLevel() { return logLevel; }
        public boolean isIncludeSql() { return includeSql; }
        public boolean isIncludeTime() { return includeTime; }
        public boolean isIncludeRows() { return includeRows; }

        public boolean shouldLog(boolean success) {
            switch (logLevel) {
                case SUCCESS: return success;
                case FAILURE: return !success;
                default: return true;
            }
        }

        @Override
        public String toString() {
            return String.format("AuditConfig[name=%s, level=%s, sql=%s, time=%s, rows=%s]",
                name, logLevel, includeSql, includeTime, includeRows);
        }
    }

    /**
     * Connection wrapper that holds audit configuration.
     */
    private class AuditConnection extends AbstractConnection {
        private final AuditConfig config;
        private final String connectionId;
        private final String user;

        public AuditConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new AuditConfig(url);
            this.connectionId = "conn-" + System.identityHashCode(conn);
            this.user = info != null ? info.getProperty("user") : null;
        }

        public AuditConfig getConfig() { return config; }
        public String getConnectionId() { return connectionId; }
        public String getUser() { return user; }

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
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql), this, sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql, autoGeneratedKeys), this, sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql, columnIndexes), this, sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency), this, sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this, sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return proxyPreparedStatementWithSql(getDelegate().prepareStatement(sql, columnNames), this, sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return proxyCallableStatementWithSql(getDelegate().prepareCall(sql), this, sql);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyCallableStatementWithSql(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency), this, sql);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyCallableStatementWithSql(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this, sql);
        }
    }

    /**
     * Record an audit event and dispatch to handlers.
     */
    private static void recordAuditEvent(AuditEvent event, AuditConfig config) {
        // Check if we should log based on success/failure
        if (!config.shouldLog(event.isSuccess())) {
            return;
        }

        // Log to java.util.logging
        Level level = event.isSuccess() ? Level.INFO : Level.WARNING;
        DEFAULT_LOGGER.log(level, event.toLogString(config));

        // Store in memory for testing/debugging
        synchronized (auditLog) {
            if (auditLog.size() >= MAX_LOG_SIZE) {
                auditLog.remove(0);
            }
            auditLog.add(event);
        }

        // Dispatch to global handlers
        for (Consumer<AuditEvent> handler : globalHandlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                DEFAULT_LOGGER.log(Level.SEVERE, "Audit handler error", e);
            }
        }
    }

    /**
     * Statement wrapper that provides audit logging.
     */
    private static class AuditStatement extends AbstractStatement {
        private final AuditConfig config;
        private final String connectionId;
        private final String user;

        public AuditStatement(Statement delegate, Connection conn, AuditConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
            if (conn instanceof AuditConnection) {
                AuditConnection ac = (AuditConnection) conn;
                this.connectionId = ac.getConnectionId();
                this.user = ac.getUser();
            } else {
                this.connectionId = "unknown";
                this.user = null;
            }
        }

        @Override
        protected ResultSet wrap(ResultSet r) throws SQLException {
            Driver driver = ((AbstractConnection)getConnection()).getDriver();
            return ((AbstractProxyDriver)driver).proxyResultSet(this, r);
        }

        private void audit(OperationType type, String sql, long startTime,
                          int rows, boolean success, SQLException error) {
            long elapsed = System.currentTimeMillis() - startTime;
            AuditEvent event = new AuditEvent(
                config.getName(), connectionId, user, type, sql,
                elapsed, rows, success,
                error != null ? error.getMessage() : null,
                error != null ? error.getSQLState() : null
            );
            recordAuditEvent(event, config);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            try {
                boolean result = super.execute(sql);
                audit(OperationType.EXECUTE, sql, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.EXECUTE, sql, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            try {
                ResultSet result = super.executeQuery(sql);
                audit(OperationType.QUERY, sql, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.QUERY, sql, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int rows = super.executeUpdate(sql);
                audit(OperationType.UPDATE, sql, start, rows, true, null);
                return rows;
            } catch (SQLException e) {
                audit(OperationType.UPDATE, sql, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int[] result = super.executeBatch();
                int totalRows = 0;
                for (int r : result) if (r > 0) totalRows += r;
                audit(OperationType.BATCH, "[batch]", start, totalRows, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.BATCH, "[batch]", start, -1, false, e);
                throw e;
            }
        }
    }

    /**
     * PreparedStatement wrapper that provides audit logging.
     */
    private static class AuditPreparedStatement extends AbstractPreparedStatement {
        private final AuditConfig config;
        private final String connectionId;
        private final String user;
        private final String sql;

        public AuditPreparedStatement(PreparedStatement delegate, Connection conn,
                                      AuditConfig config, String sql) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
            if (conn instanceof AuditConnection) {
                AuditConnection ac = (AuditConnection) conn;
                this.connectionId = ac.getConnectionId();
                this.user = ac.getUser();
            } else {
                this.connectionId = "unknown";
                this.user = null;
            }
        }

        private void audit(OperationType type, long startTime, int rows,
                          boolean success, SQLException error) {
            long elapsed = System.currentTimeMillis() - startTime;
            AuditEvent event = new AuditEvent(
                config.getName(), connectionId, user, type, sql,
                elapsed, rows, success,
                error != null ? error.getMessage() : null,
                error != null ? error.getSQLState() : null
            );
            recordAuditEvent(event, config);
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                boolean result = super.execute();
                audit(OperationType.EXECUTE, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.EXECUTE, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                ResultSet result = super.executeQuery();
                audit(OperationType.QUERY, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.QUERY, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int rows = super.executeUpdate();
                audit(OperationType.UPDATE, start, rows, true, null);
                return rows;
            } catch (SQLException e) {
                audit(OperationType.UPDATE, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int[] result = super.executeBatch();
                int totalRows = 0;
                for (int r : result) if (r > 0) totalRows += r;
                audit(OperationType.BATCH, start, totalRows, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.BATCH, start, -1, false, e);
                throw e;
            }
        }
    }

    /**
     * CallableStatement wrapper that provides audit logging.
     */
    private static class AuditCallableStatement extends AbstractCallableStatement {
        private final AuditConfig config;
        private final String connectionId;
        private final String user;
        private final String sql;

        public AuditCallableStatement(CallableStatement delegate, Connection conn,
                                      AuditConfig config, String sql) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.sql = sql;
            if (conn instanceof AuditConnection) {
                AuditConnection ac = (AuditConnection) conn;
                this.connectionId = ac.getConnectionId();
                this.user = ac.getUser();
            } else {
                this.connectionId = "unknown";
                this.user = null;
            }
        }

        private void audit(OperationType type, long startTime, int rows,
                          boolean success, SQLException error) {
            long elapsed = System.currentTimeMillis() - startTime;
            AuditEvent event = new AuditEvent(
                config.getName(), connectionId, user, type, sql,
                elapsed, rows, success,
                error != null ? error.getMessage() : null,
                error != null ? error.getSQLState() : null
            );
            recordAuditEvent(event, config);
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                boolean result = super.execute();
                audit(OperationType.EXECUTE, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.EXECUTE, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                ResultSet result = super.executeQuery();
                audit(OperationType.QUERY, start, -1, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.QUERY, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int rows = super.executeUpdate();
                audit(OperationType.UPDATE, start, rows, true, null);
                return rows;
            } catch (SQLException e) {
                audit(OperationType.UPDATE, start, -1, false, e);
                throw e;
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            long start = System.currentTimeMillis();
            try {
                int[] result = super.executeBatch();
                int totalRows = 0;
                for (int r : result) if (r > 0) totalRows += r;
                audit(OperationType.BATCH, start, totalRows, true, null);
                return result;
            } catch (SQLException e) {
                audit(OperationType.BATCH, start, -1, false, e);
                throw e;
            }
        }
    }

    // ========== Static API ==========

    /**
     * Register a global audit event handler.
     */
    public static void addAuditHandler(Consumer<AuditEvent> handler) {
        globalHandlers.add(handler);
    }

    /**
     * Remove a global audit event handler.
     */
    public static void removeAuditHandler(Consumer<AuditEvent> handler) {
        globalHandlers.remove(handler);
    }

    /**
     * Clear all global audit handlers.
     */
    public static void clearAuditHandlers() {
        globalHandlers.clear();
    }

    /**
     * Get all audit events from the in-memory log.
     */
    public static List<AuditEvent> getAuditLog() {
        synchronized (auditLog) {
            return new ArrayList<>(auditLog);
        }
    }

    /**
     * Get audit events filtered by audit name.
     */
    public static List<AuditEvent> getAuditLog(String auditName) {
        synchronized (auditLog) {
            List<AuditEvent> filtered = new ArrayList<>();
            for (AuditEvent event : auditLog) {
                if (auditName.equals(event.getAuditName())) {
                    filtered.add(event);
                }
            }
            return filtered;
        }
    }

    /**
     * Clear the in-memory audit log.
     */
    public static void clearAuditLog() {
        synchronized (auditLog) {
            auditLog.clear();
        }
    }

    /**
     * Get the audit configuration for a connection.
     * Returns null if connection is not an AuditDriver connection.
     */
    public static AuditConfig getAuditConfig(Connection conn) {
        if (conn instanceof AuditConnection) {
            return ((AuditConnection) conn).getConfig();
        }
        return null;
    }
}
