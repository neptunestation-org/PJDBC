package org.pjdbc.drivers;

import java.io.*;
import java.math.*;
import java.net.*;
import java.sql.*;
import java.util.*;

/**
 * A simple in-memory ResultSet for testing with MockDriver.
 *
 * <p>Usage:
 * <pre>
 * MockResultSet rs = MockResultSet.create()
 *     .columns("id", "name", "email")
 *     .row(1, "Alice", "alice@example.com")
 *     .row(2, "Bob", "bob@example.com")
 *     .build();
 * </pre>
 */
public class MockResultSet implements ResultSet {
    private final String[] columnNames;
    private final int[] columnTypes;
    private final List<Object[]> rows;
    private int currentRow = -1;
    private boolean closed = false;
    private boolean wasNull = false;

    private MockResultSet(String[] columnNames, int[] columnTypes, List<Object[]> rows) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.rows = rows;
    }

    /**
     * Create a new MockResultSet builder.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Create an empty ResultSet with no columns or rows.
     */
    public static MockResultSet empty() {
        return new MockResultSet(new String[0], new int[0], Collections.emptyList());
    }

    /**
     * Builder for constructing MockResultSet instances.
     */
    public static class Builder {
        private String[] columnNames;
        private int[] columnTypes;
        private final List<Object[]> rows = new ArrayList<>();

        /**
         * Set column names. Column types will be inferred from data.
         */
        public Builder columns(String... names) {
            this.columnNames = names;
            this.columnTypes = new int[names.length];
            Arrays.fill(this.columnTypes, Types.VARCHAR);
            return this;
        }

        /**
         * Set column names with explicit types.
         */
        public Builder columns(String[] names, int[] types) {
            if (names.length != types.length) {
                throw new IllegalArgumentException("Column names and types must have same length");
            }
            this.columnNames = names;
            this.columnTypes = types;
            return this;
        }

        /**
         * Add a row of data. Values must match column count.
         */
        public Builder row(Object... values) {
            if (columnNames == null) {
                throw new IllegalStateException("Must call columns() before row()");
            }
            if (values.length != columnNames.length) {
                throw new IllegalArgumentException(
                    "Row has " + values.length + " values but " + columnNames.length + " columns defined");
            }
            // Infer types from first non-null row
            if (rows.isEmpty()) {
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != null) {
                        columnTypes[i] = inferType(values[i]);
                    }
                }
            }
            rows.add(values.clone());
            return this;
        }

        private int inferType(Object value) {
            if (value instanceof Integer) return Types.INTEGER;
            if (value instanceof Long) return Types.BIGINT;
            if (value instanceof Double) return Types.DOUBLE;
            if (value instanceof Float) return Types.FLOAT;
            if (value instanceof Boolean) return Types.BOOLEAN;
            if (value instanceof BigDecimal) return Types.DECIMAL;
            if (value instanceof java.sql.Date) return Types.DATE;
            if (value instanceof java.sql.Time) return Types.TIME;
            if (value instanceof java.sql.Timestamp) return Types.TIMESTAMP;
            if (value instanceof byte[]) return Types.BINARY;
            return Types.VARCHAR;
        }

        /**
         * Build the MockResultSet.
         */
        public MockResultSet build() {
            if (columnNames == null) {
                return MockResultSet.empty();
            }
            return new MockResultSet(columnNames, columnTypes, new ArrayList<>(rows));
        }
    }

    // Navigation methods

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        if (currentRow < rows.size() - 1) {
            currentRow++;
            return true;
        }
        // Move to "after last" position
        if (currentRow < rows.size()) {
            currentRow = rows.size();
        }
        return false;
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("ResultSet is closed");
        }
    }

    private void checkRow() throws SQLException {
        checkClosed();
        if (currentRow < 0) {
            throw new SQLException("Before first row");
        }
        if (currentRow >= rows.size()) {
            throw new SQLException("After last row");
        }
    }

    private Object getValue(int columnIndex) throws SQLException {
        checkRow();
        if (columnIndex < 1 || columnIndex > columnNames.length) {
            throw new SQLException("Invalid column index: " + columnIndex);
        }
        Object value = rows.get(currentRow)[columnIndex - 1];
        wasNull = (value == null);
        return value;
    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    // Getters by column index

    @Override
    public String getString(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        return value == null ? null : value.toString();
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).byteValue();
        return Byte.parseByte(value.toString());
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).shortValue();
        return Short.parseShort(value.toString());
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0f;
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return 0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        return getBigDecimal(columnIndex);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return null;
        if (value instanceof byte[]) return (byte[]) value;
        return value.toString().getBytes();
    }

    @Override
    public java.sql.Date getDate(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return null;
        if (value instanceof java.sql.Date) return (java.sql.Date) value;
        if (value instanceof java.util.Date) return new java.sql.Date(((java.util.Date) value).getTime());
        return java.sql.Date.valueOf(value.toString());
    }

    @Override
    public java.sql.Time getTime(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return null;
        if (value instanceof java.sql.Time) return (java.sql.Time) value;
        return java.sql.Time.valueOf(value.toString());
    }

    @Override
    public java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp) return (java.sql.Timestamp) value;
        if (value instanceof java.util.Date) return new java.sql.Timestamp(((java.util.Date) value).getTime());
        return java.sql.Timestamp.valueOf(value.toString());
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return getValue(columnIndex);
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    // Getters by column name (delegate to index methods)

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public java.sql.Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public java.sql.Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public java.sql.Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equalsIgnoreCase(columnLabel)) {
                return i + 1;
            }
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    // Metadata

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new MockResultSetMetaData();
    }

    private class MockResultSetMetaData implements ResultSetMetaData {
        @Override
        public int getColumnCount() throws SQLException {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) throws SQLException {
            return columnNames[column - 1];
        }

        @Override
        public String getColumnLabel(int column) throws SQLException {
            return getColumnName(column);
        }

        @Override
        public int getColumnType(int column) throws SQLException {
            return columnTypes[column - 1];
        }

        @Override
        public String getColumnTypeName(int column) throws SQLException {
            int type = columnTypes[column - 1];
            switch (type) {
                case Types.INTEGER: return "INTEGER";
                case Types.BIGINT: return "BIGINT";
                case Types.DOUBLE: return "DOUBLE";
                case Types.FLOAT: return "FLOAT";
                case Types.BOOLEAN: return "BOOLEAN";
                case Types.DECIMAL: return "DECIMAL";
                case Types.DATE: return "DATE";
                case Types.TIME: return "TIME";
                case Types.TIMESTAMP: return "TIMESTAMP";
                case Types.BINARY: return "BINARY";
                default: return "VARCHAR";
            }
        }

        @Override
        public String getColumnClassName(int column) throws SQLException {
            int type = columnTypes[column - 1];
            switch (type) {
                case Types.INTEGER: return Integer.class.getName();
                case Types.BIGINT: return Long.class.getName();
                case Types.DOUBLE: return Double.class.getName();
                case Types.FLOAT: return Float.class.getName();
                case Types.BOOLEAN: return Boolean.class.getName();
                case Types.DECIMAL: return BigDecimal.class.getName();
                case Types.DATE: return java.sql.Date.class.getName();
                case Types.TIME: return java.sql.Time.class.getName();
                case Types.TIMESTAMP: return java.sql.Timestamp.class.getName();
                case Types.BINARY: return byte[].class.getName();
                default: return String.class.getName();
            }
        }

        @Override public boolean isAutoIncrement(int column) { return false; }
        @Override public boolean isCaseSensitive(int column) { return true; }
        @Override public boolean isSearchable(int column) { return true; }
        @Override public boolean isCurrency(int column) { return false; }
        @Override public int isNullable(int column) { return ResultSetMetaData.columnNullable; }
        @Override public boolean isSigned(int column) { return true; }
        @Override public int getColumnDisplaySize(int column) { return 50; }
        @Override public String getSchemaName(int column) { return ""; }
        @Override public int getPrecision(int column) { return 0; }
        @Override public int getScale(int column) { return 0; }
        @Override public String getTableName(int column) { return ""; }
        @Override public String getCatalogName(int column) { return ""; }
        @Override public boolean isReadOnly(int column) { return true; }
        @Override public boolean isWritable(int column) { return false; }
        @Override public boolean isDefinitelyWritable(int column) { return false; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    // Positioning methods

    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkClosed();
        return currentRow < 0 && !rows.isEmpty();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        checkClosed();
        return currentRow >= rows.size() && !rows.isEmpty();
    }

    @Override
    public boolean isFirst() throws SQLException {
        checkClosed();
        return currentRow == 0 && !rows.isEmpty();
    }

    @Override
    public boolean isLast() throws SQLException {
        checkClosed();
        return currentRow == rows.size() - 1 && !rows.isEmpty();
    }

    @Override
    public void beforeFirst() throws SQLException {
        checkClosed();
        currentRow = -1;
    }

    @Override
    public void afterLast() throws SQLException {
        checkClosed();
        currentRow = rows.size();
    }

    @Override
    public boolean first() throws SQLException {
        checkClosed();
        if (rows.isEmpty()) return false;
        currentRow = 0;
        return true;
    }

    @Override
    public boolean last() throws SQLException {
        checkClosed();
        if (rows.isEmpty()) return false;
        currentRow = rows.size() - 1;
        return true;
    }

    @Override
    public int getRow() throws SQLException {
        checkClosed();
        return currentRow < 0 || currentRow >= rows.size() ? 0 : currentRow + 1;
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        checkClosed();
        if (rows.isEmpty()) return false;
        if (row > 0) {
            currentRow = Math.min(row - 1, rows.size());
        } else if (row < 0) {
            currentRow = Math.max(rows.size() + row, -1);
        } else {
            currentRow = -1;
            return false;
        }
        return currentRow >= 0 && currentRow < rows.size();
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        checkClosed();
        return absolute(currentRow + 1 + rows);
    }

    @Override
    public boolean previous() throws SQLException {
        checkClosed();
        if (currentRow > 0) {
            currentRow--;
            return true;
        }
        currentRow = -1;
        return false;
    }

    @Override
    public int getType() throws SQLException {
        return ResultSet.TYPE_SCROLL_INSENSITIVE;
    }

    @Override
    public int getConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // ignored
    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        // ignored
    }

    @Override
    public Statement getStatement() throws SQLException {
        return null;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        // no-op
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCursorName");
    }

    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    // Update methods - not supported

    @Override public boolean rowUpdated() { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted() { return false; }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateDate(int columnIndex, java.sql.Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateTime(int columnIndex, java.sql.Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateTimestamp(int columnIndex, java.sql.Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateDate(String columnLabel, java.sql.Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateTime(String columnLabel, java.sql.Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateTimestamp(String columnLabel, java.sql.Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void insertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void deleteRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    // Stream methods

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        String s = getString(columnIndex);
        return s == null ? null : new ByteArrayInputStream(s.getBytes());
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        return getAsciiStream(columnIndex);
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        byte[] bytes = getBytes(columnIndex);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        return getAsciiStream(findColumn(columnLabel));
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        return getUnicodeStream(findColumn(columnLabel));
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        return getBinaryStream(findColumn(columnLabel));
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        String s = getString(columnIndex);
        return s == null ? null : new StringReader(s);
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        return getCharacterStream(findColumn(columnLabel));
    }

    // Ref, Blob, Clob, Array - return null

    @Override public Ref getRef(int columnIndex) throws SQLException { return null; }
    @Override public Blob getBlob(int columnIndex) throws SQLException { return null; }
    @Override public Clob getClob(int columnIndex) throws SQLException { return null; }
    @Override public Array getArray(int columnIndex) throws SQLException { return null; }
    @Override public Ref getRef(String columnLabel) throws SQLException { return null; }
    @Override public Blob getBlob(String columnLabel) throws SQLException { return null; }
    @Override public Clob getClob(String columnLabel) throws SQLException { return null; }
    @Override public Array getArray(String columnLabel) throws SQLException { return null; }

    // Date/Time with Calendar

    @Override
    public java.sql.Date getDate(int columnIndex, Calendar cal) throws SQLException {
        return getDate(columnIndex);
    }

    @Override
    public java.sql.Time getTime(int columnIndex, Calendar cal) throws SQLException {
        return getTime(columnIndex);
    }

    @Override
    public java.sql.Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        return getTimestamp(columnIndex);
    }

    @Override
    public java.sql.Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return getDate(columnLabel);
    }

    @Override
    public java.sql.Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return getTime(columnLabel);
    }

    @Override
    public java.sql.Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return getTimestamp(columnLabel);
    }

    // URL

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        String s = getString(columnIndex);
        if (s == null) return null;
        try {
            return new URL(s);
        } catch (Exception e) {
            throw new SQLException("Invalid URL: " + s, e);
        }
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        return getURL(findColumn(columnLabel));
    }

    // Object with type/map

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex);
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnLabel);
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        Object value = getObject(columnIndex);
        if (value == null) return null;
        return type.cast(value);
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    // Update streams with long length - not supported

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    // RowId - not supported

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    // NClob, NString, NCharacterStream

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        return null;
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        return getString(columnLabel);
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        return getCharacterStream(columnIndex);
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        return getCharacterStream(columnLabel);
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }

    // Wrapper

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}
