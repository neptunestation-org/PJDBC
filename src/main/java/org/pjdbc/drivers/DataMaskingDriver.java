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

        @Override public String getString(int i) throws SQLException {
            String v = super.getString(i); return (shouldMaskColumn(i) && v != null) ? config.maskValue(v) : v;}
        @Override public String getString(String l) throws SQLException {return getString(findColumn(l));}
        @Override public String getNString(String l) throws SQLException {return getNString(findColumn(l));}
        @Override public String getNString(int i) throws SQLException {
            String v = super.getNString(i); return (shouldMaskColumn(i) && v != null) ? config.maskValue(v) : v;}

        @Override public Object getObject(int i) throws SQLException {
            if (!shouldMaskColumn(i)) return super.getObject(i);
            Object v = super.getObject(i);
            if (v == null) return null;
            if (v instanceof String s) return config.maskValue(s);
            throwMaskedColumnException(String.valueOf(i), "getObject"); return null;}
        @Override public Object getObject(String l) throws SQLException {return getObject(findColumn(l));}
        @Override public Object getObject(String l, java.util.Map<String,Class<?>> m) throws SQLException {return getObject(findColumn(l), m);}
        @Override public <T> T getObject(String l, Class<T> t) throws SQLException {return getObject(findColumn(l), t);}
        @Override public <T> T getObject(int i, Class<T> t) throws SQLException {
            if (shouldMaskColumn(i)) {
                if (t.equals(String.class)) return t.cast(getString(i));
                throwMaskedColumnException(String.valueOf(i), "getObject");}
            return super.getObject(i, t);}

        @Override public byte getByte(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getByte"); return super.getByte(i);}
        @Override public byte getByte(String l) throws SQLException {return getByte(findColumn(l));}
        @Override public short getShort(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getShort"); return super.getShort(i);}
        @Override public short getShort(String l) throws SQLException {return getShort(findColumn(l));}
        @Override public int getInt(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getInt"); return super.getInt(i);}
        @Override public int getInt(String l) throws SQLException {return getInt(findColumn(l));}
        @Override public long getLong(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getLong"); return super.getLong(i);}
        @Override public long getLong(String l) throws SQLException {return getLong(findColumn(l));}
        @Override public float getFloat(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getFloat"); return super.getFloat(i);}
        @Override public float getFloat(String l) throws SQLException {return getFloat(findColumn(l));}
        @Override public double getDouble(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getDouble"); return super.getDouble(i);}
        @Override public double getDouble(String l) throws SQLException {return getDouble(findColumn(l));}
        @Override public java.math.BigDecimal getBigDecimal(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getBigDecimal"); return super.getBigDecimal(i);}
        @Override public java.math.BigDecimal getBigDecimal(String l) throws SQLException {return getBigDecimal(findColumn(l));}
        @Override @SuppressWarnings("deprecation") public java.math.BigDecimal getBigDecimal(int i, int s) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getBigDecimal"); return super.getBigDecimal(i, s);}
        @Override @SuppressWarnings("deprecation") public java.math.BigDecimal getBigDecimal(String l, int s) throws SQLException {return getBigDecimal(findColumn(l), s);}

        @Override public byte[] getBytes(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getString(i); return v == null ? null : config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
            return super.getBytes(i);}
        @Override public byte[] getBytes(String l) throws SQLException {return getBytes(findColumn(l));}

        @Override public boolean getBoolean(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getBoolean"); return super.getBoolean(i);}
        @Override public boolean getBoolean(String l) throws SQLException {return getBoolean(findColumn(l));}

        @Override public java.sql.Date getDate(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getDate"); return super.getDate(i);}
        @Override public java.sql.Date getDate(String l) throws SQLException {return getDate(findColumn(l));}
        @Override public java.sql.Date getDate(int i, java.util.Calendar c) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getDate"); return super.getDate(i, c);}
        @Override public java.sql.Date getDate(String l, java.util.Calendar c) throws SQLException {return getDate(findColumn(l), c);}
        @Override public java.sql.Time getTime(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getTime"); return super.getTime(i);}
        @Override public java.sql.Time getTime(String l) throws SQLException {return getTime(findColumn(l));}
        @Override public java.sql.Time getTime(int i, java.util.Calendar c) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getTime"); return super.getTime(i, c);}
        @Override public java.sql.Time getTime(String l, java.util.Calendar c) throws SQLException {return getTime(findColumn(l), c);}
        @Override public java.sql.Timestamp getTimestamp(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getTimestamp"); return super.getTimestamp(i);}
        @Override public java.sql.Timestamp getTimestamp(String l) throws SQLException {return getTimestamp(findColumn(l));}
        @Override public java.sql.Timestamp getTimestamp(int i, java.util.Calendar c) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getTimestamp"); return super.getTimestamp(i, c);}
        @Override public java.sql.Timestamp getTimestamp(String l, java.util.Calendar c) throws SQLException {return getTimestamp(findColumn(l), c);}

        @Override public java.io.Reader getCharacterStream(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getString(i); return v == null ? null : new java.io.StringReader(config.maskValue(v));}
            return super.getCharacterStream(i);}
        @Override public java.io.Reader getCharacterStream(String l) throws SQLException {return getCharacterStream(findColumn(l));}
        @Override public java.io.Reader getNCharacterStream(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getNString(i); return v == null ? null : new java.io.StringReader(config.maskValue(v));}
            return super.getNCharacterStream(i);}
        @Override public java.io.Reader getNCharacterStream(String l) throws SQLException {return getNCharacterStream(findColumn(l));}

        @Override public java.io.InputStream getAsciiStream(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getString(i); return v == null ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            return super.getAsciiStream(i);}
        @Override public java.io.InputStream getAsciiStream(String l) throws SQLException {return getAsciiStream(findColumn(l));}
        @Override public java.io.InputStream getBinaryStream(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getString(i); return v == null ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            return super.getBinaryStream(i);}
        @Override public java.io.InputStream getBinaryStream(String l) throws SQLException {return getBinaryStream(findColumn(l));}
        @Override @SuppressWarnings("deprecation") public java.io.InputStream getUnicodeStream(int i) throws SQLException {
            if (shouldMaskColumn(i)) {
                String v = super.getString(i); return v == null ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_16));}
            return super.getUnicodeStream(i);}
        @Override @SuppressWarnings("deprecation") public java.io.InputStream getUnicodeStream(String l) throws SQLException {return getUnicodeStream(findColumn(l));}

        @Override public java.sql.Blob getBlob(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getBlob"); return super.getBlob(i);}
        @Override public java.sql.Blob getBlob(String l) throws SQLException {return getBlob(findColumn(l));}
        @Override public java.sql.Clob getClob(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getClob"); return super.getClob(i);}
        @Override public java.sql.Clob getClob(String l) throws SQLException {return getClob(findColumn(l));}
        @Override public java.sql.NClob getNClob(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getNClob"); return super.getNClob(i);}
        @Override public java.sql.NClob getNClob(String l) throws SQLException {return getNClob(findColumn(l));}
        @Override public java.sql.Array getArray(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getArray"); return super.getArray(i);}
        @Override public java.sql.Array getArray(String l) throws SQLException {return getArray(findColumn(l));}
        @Override public java.sql.Ref getRef(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getRef"); return super.getRef(i);}
        @Override public java.sql.Ref getRef(String l) throws SQLException {return getRef(findColumn(l));}
        @Override public java.sql.SQLXML getSQLXML(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getSQLXML"); return super.getSQLXML(i);}
        @Override public java.sql.SQLXML getSQLXML(String l) throws SQLException {return getSQLXML(findColumn(l));}
        @Override public java.net.URL getURL(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getURL"); return super.getURL(i);}
        @Override public java.net.URL getURL(String l) throws SQLException {return getURL(findColumn(l));}
        @Override public java.sql.RowId getRowId(int i) throws SQLException {if (shouldMaskColumn(i)) throwMaskedColumnException(String.valueOf(i), "getRowId"); return super.getRowId(i);}
        @Override public java.sql.RowId getRowId(String l) throws SQLException {return getRowId(findColumn(l));}
    }
}
