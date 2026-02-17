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
    protected Connection proxyConnection(Connection delegate, String url, Properties info, Driver driver) throws SQLException {
        return new MaskingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingStatement(delegate, conn, maskConn.getConfig());
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingPreparedStatement(delegate, conn, maskConn.getConfig());
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        MaskingConnection maskConn = (MaskingConnection) conn;
        return new MaskingCallableStatement(delegate, conn, maskConn.getConfig());
    }

    @Override
    public ResultSet proxyResultSet(Statement stmt, ResultSet delegate) throws SQLException {
        Connection conn = stmt.getConnection();
        if (conn instanceof MaskingConnection maskConn) {
            return new MaskingResultSet(stmt, delegate, maskConn.getConfig());
        }
        return delegate;
    }

    public enum MaskingStrategy { FULL, PARTIAL, EMAIL, REDACT, HASH }

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
                    if (!trimmed.isEmpty()) patterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
                }
            }
            return patterns;
        }

        private static MaskingStrategy parseStrategy(String s) {
            try { return MaskingStrategy.valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return MaskingStrategy.PARTIAL; }
        }

        private static char parseMaskChar(String s) { return (s != null && !s.isEmpty()) ? s.charAt(0) : '*'; }
        private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 4; } }

        public boolean shouldMask(String columnName) {
            if (columnName == null || columnPatterns.isEmpty()) return false;
            for (Pattern p : columnPatterns) {
                if (p.matcher(columnName).matches()) return true;
            }
            return false;
        }

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
            if (maskLen <= 0) return repeat(maskChar, len);
            StringBuilder sb = new StringBuilder();
            if (showFirst > 0) sb.append(value.substring(0, Math.min(showFirst, len)));
            sb.append(repeat(maskChar, maskLen));
            if (showLast > 0 && showFirst + showLast <= len) sb.append(value.substring(len - showLast));
            return sb.toString();
        }

        private String maskEmail(String value) {
            int atIndex = value.indexOf('@');
            if (atIndex <= 0) return maskPartial(value);
            String local = value.substring(0, atIndex);
            String domain = value.substring(atIndex);
            if (local.length() <= 1) return local + repeat(maskChar, 3) + domain;
            return local.charAt(0) + repeat(maskChar, local.length() - 1) + domain;
        }

        private String maskHash(String value) {
            int hash = value.hashCode();
            String hex = Integer.toHexString(hash & 0x7FFFFFFF);
            while (hex.length() < 8) hex = "0" + hex;
            return hex + "...";
        }

        private String repeat(char c, int count) {
            StringBuilder sb = new StringBuilder(count);
            for (int i = 0; i < count; i++) sb.append(c);
            return sb.toString();
        }

        public MaskingStrategy getStrategy() { return strategy; }
        public char getMaskChar() { return maskChar; }
        public int getShowLast() { return showLast; }
        public int getShowFirst() { return showFirst; }
        public List<Pattern> getColumnPatterns() { return columnPatterns; }
    }

    private class MaskingConnection extends AbstractConnection {
        private final MaskingConfig config;
        public MaskingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.config = new MaskingConfig(url);
        }
        public MaskingConfig getConfig() { return config; }
        @Override public Statement createStatement() throws SQLException { return proxyStatement(getDelegate().createStatement(), this); }
        @Override public Statement createStatement(int rsType, int rsConc) throws SQLException { return proxyStatement(getDelegate().createStatement(rsType, rsConc), this); }
        @Override public Statement createStatement(int rsType, int rsConc, int rsHold) throws SQLException { return proxyStatement(getDelegate().createStatement(rsType, rsConc, rsHold), this); }
        @Override public PreparedStatement prepareStatement(String sql) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql), this); }
        @Override public PreparedStatement prepareStatement(String sql, int autoKeys) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql, autoKeys), this); }
        @Override public PreparedStatement prepareStatement(String sql, int[] colIdx) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql, colIdx), this); }
        @Override public PreparedStatement prepareStatement(String sql, int rsType, int rsConc) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql, rsType, rsConc), this); }
        @Override public PreparedStatement prepareStatement(String sql, int rsType, int rsConc, int rsHold) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql, rsType, rsConc, rsHold), this); }
        @Override public PreparedStatement prepareStatement(String sql, String[] colNames) throws SQLException { return proxyPreparedStatement(getDelegate().prepareStatement(sql, colNames), this); }
        @Override public CallableStatement prepareCall(String sql) throws SQLException { return proxyCallableStatement(getDelegate().prepareCall(sql), this); }
        @Override public CallableStatement prepareCall(String sql, int rsType, int rsConc) throws SQLException { return proxyCallableStatement(getDelegate().prepareCall(sql, rsType, rsConc), this); }
        @Override public CallableStatement prepareCall(String sql, int rsType, int rsConc, int rsHold) throws SQLException { return proxyCallableStatement(getDelegate().prepareCall(sql, rsType, rsConc, rsHold), this); }
    }

    private class MaskingStatement extends AbstractStatement {
        private final MaskingConfig config;
        public MaskingStatement(Statement delegate, Connection conn, MaskingConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }
        @Override protected ResultSet wrap(ResultSet rs) throws SQLException { return new MaskingResultSet(this, rs, config); }
    }

    private class MaskingPreparedStatement extends AbstractPreparedStatement {
        private final MaskingConfig config;
        public MaskingPreparedStatement(PreparedStatement delegate, Connection conn, MaskingConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }
        @Override protected ResultSet wrap(ResultSet rs) throws SQLException { return new MaskingResultSet(this, rs, config); }
    }

    private class MaskingCallableStatement extends AbstractCallableStatement {
        private final MaskingConfig config;
        public MaskingCallableStatement(CallableStatement delegate, Connection conn, MaskingConfig config) throws SQLException {
            super(delegate, conn);
            this.config = config;
        }
        @Override protected ResultSet wrap(ResultSet rs) throws SQLException { return new MaskingResultSet(this, rs, config); }
    }

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
            maskedColumns = new boolean[columnCount + 1];
            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnName(i);
                String columnLabel = meta.getColumnLabel(i);
                maskedColumns[i] = config.shouldMask(columnName) || config.shouldMask(columnLabel);
            }
        }

        private boolean shouldMaskColumn(int columnIndex) {
            return columnIndex > 0 && columnIndex < maskedColumns.length && maskedColumns[columnIndex];
        }

        private boolean shouldMaskColumn(String columnLabel) throws SQLException {
            return shouldMaskColumn(findColumn(columnLabel));
        }

        private void throwMaskedColumnException(String columnRef, String getterName) throws SQLException {
            throw new SQLException("DataMaskingDriver: Column '" + columnRef + "' is masked. Cannot retrieve via " + getterName + "() - use getString() to get the masked value.", "22000");
        }

        @Override public String getString(int idx) throws SQLException { String v = super.getString(idx); return (shouldMaskColumn(idx) && v != null) ? config.maskValue(v) : v; }
        @Override public String getString(String lbl) throws SQLException { String v = super.getString(lbl); return (shouldMaskColumn(lbl) && v != null) ? config.maskValue(v) : v; }
        @Override public String getNString(int idx) throws SQLException { String v = super.getNString(idx); return (shouldMaskColumn(idx) && v != null) ? config.maskValue(v) : v; }
        @Override public String getNString(String lbl) throws SQLException { String v = super.getNString(lbl); return (shouldMaskColumn(lbl) && v != null) ? config.maskValue(v) : v; }

        @Override public Object getObject(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                Object v = super.getObject(idx);
                if (v == null) return null;
                if (v instanceof String s) return config.maskValue(s);
                throwMaskedColumnException(String.valueOf(idx), "getObject");
            }
            return super.getObject(idx);
        }

        @Override public Object getObject(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                Object v = super.getObject(lbl);
                if (v == null) return null;
                if (v instanceof String s) return config.maskValue(s);
                throwMaskedColumnException(lbl, "getObject");
            }
            return super.getObject(lbl);
        }

        @Override public byte getByte(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getByte"); return super.getByte(idx); }
        @Override public byte getByte(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getByte"); return super.getByte(lbl); }
        @Override public short getShort(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getShort"); return super.getShort(idx); }
        @Override public short getShort(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getShort"); return super.getShort(lbl); }
        @Override public int getInt(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getInt"); return super.getInt(idx); }
        @Override public int getInt(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getInt"); return super.getInt(lbl); }
        @Override public long getLong(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getLong"); return super.getLong(idx); }
        @Override public long getLong(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getLong"); return super.getLong(lbl); }
        @Override public float getFloat(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getFloat"); return super.getFloat(idx); }
        @Override public float getFloat(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getFloat"); return super.getFloat(lbl); }
        @Override public double getDouble(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getDouble"); return super.getDouble(idx); }
        @Override public double getDouble(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getDouble"); return super.getDouble(lbl); }
        @Override public java.math.BigDecimal getBigDecimal(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getBigDecimal"); return super.getBigDecimal(idx); }
        @Override public java.math.BigDecimal getBigDecimal(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getBigDecimal"); return super.getBigDecimal(lbl); }
        @Override @SuppressWarnings("deprecation") public java.math.BigDecimal getBigDecimal(int idx, int s) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getBigDecimal"); return super.getBigDecimal(idx, s); }
        @Override @SuppressWarnings("deprecation") public java.math.BigDecimal getBigDecimal(String lbl, int s) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getBigDecimal"); return super.getBigDecimal(lbl, s); }

        @Override public byte[] getBytes(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getString(idx);
                return (v == null) ? null : config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return super.getBytes(idx);
        }

        @Override public byte[] getBytes(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getString(lbl);
                return (v == null) ? null : config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return super.getBytes(lbl);
        }

        @Override public boolean getBoolean(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getBoolean"); return super.getBoolean(idx); }
        @Override public boolean getBoolean(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getBoolean"); return super.getBoolean(lbl); }
        @Override public java.sql.Date getDate(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getDate"); return super.getDate(idx); }
        @Override public java.sql.Date getDate(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getDate"); return super.getDate(lbl); }
        @Override public java.sql.Date getDate(int idx, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getDate"); return super.getDate(idx, c); }
        @Override public java.sql.Date getDate(String lbl, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getDate"); return super.getDate(lbl, c); }
        @Override public java.sql.Time getTime(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getTime"); return super.getTime(idx); }
        @Override public java.sql.Time getTime(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getTime"); return super.getTime(lbl); }
        @Override public java.sql.Time getTime(int idx, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getTime"); return super.getTime(idx, c); }
        @Override public java.sql.Time getTime(String lbl, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getTime"); return super.getTime(lbl, c); }
        @Override public java.sql.Timestamp getTimestamp(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getTimestamp"); return super.getTimestamp(idx); }
        @Override public java.sql.Timestamp getTimestamp(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getTimestamp"); return super.getTimestamp(lbl); }
        @Override public java.sql.Timestamp getTimestamp(int idx, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getTimestamp"); return super.getTimestamp(idx, c); }
        @Override public java.sql.Timestamp getTimestamp(String lbl, java.util.Calendar c) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getTimestamp"); return super.getTimestamp(lbl, c); }

        @Override public java.io.Reader getCharacterStream(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getString(idx);
                return (v == null) ? null : new java.io.StringReader(config.maskValue(v));
            }
            return super.getCharacterStream(idx);
        }

        @Override public java.io.Reader getCharacterStream(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getString(lbl);
                return (v == null) ? null : new java.io.StringReader(config.maskValue(v));
            }
            return super.getCharacterStream(lbl);
        }

        @Override public java.io.Reader getNCharacterStream(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getNString(idx);
                return (v == null) ? null : new java.io.StringReader(config.maskValue(v));
            }
            return super.getNCharacterStream(idx);
        }

        @Override public java.io.Reader getNCharacterStream(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getNString(lbl);
                return (v == null) ? null : new java.io.StringReader(config.maskValue(v));
            }
            return super.getNCharacterStream(lbl);
        }

        @Override public java.io.InputStream getAsciiStream(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getString(idx);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            return super.getAsciiStream(idx);
        }

        @Override public java.io.InputStream getAsciiStream(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getString(lbl);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            return super.getAsciiStream(lbl);
        }

        @Override public java.io.InputStream getBinaryStream(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getString(idx);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return super.getBinaryStream(idx);
        }

        @Override public java.io.InputStream getBinaryStream(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getString(lbl);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return super.getBinaryStream(lbl);
        }

        @Override @SuppressWarnings("deprecation") public java.io.InputStream getUnicodeStream(int idx) throws SQLException {
            if (shouldMaskColumn(idx)) {
                String v = super.getString(idx);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_16));
            }
            return super.getUnicodeStream(idx);
        }

        @Override @SuppressWarnings("deprecation") public java.io.InputStream getUnicodeStream(String lbl) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                String v = super.getString(lbl);
                return (v == null) ? null : new java.io.ByteArrayInputStream(config.maskValue(v).getBytes(java.nio.charset.StandardCharsets.UTF_16));
            }
            return super.getUnicodeStream(lbl);
        }

        @Override public java.sql.Blob getBlob(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getBlob"); return super.getBlob(idx); }
        @Override public java.sql.Blob getBlob(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getBlob"); return super.getBlob(lbl); }
        @Override public java.sql.Clob getClob(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getClob"); return super.getClob(idx); }
        @Override public java.sql.Clob getClob(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getClob"); return super.getClob(lbl); }
        @Override public java.sql.NClob getNClob(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getNClob"); return super.getNClob(idx); }
        @Override public java.sql.NClob getNClob(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getNClob"); return super.getNClob(lbl); }
        @Override public java.sql.Array getArray(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getArray"); return super.getArray(idx); }
        @Override public java.sql.Array getArray(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getArray"); return super.getArray(lbl); }
        @Override public java.net.URL getURL(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getURL"); return super.getURL(idx); }
        @Override public java.net.URL getURL(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getURL"); return super.getURL(lbl); }
        @Override public java.sql.RowId getRowId(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getRowId"); return super.getRowId(idx); }
        @Override public java.sql.RowId getRowId(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getRowId"); return super.getRowId(lbl); }
        @Override public java.sql.SQLXML getSQLXML(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getSQLXML"); return super.getSQLXML(idx); }
        @Override public java.sql.SQLXML getSQLXML(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getSQLXML"); return super.getSQLXML(lbl); }
        @Override public java.sql.Ref getRef(int idx) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getRef"); return super.getRef(idx); }
        @Override public java.sql.Ref getRef(String lbl) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getRef"); return super.getRef(lbl); }

        @Override public <T> T getObject(int idx, Class<T> t) throws SQLException {
            if (shouldMaskColumn(idx)) {
                if (t == String.class) return t.cast(config.maskValue(super.getString(idx)));
                throwMaskedColumnException(String.valueOf(idx), "getObject");
            }
            return super.getObject(idx, t);
        }

        @Override public <T> T getObject(String lbl, Class<T> t) throws SQLException {
            if (shouldMaskColumn(lbl)) {
                if (t == String.class) return t.cast(config.maskValue(super.getString(lbl)));
                throwMaskedColumnException(lbl, "getObject");
            }
            return super.getObject(lbl, t);
        }

        @Override public Object getObject(int idx, java.util.Map<String, Class<?>> m) throws SQLException { if (shouldMaskColumn(idx)) throwMaskedColumnException(String.valueOf(idx), "getObject"); return super.getObject(idx, m); }
        @Override public Object getObject(String lbl, java.util.Map<String, Class<?>> m) throws SQLException { if (shouldMaskColumn(lbl)) throwMaskedColumnException(lbl, "getObject"); return super.getObject(lbl, m); }
    }
}
