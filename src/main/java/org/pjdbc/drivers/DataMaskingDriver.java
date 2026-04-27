package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractResultSet;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.JdbcUrlParser;

/**
 * DataMaskingDriver masks sensitive data in query results on-the-fly.
 *
 * <p>URL format: {@code jdbc:mask[param=value,...]:jdbc:target:...}
 *
 * <p>Masking Strategies:
 * <ul>
 *   <li>FULL - Replace entire value with mask characters (e.g., "********")</li>
 *   <li>PARTIAL - Show first/last N characters (e.g., "****1234")</li>
 *   <li>EMAIL - Mask email preserving first char and domain (e.g., "j***@example.com")</li>
 *   <li>REDACT - Replace with "[REDACTED]"</li>
 *   <li>HASH - Replace with hash prefix (e.g., "a1b2c3d4...")</li>
 * </ul>
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:mask[columns=ssn;credit_card]:jdbc:postgresql://localhost/mydb
 * jdbc:mask[columns=.*email.*,strategy=EMAIL]:jdbc:postgresql://localhost/mydb
 * jdbc:mask[columns=password;secret,strategy=REDACT]:jdbc:postgresql://localhost/mydb
 * jdbc:mask[columns=card_number,showLast=4,showFirst=0]:jdbc:mysql://localhost/db
 * </pre>
 */
@DriverCapability(
    prefix = "mask",
    description = "Masks sensitive data in query results",
    capabilities = {"masking", "security"}
)
@DriverParameter(name = "columns", type = ParameterType.STRING,
    description = "Semicolon-separated column name patterns (regex) to mask")
@DriverParameter(name = "strategy", type = ParameterType.STRING,
    description = "Masking strategy", defaultValue = "PARTIAL",
    enumValues = {"FULL", "PARTIAL", "EMAIL", "REDACT", "HASH"})
@DriverParameter(name = "mask", type = ParameterType.STRING,
    description = "Mask character", defaultValue = "*")
@DriverParameter(name = "showFirst", type = ParameterType.INTEGER,
    description = "Characters to show at start for PARTIAL", defaultValue = "0", min = 0)
@DriverParameter(name = "showLast", type = ParameterType.INTEGER,
    description = "Characters to show at end for PARTIAL", defaultValue = "4", min = 0)
public class DataMaskingDriver extends AbstractProxyDriver {

    static {
        try {
            DriverManager.registerDriver(new DataMaskingDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "mask".equals(subprotocol);
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
        return new MaskingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingStatement(delegate, conn, maskConn.getConfig(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingPreparedStatement(delegate, conn, maskConn.getConfig(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingCallableStatement(delegate, conn, maskConn.getConfig(), this);
    }

    @Override
    public ResultSet proxyResultSet(Statement stmt, ResultSet delegate) throws SQLException {
        Connection conn = stmt.getConnection();
        if (conn instanceof MaskingConnection maskConn) {
            return new MaskingResultSet(stmt, delegate, maskConn.getConfig());
        }
        return delegate;
    }

    /**
     * Masking strategy enumeration.
     */
    public enum MaskingStrategy {
        FULL,      // Replace entire value
        PARTIAL,   // Show first/last N characters
        EMAIL,     // Mask email preserving structure
        REDACT,    // Replace with [REDACTED]
        HASH       // Replace with hash
    }

    /**
     * Configuration holder for masking parameters.
     */
    public static class MaskingConfig {
        private final List<Pattern> columnPatterns;
        private final MaskingStrategy strategy;
        private final char maskChar;
        private final int showLast;
        private final int showFirst;

        public MaskingConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.columnPatterns = parsePatterns(parser.getParameter("columns", ""));
            this.strategy = parseStrategy(parser.getParameter("strategy", "PARTIAL"));
            this.maskChar = parseMaskChar(parser.getParameter("mask", "*"));
            this.showLast = parseInt(parser.getParameter("showLast", "4"));
            this.showFirst = parseInt(parser.getParameter("showFirst", "0"));
        }

        private static List<Pattern> parsePatterns(String s) {
            List<Pattern> patterns = new ArrayList<>();
            if (s != null && !s.trim().isEmpty()) {
                for (String pattern : s.split(";")) {
                    String trimmed = pattern.trim();
                    if (!trimmed.isEmpty()) {
                        patterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
                    }
                }
            }
            return patterns;
        }

        private static MaskingStrategy parseStrategy(String s) {
            try {
                return MaskingStrategy.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MaskingStrategy.PARTIAL;
            }
        }

        private static char parseMaskChar(String s) {
            return (s != null && !s.isEmpty()) ? s.charAt(0) : '*';
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 4; }
        }

        public List<Pattern> getColumnPatterns() { return columnPatterns; }
        public MaskingStrategy getStrategy() { return strategy; }
        public char getMaskChar() { return maskChar; }
        public int getShowLast() { return showLast; }
        public int getShowFirst() { return showFirst; }

        /**
         * Check if a column name matches any masking pattern.
         */
        public boolean shouldMask(String columnName) {
            if (columnName == null || columnPatterns.isEmpty()) return false;
            for (Pattern p : columnPatterns) {
                if (p.matcher(columnName).matches()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Mask a value according to the configured strategy.
         */
        public String maskValue(String value) {
            if (value == null) return null;
            if (value.isEmpty()) return value;

            return switch (strategy) {
                case FULL -> repeat(maskChar, value.length());
                case PARTIAL -> maskPartial(value);
                case EMAIL -> maskEmail(value);
                case REDACT -> "[REDACTED]";
                case HASH -> maskHash(value);
                default -> maskPartial(value);
            };
        }

        private String maskPartial(String value) {
            int len = value.length();
            int maskLen = len - showFirst - showLast;

            if (maskLen <= 0) {
                // Value too short, mask entirely
                return repeat(maskChar, len);
            }

            StringBuilder sb = new StringBuilder();
            if (showFirst > 0) {
                sb.append(value.substring(0, Math.min(showFirst, len)));
            }
            sb.append(repeat(maskChar, maskLen));
            if (showLast > 0 && showFirst + showLast <= len) {
                sb.append(value.substring(len - showLast));
            }
            return sb.toString();
        }

        private String maskEmail(String value) {
            int atIndex = value.indexOf('@');
            if (atIndex <= 0) {
                // Not a valid email format, use partial masking
                return maskPartial(value);
            }

            String local = value.substring(0, atIndex);
            String domain = value.substring(atIndex);

            if (local.length() <= 1) {
                return local + repeat(maskChar, 3) + domain;
            }

            return local.charAt(0) + repeat(maskChar, local.length() - 1) + domain;
        }

        private String maskHash(String value) {
            int hash = value.hashCode();
            // Use bitwise AND to avoid overflow when hash is Integer.MIN_VALUE
            String hex = Integer.toHexString(hash & 0x7FFFFFFF);
            // Pad to 8 characters and add ellipsis
            while (hex.length() < 8) {
                hex = "0" + hex;
            }
            return hex + "...";
        }

        private String repeat(char c, int count) {
            StringBuilder sb = new StringBuilder(count);
            for (int i = 0; i < count; i++) {
                sb.append(c);
            }
            return sb.toString();
        }
    }

    /**
     * Connection wrapper that holds masking configuration.
     */
    private class MaskingConnection extends AbstractConnection {
        private final MaskingConfig config;

        public MaskingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new MaskingConfig(url);
        }

        public MaskingConfig getConfig() {
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
     * Statement wrapper that returns masking result sets.
     */
    private class MaskingStatement extends AbstractStatement {
        private final MaskingConfig config;
        private final DataMaskingDriver driver;

        public MaskingStatement(Statement delegate, Connection conn, MaskingConfig config, DataMaskingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.driver = driver;
        }

        @Override
        protected ResultSet wrap(ResultSet rs) throws SQLException {
            return new MaskingResultSet(this, rs, config);
        }
    }

    /**
     * PreparedStatement wrapper that returns masking result sets.
     */
    private class MaskingPreparedStatement extends AbstractPreparedStatement {
        private final MaskingConfig config;
        private final DataMaskingDriver driver;

        public MaskingPreparedStatement(PreparedStatement delegate, Connection conn, MaskingConfig config, DataMaskingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.driver = driver;
        }

        @Override
        protected ResultSet wrap(ResultSet rs) throws SQLException {
            return new MaskingResultSet(this, rs, config);
        }
    }

    /**
     * CallableStatement wrapper that returns masking result sets.
     */
    private class MaskingCallableStatement extends AbstractCallableStatement {
        private final MaskingConfig config;
        private final DataMaskingDriver driver;

        public MaskingCallableStatement(CallableStatement delegate, Connection conn, MaskingConfig config, DataMaskingDriver driver) throws SQLException {
            super(delegate, conn);
            this.config = config;
            this.driver = driver;
        }

        @Override
        protected ResultSet wrap(ResultSet rs) throws SQLException {
            return new MaskingResultSet(this, rs, config);
        }
    }

    /**
     * ResultSet wrapper that masks values from matched columns.
     *
     * <p>Masking is applied to all getter methods to prevent bypass attacks:</p>
     * <ul>
     *   <li>String methods (getString, getNString): return masked string</li>
     *   <li>Bytes (getBytes): return masked string as UTF-8 bytes</li>
     *   <li>Streams (getCharacterStream, getBinaryStream, etc.): return stream of masked content</li>
     *   <li>Numeric methods (getInt, getLong, getBigDecimal, etc.): throw SQLException</li>
     *   <li>Date/time methods (getDate, getTime, getTimestamp): throw SQLException</li>
     *   <li>Boolean (getBoolean): throw SQLException</li>
     *   <li>Object (getObject): returns masked string if String, throws for other types</li>
     * </ul>
     *
     * <p><strong>Rationale for throwing on non-string types:</strong> Returning default values
     * (0 for numbers, false for boolean) could be confused with actual data. Throwing SQLException
     * makes it explicit that the column is masked. Use getString() to retrieve the masked
     * representation of any column type.</p>
     */
    private static class MaskingResultSet extends AbstractResultSet {
        private final MaskingConfig config;
        private boolean[] maskedColumns;

        public MaskingResultSet(Statement stmt, ResultSet delegate, MaskingConfig config) throws SQLException {
            super(stmt, delegate);
            this.config = config;
            initMaskedColumns(delegate);
        }

        private void initMaskedColumns(ResultSet rs) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            maskedColumns = new boolean[columnCount + 1]; // 1-indexed

            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnName(i);
                String columnLabel = meta.getColumnLabel(i);
                maskedColumns[i] = config.shouldMask(columnName) || config.shouldMask(columnLabel);
            }
        }

        private boolean shouldMaskColumn(int columnIndex) {
            return columnIndex > 0 && columnIndex < maskedColumns.length && maskedColumns[columnIndex];
        }

        /**
         * Throw SQLException for masked column access via non-string getter.
         */
        private void throwMaskedColumnException(String columnRef, String getterName) throws SQLException {
            throw new SQLException(
                "DataMaskingDriver: Column '" + columnRef + "' is masked. " +
                "Cannot retrieve via " + getterName + "() - use getString() to get the masked value.",
                "22000"); // Data exception SQL state
        }

        // === STRING METHODS ===

        @Override
        public String getString(int columnIndex) throws SQLException {
            String value = super.getString(columnIndex);
            if (shouldMaskColumn(columnIndex) && value != null) {
                return config.maskValue(value);
            }
            return value;
        }

        @Override
        public String getString(String columnLabel) throws SQLException {
            String value = super.getString(columnLabel);
            if (config.shouldMask(columnLabel) && value != null) {
                return config.maskValue(value);
            }
            return value;
        }

        @Override
        public String getNString(int columnIndex) throws SQLException {
            String value = super.getNString(columnIndex);
            if (shouldMaskColumn(columnIndex) && value != null) {
                return config.maskValue(value);
            }
            return value;
        }

        @Override
        public String getNString(String columnLabel) throws SQLException {
            String value = super.getNString(columnLabel);
            if (config.shouldMask(columnLabel) && value != null) {
                return config.maskValue(value);
            }
            return value;
        }

        // === OBJECT METHODS ===

        @Override
        public Object getObject(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                Object value = super.getObject(columnIndex);
                if (value == null) return null;
                if (value instanceof String s) {
                    return config.maskValue(s);
                }
                // Non-string type in masked column - throw to prevent data leak
                throwMaskedColumnException(String.valueOf(columnIndex), "getObject");
            }
            return super.getObject(columnIndex);
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                Object value = super.getObject(columnLabel);
                if (value == null) return null;
                if (value instanceof String s) {
                    return config.maskValue(s);
                }
                // Non-string type in masked column - throw to prevent data leak
                throwMaskedColumnException(columnLabel, "getObject");
            }
            return super.getObject(columnLabel);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                if (type == String.class) {
                    String value = super.getString(columnIndex);
                    if (value == null) return null;
                    return (T) config.maskValue(value);
                }
                throwMaskedColumnException(String.valueOf(columnIndex), "getObject");
            }
            return super.getObject(columnIndex, type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                if (type == String.class) {
                    String value = super.getString(columnLabel);
                    if (value == null) return null;
                    return (T) config.maskValue(value);
                }
                throwMaskedColumnException(columnLabel, "getObject");
            }
            return super.getObject(columnLabel, type);
        }

        @Override
        public byte getByte(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getByte");
            return super.getByte(columnIndex);
        }

        @Override
        public byte getByte(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getByte");
            return super.getByte(columnLabel);
        }

        @Override
        public short getShort(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getShort");
            return super.getShort(columnIndex);
        }

        @Override
        public short getShort(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getShort");
            return super.getShort(columnLabel);
        }

        @Override
        public int getInt(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getInt");
            return super.getInt(columnIndex);
        }

        @Override
        public int getInt(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getInt");
            return super.getInt(columnLabel);
        }

        @Override
        public long getLong(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getLong");
            return super.getLong(columnIndex);
        }

        @Override
        public long getLong(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getLong");
            return super.getLong(columnLabel);
        }

        @Override
        public float getFloat(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getFloat");
            return super.getFloat(columnIndex);
        }

        @Override
        public float getFloat(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getFloat");
            return super.getFloat(columnLabel);
        }

        @Override
        public double getDouble(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getDouble");
            return super.getDouble(columnIndex);
        }

        @Override
        public double getDouble(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getDouble");
            return super.getDouble(columnLabel);
        }

        @Override
        public java.math.BigDecimal getBigDecimal(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getBigDecimal");
            return super.getBigDecimal(columnIndex);
        }

        @Override
        public java.math.BigDecimal getBigDecimal(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getBigDecimal");
            return super.getBigDecimal(columnLabel);
        }

        @Override
        @SuppressWarnings("deprecation")
        public java.math.BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getBigDecimal");
            return super.getBigDecimal(columnIndex, scale);
        }

        @Override
        @SuppressWarnings("deprecation")
        public java.math.BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getBigDecimal");
            return super.getBigDecimal(columnLabel, scale);
        }

        // === BYTES - return masked string as bytes ===

        @Override
        public byte[] getBytes(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getString(columnIndex);
                if (value == null) return null;
                return config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return super.getBytes(columnIndex);
        }

        @Override
        public byte[] getBytes(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getString(columnLabel);
                if (value == null) return null;
                return config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return super.getBytes(columnLabel);
        }

        // === BOOLEAN - throw SQLException for masked columns ===

        @Override
        public boolean getBoolean(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getBoolean");
            return super.getBoolean(columnIndex);
        }

        @Override
        public boolean getBoolean(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getBoolean");
            return super.getBoolean(columnLabel);
        }

        // === DATE/TIME METHODS - throw SQLException for masked columns ===

        @Override
        public java.sql.Date getDate(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getDate");
            return super.getDate(columnIndex);
        }

        @Override
        public java.sql.Date getDate(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getDate");
            return super.getDate(columnLabel);
        }

        @Override
        public java.sql.Date getDate(int columnIndex, java.util.Calendar cal) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getDate");
            return super.getDate(columnIndex, cal);
        }

        @Override
        public java.sql.Date getDate(String columnLabel, java.util.Calendar cal) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getDate");
            return super.getDate(columnLabel, cal);
        }

        @Override
        public java.sql.Time getTime(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getTime");
            return super.getTime(columnIndex);
        }

        @Override
        public java.sql.Time getTime(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getTime");
            return super.getTime(columnLabel);
        }

        @Override
        public java.sql.Time getTime(int columnIndex, java.util.Calendar cal) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getTime");
            return super.getTime(columnIndex, cal);
        }

        @Override
        public java.sql.Time getTime(String columnLabel, java.util.Calendar cal) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getTime");
            return super.getTime(columnLabel, cal);
        }

        @Override
        public java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getTimestamp");
            return super.getTimestamp(columnIndex);
        }

        @Override
        public java.sql.Timestamp getTimestamp(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getTimestamp");
            return super.getTimestamp(columnLabel);
        }

        @Override
        public java.sql.Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getTimestamp");
            return super.getTimestamp(columnIndex, cal);
        }

        @Override
        public java.sql.Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getTimestamp");
            return super.getTimestamp(columnLabel, cal);
        }

        // === CHARACTER STREAMS - return stream of masked content ===

        @Override
        public java.io.Reader getCharacterStream(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getString(columnIndex);
                if (value == null) return null;
                return new java.io.StringReader(config.maskValue(value));
            }
            return super.getCharacterStream(columnIndex);
        }

        @Override
        public java.io.Reader getCharacterStream(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getString(columnLabel);
                if (value == null) return null;
                return new java.io.StringReader(config.maskValue(value));
            }
            return super.getCharacterStream(columnLabel);
        }

        @Override
        public java.io.Reader getNCharacterStream(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getNString(columnIndex);
                if (value == null) return null;
                return new java.io.StringReader(config.maskValue(value));
            }
            return super.getNCharacterStream(columnIndex);
        }

        @Override
        public java.io.Reader getNCharacterStream(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getNString(columnLabel);
                if (value == null) return null;
                return new java.io.StringReader(config.maskValue(value));
            }
            return super.getNCharacterStream(columnLabel);
        }

        // === BINARY STREAMS - return stream of masked bytes ===

        @Override
        public java.io.InputStream getAsciiStream(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getString(columnIndex);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getAsciiStream(columnIndex);
        }

        @Override
        public java.io.InputStream getAsciiStream(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getString(columnLabel);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getAsciiStream(columnLabel);
        }

        @Override
        public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getString(columnIndex);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getBinaryStream(columnIndex);
        }

        @Override
        public java.io.InputStream getBinaryStream(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getString(columnLabel);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getBinaryStream(columnLabel);
        }

        @Override
        @SuppressWarnings("deprecation")
        public java.io.InputStream getUnicodeStream(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) {
                String value = super.getString(columnIndex);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_16);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getUnicodeStream(columnIndex);
        }

        @Override
        @SuppressWarnings("deprecation")
        public java.io.InputStream getUnicodeStream(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) {
                String value = super.getString(columnLabel);
                if (value == null) return null;
                byte[] maskedBytes = config.maskValue(value).getBytes(java.nio.charset.StandardCharsets.UTF_16);
                return new java.io.ByteArrayInputStream(maskedBytes);
            }
            return super.getUnicodeStream(columnLabel);
        }

        // === COMPLEX TYPES - throw SQLException for masked columns ===

        @Override
        public java.sql.Blob getBlob(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getBlob");
            return super.getBlob(columnIndex);
        }

        @Override
        public java.sql.Blob getBlob(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getBlob");
            return super.getBlob(columnLabel);
        }

        @Override
        public java.sql.Clob getClob(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getClob");
            return super.getClob(columnIndex);
        }

        @Override
        public java.sql.Clob getClob(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getClob");
            return super.getClob(columnLabel);
        }

        @Override
        public java.sql.NClob getNClob(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getNClob");
            return super.getNClob(columnIndex);
        }

        @Override
        public java.sql.NClob getNClob(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getNClob");
            return super.getNClob(columnLabel);
        }

        @Override
        public java.sql.Array getArray(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getArray");
            return super.getArray(columnIndex);
        }

        @Override
        public java.sql.Array getArray(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getArray");
            return super.getArray(columnLabel);
        }

        @Override
        public java.sql.Ref getRef(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getRef");
            return super.getRef(columnIndex);
        }

        @Override
        public java.sql.Ref getRef(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getRef");
            return super.getRef(columnLabel);
        }

        @Override
        public java.sql.RowId getRowId(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getRowId");
            return super.getRowId(columnIndex);
        }

        @Override
        public java.sql.RowId getRowId(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getRowId");
            return super.getRowId(columnLabel);
        }

        @Override
        public java.sql.SQLXML getSQLXML(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getSQLXML");
            return super.getSQLXML(columnIndex);
        }

        @Override
        public java.sql.SQLXML getSQLXML(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getSQLXML");
            return super.getSQLXML(columnLabel);
        }

        @Override
        public java.net.URL getURL(int columnIndex) throws SQLException {
            if (shouldMaskColumn(columnIndex)) throwMaskedColumnException(String.valueOf(columnIndex), "getURL");
            return super.getURL(columnIndex);
        }

        @Override
        public java.net.URL getURL(String columnLabel) throws SQLException {
            if (config.shouldMask(columnLabel)) throwMaskedColumnException(columnLabel, "getURL");
            return super.getURL(columnLabel);
        }

    }
}
