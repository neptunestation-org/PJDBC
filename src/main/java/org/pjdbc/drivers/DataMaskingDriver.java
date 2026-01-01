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
 * URL format: jdbc:mask[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   columns     - Semicolon-separated column name patterns (regex) to mask
 *   strategy    - Masking strategy: FULL, PARTIAL, EMAIL, REDACT, HASH (default: PARTIAL)
 *   mask        - Character to use for masking (default: *)
 *   showLast    - Characters to show at end for PARTIAL strategy (default: 4)
 *   showFirst   - Characters to show at start for PARTIAL strategy (default: 0)
 *
 * Masking Strategies:
 *   FULL    - Replace entire value with mask characters (e.g., "********")
 *   PARTIAL - Show first/last N characters (e.g., "****1234")
 *   EMAIL   - Mask email preserving first char and domain (e.g., "j***@example.com")
 *   REDACT  - Replace with "[REDACTED]"
 *   HASH    - Replace with hash prefix (e.g., "a1b2c3d4...")
 *
 * Example URLs:
 *   jdbc:mask[columns=ssn;credit_card]:jdbc:postgresql://localhost/mydb
 *   jdbc:mask[columns=.*email.*,strategy=EMAIL]:jdbc:postgresql://localhost/mydb
 *   jdbc:mask[columns=password;secret,strategy=REDACT]:jdbc:postgresql://localhost/mydb
 *   jdbc:mask[columns=card_number,showLast=4,showFirst=0]:jdbc:mysql://localhost/db
 */
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
    protected ResultSet proxyResultSet(Statement stmt, ResultSet delegate) throws SQLException {
        Connection conn = stmt.getConnection();
        if (conn instanceof MaskingConnection) {
            MaskingConnection maskConn = (MaskingConnection) conn;
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

            switch (strategy) {
                case FULL:
                    return repeat(maskChar, value.length());

                case PARTIAL:
                    return maskPartial(value);

                case EMAIL:
                    return maskEmail(value);

                case REDACT:
                    return "[REDACTED]";

                case HASH:
                    return maskHash(value);

                default:
                    return maskPartial(value);
            }
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
            String hex = Integer.toHexString(Math.abs(hash));
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
        public Object getObject(int columnIndex) throws SQLException {
            Object value = super.getObject(columnIndex);
            if (shouldMaskColumn(columnIndex) && value instanceof String) {
                return config.maskValue((String) value);
            }
            return value;
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException {
            Object value = super.getObject(columnLabel);
            if (config.shouldMask(columnLabel) && value instanceof String) {
                return config.maskValue((String) value);
            }
            return value;
        }

        private boolean shouldMaskColumn(int columnIndex) {
            return columnIndex > 0 && columnIndex < maskedColumns.length && maskedColumns[columnIndex];
        }
    }
}
