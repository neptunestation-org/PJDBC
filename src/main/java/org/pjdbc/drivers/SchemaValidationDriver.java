package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * SchemaValidationDriver validates SQL statements against a defined schema.
 *
 * <p>URL format: {@code jdbc:schema[param=value,...]:jdbc:target:...}
 *
 * <p>Modes:
 * <ul>
 *   <li>whitelist - Only tables in allowedTables list are permitted</li>
 *   <li>blacklist - All tables except those in blockedTables list are permitted</li>
 *   <li>metadata - Load allowed tables from database metadata at connection time</li>
 * </ul>
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:schema[allowedTables=users;orders;products]:jdbc:postgresql://localhost/mydb
 * jdbc:schema[blockedTables=audit_log;secrets,mode=blacklist]:jdbc:mysql://localhost/db
 * jdbc:schema[mode=metadata,schemaPattern=public]:jdbc:postgresql://localhost/mydb
 * jdbc:schema[blockedColumns=ssn;credit_card]:jdbc:postgresql://localhost/mydb
 * </pre>
 */
@DriverCapability(
    prefix = "schema",
    description = "Validates SQL statements against a defined schema (table/column whitelist or blacklist)",
    capabilities = {"security", "validation"}
)
@DriverParameter(name = "allowedTables", type = ParameterType.STRING,
    description = "Semicolon-separated list of allowed table names (whitelist mode)")
@DriverParameter(name = "blockedTables", type = ParameterType.STRING,
    description = "Semicolon-separated list of blocked table names (blacklist mode)")
@DriverParameter(name = "allowedColumns", type = ParameterType.STRING,
    description = "Semicolon-separated list of allowed column patterns")
@DriverParameter(name = "blockedColumns", type = ParameterType.STRING,
    description = "Semicolon-separated list of blocked column patterns")
@DriverParameter(name = "mode", type = ParameterType.STRING,
    description = "Validation mode", defaultValue = "whitelist",
    enumValues = {"whitelist", "blacklist", "metadata"})
@DriverParameter(name = "caseSensitive", type = ParameterType.BOOLEAN,
    description = "Whether table/column names are case-sensitive", defaultValue = "false")
@DriverParameter(name = "message", type = ParameterType.STRING,
    description = "Custom error message prefix", defaultValue = "SchemaValidationDriver")
@DriverParameter(name = "loadFromDb", type = ParameterType.BOOLEAN,
    description = "Load allowed tables from database metadata", defaultValue = "false")
@DriverParameter(name = "schemaPattern", type = ParameterType.STRING,
    description = "Schema pattern for metadata loading")
@DriverParameter(name = "tableTypes", type = ParameterType.STRING,
    description = "Semicolon-separated table types for metadata", defaultValue = "TABLE;VIEW")
public class SchemaValidationDriver extends AbstractProxyDriver {

    // Regex to handle whitespace and comments as separators
    private static final String SEP = "(?:\\s|/\\*.*?\\*/|--[^\\n]*?(?:\\n|$))+";
    private static final String OPT_SEP = "(?:\\s|/\\*.*?\\*/|--[^\\n]*?(?:\\n|$))*";

    // Pattern to extract table names from SQL
    // Matches: FROM table, JOIN table, INTO table, UPDATE table, TABLE table (for TRUNCATE)
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "\\b(?:FROM|JOIN|INTO|UPDATE|TABLE|TRUNCATE)" + SEP + "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to extract column names from SELECT
    private static final Pattern SELECT_COLUMNS_PATTERN = Pattern.compile(
        "\\bSELECT" + SEP + "(.+?)" + SEP + "FROM\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to extract columns from INSERT
    private static final Pattern INSERT_COLUMNS_PATTERN = Pattern.compile(
        "\\bINSERT" + SEP + "INTO" + SEP + "[a-zA-Z_][a-zA-Z0-9_.]*" + OPT_SEP + "\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to extract columns from UPDATE SET
    private static final Pattern UPDATE_COLUMNS_PATTERN = Pattern.compile(
        "\\bSET" + SEP + "([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to parse column names (handles table.column and aliases)
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)"
    );

    // Global configurations for external access
    private static final Map<Connection, SchemaConfig> connectionConfigs = new ConcurrentHashMap<>();

    static {
        try {
            DriverManager.registerDriver(new SchemaValidationDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "schema".equals(subprotocol);
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
        SchemaConnection conn = new SchemaConnection(delegate, this, url, info);
        connectionConfigs.put(conn, conn.getConfig());
        return conn;
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        SchemaConnection schemaConn = (SchemaConnection) conn;
        return new SchemaStatement(delegate, conn, schemaConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        SchemaConnection schemaConn = (SchemaConnection) conn;
        return new SchemaPreparedStatement(delegate, conn, schemaConn.getConfig());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        SchemaConnection schemaConn = (SchemaConnection) conn;
        return new SchemaCallableStatement(delegate, conn, schemaConn.getConfig());
    }

    /**
     * Get the schema configuration for a connection.
     */
    public static SchemaConfig getSchemaConfig(Connection conn) {
        return connectionConfigs.get(conn);
    }

    /**
     * Validation mode for the schema driver.
     */
    public enum ValidationMode {
        WHITELIST,  // Only allow explicitly listed tables
        BLACKLIST,  // Block explicitly listed tables, allow all others
        METADATA    // Load allowed tables from database metadata
    }

    /**
     * Configuration holder for schema validation parameters.
     */
    public static class SchemaConfig {
        private final Set<String> allowedTables;
        private final Set<String> blockedTables;
        private final Set<String> allowedColumns;
        private final Set<String> blockedColumns;
        private final ValidationMode mode;
        private final boolean caseSensitive;
        private final String message;
        private final boolean loadFromDb;
        private final String schemaPattern;
        private final Set<String> tableTypes;
        private boolean metadataLoaded = false;

        public SchemaConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);

            this.caseSensitive = parseBoolean(parser.getParameter("caseSensitive", "false"));
            this.allowedTables = parseSet(parser.getParameter("allowedTables", ""), caseSensitive);
            this.blockedTables = parseSet(parser.getParameter("blockedTables", ""), caseSensitive);
            this.allowedColumns = parseSet(parser.getParameter("allowedColumns", ""), caseSensitive);
            this.blockedColumns = parseSet(parser.getParameter("blockedColumns", ""), caseSensitive);
            this.mode = parseMode(parser.getParameter("mode", "whitelist"));
            this.message = parser.getParameter("message", "SchemaValidationDriver");
            this.loadFromDb = parseBoolean(parser.getParameter("loadFromDb", "false"));
            this.schemaPattern = parser.getParameter("schemaPattern", null);
            this.tableTypes = parseSet(parser.getParameter("tableTypes", "TABLE;VIEW"), true);  // Keep table types uppercase
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s);
        }

        private static ValidationMode parseMode(String s) {
            if ("blacklist".equalsIgnoreCase(s)) return ValidationMode.BLACKLIST;
            if ("metadata".equalsIgnoreCase(s)) return ValidationMode.METADATA;
            return ValidationMode.WHITELIST;
        }

        private static Set<String> parseSet(String s, boolean caseSensitive) {
            Set<String> result = new HashSet<>();
            if (s != null && !s.isEmpty()) {
                for (String part : s.split(";")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(caseSensitive ? trimmed : trimmed.toLowerCase());
                    }
                }
            }
            return result;
        }

        /**
         * Load allowed tables from database metadata.
         */
        public void loadFromMetadata(Connection conn) throws SQLException {
            if (metadataLoaded || mode != ValidationMode.METADATA) return;

            DatabaseMetaData meta = conn.getMetaData();
            String[] types = tableTypes.toArray(new String[0]);

            try (ResultSet rs = meta.getTables(null, schemaPattern, "%", types)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null) {
                        allowedTables.add(caseSensitive ? tableName : tableName.toLowerCase());
                    }
                }
            }
            metadataLoaded = true;
        }

        public Set<String> getAllowedTables() { return Collections.unmodifiableSet(allowedTables); }
        public Set<String> getBlockedTables() { return Collections.unmodifiableSet(blockedTables); }
        public Set<String> getAllowedColumns() { return Collections.unmodifiableSet(allowedColumns); }
        public Set<String> getBlockedColumns() { return Collections.unmodifiableSet(blockedColumns); }
        public ValidationMode getMode() { return mode; }
        public boolean isCaseSensitive() { return caseSensitive; }
        public String getMessage() { return message; }

        /**
         * Add a table to the allowed list programmatically.
         */
        public void addAllowedTable(String table) {
            allowedTables.add(caseSensitive ? table : table.toLowerCase());
        }

        /**
         * Add a table to the blocked list programmatically.
         */
        public void addBlockedTable(String table) {
            blockedTables.add(caseSensitive ? table : table.toLowerCase());
        }

        /**
         * Add a column to the blocked list programmatically.
         */
        public void addBlockedColumn(String column) {
            blockedColumns.add(caseSensitive ? column : column.toLowerCase());
        }

        /**
         * Check if a SQL statement is allowed to execute.
         * @throws SQLException if the statement references blocked tables or columns
         */
        public void checkStatement(String sql) throws SQLException {
            if (sql == null || sql.trim().isEmpty()) return;

            // Extract and validate tables
            Set<String> tables = extractTables(sql);
            for (String table : tables) {
                validateTable(table, sql);
            }

            // Extract and validate columns
            Set<String> columns = extractColumns(sql);
            for (String column : columns) {
                validateColumn(column, sql);
            }
        }

        private Set<String> extractTables(String sql) {
            Set<String> tables = new HashSet<>();
            Matcher matcher = TABLE_PATTERN.matcher(sql);
            while (matcher.find()) {
                String table = matcher.group(1);
                // Handle schema.table format - extract just table name
                if (table.contains(".")) {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }
                tables.add(caseSensitive ? table : table.toLowerCase());
            }
            return tables;
        }

        private Set<String> extractColumns(String sql) {
            Set<String> columns = new HashSet<>();

            // Extract from SELECT clause
            Matcher selectMatcher = SELECT_COLUMNS_PATTERN.matcher(sql);
            if (selectMatcher.find()) {
                String selectPart = selectMatcher.group(1);
                if (!"*".equals(selectPart.trim())) {
                    extractColumnNames(selectPart, columns);
                }
            }

            // Extract from INSERT columns
            Matcher insertMatcher = INSERT_COLUMNS_PATTERN.matcher(sql);
            if (insertMatcher.find()) {
                extractColumnNames(insertMatcher.group(1), columns);
            }

            // Extract from UPDATE SET
            Matcher updateMatcher = UPDATE_COLUMNS_PATTERN.matcher(sql);
            while (updateMatcher.find()) {
                String col = updateMatcher.group(1);
                columns.add(caseSensitive ? col : col.toLowerCase());
            }

            return columns;
        }

        private void extractColumnNames(String columnList, Set<String> columns) {
            // Strip comments from column list before parsing
            columnList = Pattern.compile("/\\*.*?\\*/|--[^\\n]*?(?:\\n|$)", Pattern.DOTALL)
                .matcher(columnList).replaceAll(" ");

            // Split by comma and extract column names
            for (String part : columnList.split(",")) {
                // Skip aggregate functions and literals
                String trimmed = part.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("'") ||
                    trimmed.matches("\\d+") || trimmed.equals("*")) {
                    continue;
                }

                // Handle aliases (column AS alias or column alias)
                String[] asParts = trimmed.split("\\s+(?:AS\\s+)?", 2);
                String columnExpr = asParts[0].trim();

                // Extract column name from expression
                Matcher nameMatcher = COLUMN_NAME_PATTERN.matcher(columnExpr);
                if (nameMatcher.find()) {
                    String col = nameMatcher.group(1);
                    // Handle table.column - extract just column name for checking
                    if (col.contains(".")) {
                        col = col.substring(col.lastIndexOf('.') + 1);
                    }
                    columns.add(caseSensitive ? col : col.toLowerCase());
                }
            }
        }

        private void validateTable(String table, String sql) throws SQLException {
            switch (mode) {
                case WHITELIST:
                    if (!allowedTables.isEmpty() && !allowedTables.contains(table)) {
                        throw new SQLException(message + ": Table '" + table + "' is not in allowed tables list");
                    }
                    break;
                case BLACKLIST:
                    if (blockedTables.contains(table)) {
                        throw new SQLException(message + ": Table '" + table + "' is blocked");
                    }
                    break;
                case METADATA:
                    // Metadata mode uses allowedTables populated from database
                    if (!allowedTables.isEmpty() && !allowedTables.contains(table)) {
                        throw new SQLException(message + ": Table '" + table + "' does not exist in database schema");
                    }
                    break;
            }
        }

        private void validateColumn(String column, String sql) throws SQLException {
            // Check blocked columns regardless of mode
            if (blockedColumns.contains(column)) {
                throw new SQLException(message + ": Column '" + column + "' is blocked");
            }

            // In whitelist mode, also check allowed columns if specified
            if (mode == ValidationMode.WHITELIST && !allowedColumns.isEmpty()) {
                if (!allowedColumns.contains(column)) {
                    throw new SQLException(message + ": Column '" + column + "' is not in allowed columns list");
                }
            }
        }
    }

    /**
     * Connection wrapper that holds schema configuration.
     */
    private class SchemaConnection extends AbstractConnection {
        private final SchemaConfig config;

        public SchemaConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new SchemaConfig(url);

            // Load from metadata if configured
            if (config.loadFromDb || config.getMode() == ValidationMode.METADATA) {
                config.loadFromMetadata(conn);
            }
        }

        public SchemaConfig getConfig() {
            return config;
        }

        @Override
        public void close() throws SQLException {
            connectionConfigs.remove(this);
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
    private static class SchemaStatement extends AbstractStatement {
        private final SchemaConfig config;

        public SchemaStatement(Statement delegate, Connection conn, SchemaConfig config) throws SQLException {
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
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            config.checkStatement(sql);
            return super.execute(sql, autoGeneratedKeys);
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            config.checkStatement(sql);
            return super.execute(sql, columnIndexes);
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            config.checkStatement(sql);
            return super.execute(sql, columnNames);
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
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            config.checkStatement(sql);
            return super.executeUpdate(sql, autoGeneratedKeys);
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            config.checkStatement(sql);
            return super.executeUpdate(sql, columnIndexes);
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            config.checkStatement(sql);
            return super.executeUpdate(sql, columnNames);
        }

        @Override
        public void addBatch(String sql) throws SQLException {
            config.checkStatement(sql);
            super.addBatch(sql);
        }
    }

    /**
     * PreparedStatement wrapper (SQL already checked at prepare time).
     */
    private static class SchemaPreparedStatement extends AbstractPreparedStatement {
        @SuppressWarnings("unused")
        private final SchemaConfig config;

        public SchemaPreparedStatement(PreparedStatement delegate, Connection conn, SchemaConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        // SQL was already validated at prepareStatement() time
        // No additional checks needed for execute methods
    }

    /**
     * CallableStatement wrapper (SQL already checked at prepare time).
     */
    private static class SchemaCallableStatement extends AbstractCallableStatement {
        @SuppressWarnings("unused")
        private final SchemaConfig config;

        public SchemaCallableStatement(CallableStatement delegate, Connection conn, SchemaConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }

        // SQL was already validated at prepareCall() time
        // No additional checks needed for execute methods
    }
}
