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

    // Clean patterns to match on the comment/literal stripped SQL
    private static final Pattern CLEAN_DML_PATTERN = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CLEAN_DDL_PATTERN = Pattern.compile(
        "\\b(CREATE|ALTER|DROP|RENAME)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CLEAN_DCL_PATTERN = Pattern.compile(
        "\\b(GRANT|REVOKE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Clean SQL statement by stripping single-line comments, block comments,
     * single-quoted string literals, double-quoted identifiers, and backtick-quoted identifiers.
     * Replaces them with spaces of the same length to preserve character alignment.
     */
    public static String cleanSql(String sql) {
        if (sql == null) return "";
        StringBuilder sb = new StringBuilder();
        int len = sql.length();
        boolean inSingleLineComment = false;
        boolean inBlockComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < len) ? sql.charAt(i + 1) : '\0';

            if (inSingleLineComment) {
                if (c == '\n' || c == '\r') {
                    inSingleLineComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    sb.append(' ');
                    sb.append(' ');
                    i++; // skip '/'
                } else {
                    if (c == '\n' || c == '\r') {
                        sb.append(c);
                    } else {
                        sb.append(' ');
                    }
                }
                continue;
            }

            if (inSingleQuote) {
                if (c == '\\' && next != '\0') {
                    sb.append(' ');
                    sb.append(' ');
                    i++; // skip next char
                } else if (c == '\'') {
                    if (next == '\'') {
                        // Escaped quote: ''
                        sb.append(' ');
                        sb.append(' ');
                        i++; // skip next quote
                    } else {
                        inSingleQuote = false;
                        sb.append(' ');
                    }
                } else {
                    sb.append(' ');
                }
                continue;
            }

            if (inDoubleQuote) {
                if (c == '\\' && next != '\0') {
                    sb.append(' ');
                    sb.append(' ');
                    i++;
                } else if (c == '"') {
                    inDoubleQuote = false;
                    sb.append(' ');
                } else {
                    sb.append(' ');
                }
                continue;
            }

            if (inBacktick) {
                if (c == '\\' && next != '\0') {
                    sb.append(' ');
                    sb.append(' ');
                    i++;
                } else if (c == '`') {
                    inBacktick = false;
                    sb.append(' ');
                } else {
                    sb.append(' ');
                }
                continue;
            }

            // Check for starting of comments/literals
            if (c == '-' && next == '-') {
                inSingleLineComment = true;
                sb.append(' ');
                sb.append(' ');
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                sb.append(' ');
                sb.append(' ');
                i++;
            } else if (c == '\'') {
                inSingleQuote = true;
                sb.append(' ');
            } else if (c == '"') {
                inDoubleQuote = true;
                sb.append(' ');
            } else if (c == '`') {
                inBacktick = true;
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

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

            String cleaned = cleanSql(sql);

            // Check DML
            if (!allowDML) {
                Matcher m = CLEAN_DML_PATTERN.matcher(cleaned);
                if (m.find()) {
                    throw new SQLException(message + " [DML blocked: " + m.group(1).toUpperCase() + "]");
                }
            }

            // Check DDL
            if (!allowDDL) {
                Matcher m = CLEAN_DDL_PATTERN.matcher(cleaned);
                if (m.find()) {
                    throw new SQLException(message + " [DDL blocked: " + m.group(1).toUpperCase() + "]");
                }
            }

            // Always block DCL
            Matcher m = CLEAN_DCL_PATTERN.matcher(cleaned);
            if (m.find()) {
                throw new SQLException(message + " [DCL blocked: " + m.group(1).toUpperCase() + "]");
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
