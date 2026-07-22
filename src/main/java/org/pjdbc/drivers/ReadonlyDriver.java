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

        /**
         * Helper method to strip comments, string literals, and quoted identifiers
         * from a SQL string to prevent security bypasses and ReDoS.
         */
        private String cleanSql(String sql) {
            if (sql == null) return "";
            StringBuilder sb = new StringBuilder();
            int len = sql.length();
            boolean inString = false;
            boolean inDoubleQuote = false;
            boolean inBacktick = false;
            boolean inBlockComment = false;
            boolean inLineComment = false;

            for (int i = 0; i < len; i++) {
                char c = sql.charAt(i);

                if (inBlockComment) {
                    if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                        inBlockComment = false;
                        i++; // skip '/'
                    }
                    continue;
                }

                if (inLineComment) {
                    if (c == '\n' || c == '\r') {
                        inLineComment = false;
                        sb.append(' '); // replace newline with space to preserve separation
                    }
                    continue;
                }

                if (inString) {
                    if (c == '\'') {
                        // Check if it's an escaped single quote (doubled: '')
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            i++; // skip next quote
                        } else {
                            inString = false;
                        }
                    }
                    continue;
                }

                if (inDoubleQuote) {
                    if (c == '"') {
                        inDoubleQuote = false;
                    }
                    continue;
                }

                if (inBacktick) {
                    if (c == '`') {
                        inBacktick = false;
                    }
                    continue;
                }

                // Not in string, comment, or identifier quote
                // Check for block comment start
                if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }

                // Check for line comment start
                if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }

                // Check for string start
                if (c == '\'') {
                    inString = true;
                    continue;
                }

                // Check for double quote identifier start
                if (c == '"') {
                    inDoubleQuote = true;
                    continue;
                }

                // Check for backtick identifier start
                if (c == '`') {
                    inBacktick = true;
                    continue;
                }

                sb.append(c);
            }
            return sb.toString();
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
