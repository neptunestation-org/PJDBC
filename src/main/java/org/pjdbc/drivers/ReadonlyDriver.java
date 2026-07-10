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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.JdbcUrlParser;

/**
 * ReadonlyDriver enforces read-only database access by blocking write operations.
 *
 * <p>URL format: {@code jdbc:readonly[param=value,...]:jdbc:target:...}
 *
 * <p>Blocked operations (by default):
 * <ul>
 *   <li>DML: INSERT, UPDATE, DELETE, MERGE, UPSERT, REPLACE, TRUNCATE</li>
 *   <li>DDL: CREATE, ALTER, DROP, RENAME</li>
 *   <li>DCL: GRANT, REVOKE</li>
 *   <li>TCL: (transactions are allowed)</li>
 * </ul>
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:readonly:jdbc:postgresql://localhost/mydb
 * jdbc:readonly[allowDDL=true]:jdbc:postgresql://localhost/mydb
 * jdbc:readonly[message=No writes allowed in reporting mode]:jdbc:mysql://localhost/db
 * </pre>
 */
@DriverCapability(
    prefix = "readonly",
    description = "Enforces read-only database access",
    capabilities = {"security", "filtering"}
)
@DriverParameter(name = "allowDDL", type = ParameterType.BOOLEAN,
    description = "Allow DDL statements (CREATE, ALTER, DROP)", defaultValue = "false")
@DriverParameter(name = "allowDML", type = ParameterType.BOOLEAN,
    description = "Allow DML statements (INSERT, UPDATE, DELETE)", defaultValue = "false")
@DriverParameter(name = "message", type = ParameterType.STRING,
    description = "Custom error message for blocked operations")
public class ReadonlyDriver extends AbstractProxyDriver {

    // Robust prefix to skip leading comments and whitespace
    private static final String PREFIX = "(?:\\s|/\\*.*?\\*/|--[^\\n]*?(?:\\n|$))*";

    // Pattern to detect DML write operations
    private static final Pattern DML_PATTERN = Pattern.compile(
        "^" + PREFIX + "(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to detect DDL operations
    private static final Pattern DDL_PATTERN = Pattern.compile(
        "^" + PREFIX + "(CREATE|ALTER|DROP|RENAME)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to detect DCL operations
    private static final Pattern DCL_PATTERN = Pattern.compile(
        "^" + PREFIX + "(GRANT|REVOKE)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to detect DML within CTEs: WITH x AS (DELETE ...)
    private static final Pattern CTE_DML_PATTERN = Pattern.compile(
        "\\bAS\\s*\\(" + PREFIX + "(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    static {
        try {
            DriverManager.registerDriver(new ReadonlyDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "readonly".equals(subprotocol);
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
        return new ReadonlyConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        ReadonlyConnection readonlyConn = (ReadonlyConnection) conn;
        return new ReadonlyStatement(delegate, conn, readonlyConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        return new ReadonlyPreparedStatement(delegate, conn);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        return new ReadonlyCallableStatement(delegate, conn);
    }

    /**
     * Configuration holder for readonly parameters.
     */
    public static class ReadonlyConfig {
        private final boolean allowDDL;
        private final boolean allowDML;
        private final String message;

        public ReadonlyConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.allowDDL = parseBoolean(parser.getParameter("allowDDL", "false"));
            this.allowDML = parseBoolean(parser.getParameter("allowDML", "false"));
            this.message = parser.getParameter("message", "ReadonlyDriver: Write operation not permitted");
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s);
        }

        public boolean isAllowDDL() { return allowDDL; }
        public boolean isAllowDML() { return allowDML; }
        public String getMessage() { return message; }

        /**
         * Check if a SQL statement is allowed to execute.
         * @throws SQLException if the statement is blocked
         */
        public void checkStatement(String sql) throws SQLException {
            if (sql == null) return;

            // Check DML
            Matcher dmlMatcher = DML_PATTERN.matcher(sql);
            if (!allowDML && dmlMatcher.find()) {
                throw new SQLException(message + " [DML blocked: " + dmlMatcher.group(1).toUpperCase() + "]");
            }

            // Check DDL
            Matcher ddlMatcher = DDL_PATTERN.matcher(sql);
            if (!allowDDL && ddlMatcher.find()) {
                throw new SQLException(message + " [DDL blocked: " + ddlMatcher.group(1).toUpperCase() + "]");
            }

            // Always block DCL
            Matcher dclMatcher = DCL_PATTERN.matcher(sql);
            if (dclMatcher.find()) {
                throw new SQLException(message + " [DCL blocked: " + dclMatcher.group(1).toUpperCase() + "]");
            }

            // Check DML in CTEs
            Matcher cteMatcher = CTE_DML_PATTERN.matcher(sql);
            if (!allowDML && cteMatcher.find()) {
                throw new SQLException(message + " [DML blocked: " + cteMatcher.group(1).toUpperCase() + "]");
            }
        }
    }

    /**
     * Connection wrapper that holds readonly configuration.
     */
    private class ReadonlyConnection extends AbstractConnection {
        private final ReadonlyConfig config;

        public ReadonlyConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new ReadonlyConfig(url);
        }

        public ReadonlyConfig getConfig() {
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
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, autoGeneratedKeys), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnIndexes), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            config.checkStatement(sql);
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnNames), this);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            config.checkStatement(sql);
            return proxyCallableStatement(getDelegate().prepareCall(sql), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            config.checkStatement(sql);
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            config.checkStatement(sql);
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }
    }

    /**
     * Statement wrapper that checks SQL before execution.
     */
    private static class ReadonlyStatement extends AbstractStatement {
        private final ReadonlyConfig config;

        public ReadonlyStatement(Statement delegate, Connection conn, ReadonlyConfig config) throws SQLException {
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
            config.checkStatement(sql);
            return super.execute(sql);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            config.checkStatement(sql);
            return super.executeQuery(sql);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            config.checkStatement(sql);
            return super.executeUpdate(sql);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // Batch statements were already checked when added
            return super.executeBatch();
        }

        @Override
        public void addBatch(String sql) throws SQLException {
            config.checkStatement(sql);
            super.addBatch(sql);
        }
    }

    /**
     * PreparedStatement wrapper (SQL already checked at prepare time).
     * No additional validation needed - SQL was already checked at prepareStatement() time.
     */
    private static class ReadonlyPreparedStatement extends AbstractPreparedStatement {

        public ReadonlyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
            super(delegate, conn);
        }
    }

    /**
     * CallableStatement wrapper (SQL already checked at prepare time).
     * No additional validation needed - SQL was already checked at prepareCall() time.
     */
    private static class ReadonlyCallableStatement extends AbstractCallableStatement {

        public ReadonlyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
            super(delegate, conn);
        }
    }
}
