package org.pjdbc.sql;

import java.io.*;
import java.math.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public abstract class AbstractPreparedStatement extends AbstractStatement implements PreparedStatement {
    private List<PreparedStatement> delegates = new ArrayList<PreparedStatement>();

    public AbstractPreparedStatement(PreparedStatement stmt, Connection conn) throws SQLException {
        super(stmt, conn);
        delegates.add(stmt);
    }

    public AbstractPreparedStatement(PreparedStatement[] stmts, Connection conn) throws SQLException {
        super(stmts[0], conn);
        delegates = Arrays.asList(stmts);
    }

    protected PreparedStatement getDelegate() {
        return delegates.isEmpty() ? null : delegates.get(0);
    }

    protected List<PreparedStatement> getPreparedStatements() {
        return delegates;
    }

    /**
     * Transform a parameter value before binding.
     * Subclasses can override this to apply transformations to input parameters.
     * @param parameterIndex 1-based parameter index
     * @param value the original parameter value
     * @param sqlType SQL type from java.sql.Types
     * @return the transformed value (default implementation returns value unchanged)
     */
    protected Object transformParameter(int parameterIndex, Object value, int sqlType) throws SQLException {
        return value;
    }

    // Helper to buffer InputStream for reuse across multiple delegates
    private byte[] bufferStream(InputStream x) throws SQLException {
        if (x == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = x.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SQLException("Failed to buffer stream", e);
        }
    }

    // Helper to buffer Reader for reuse across multiple delegates
    private String bufferReader(Reader r) throws SQLException {
        if (r == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to buffer reader", e);
        }
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return getDelegate().getParameterMetaData();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        ArrayList<ResultSet> rsets = new ArrayList<ResultSet>();
        for (PreparedStatement d : getPreparedStatements()) {
            rsets.add(d.executeQuery());
        }
        for (ResultSet r : rsets) {
            return wrap(r);
        }
        throw new SQLException();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return getDelegate().getMetaData();
    }

    @Override
    public boolean execute() throws SQLException {
        Boolean first = null;
        for (PreparedStatement d : getPreparedStatements()) {
            boolean r = d.execute();
            if (first == null) first = r;
        }
        if (first != null) return first;
        throw new SQLException();
    }

    @Override
    public int executeUpdate() throws SQLException {
        List<Integer> counts = new ArrayList<>();
        for (PreparedStatement d : getPreparedStatements()) {
            counts.add(d.executeUpdate());
        }
        if (!counts.isEmpty()) return mergeUpdateCounts(counts);
        throw new SQLException();
    }

    @Override
    public void addBatch() throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.addBatch();
        }
    }

    @Override
    public void clearParameters() throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.clearParameters();
        }
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setArray(parameterIndex, x);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setAsciiStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data));
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setAsciiStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setAsciiStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBigDecimal(parameterIndex, x);
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBinaryStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data));
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBinaryStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBinaryStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBlob(parameterIndex, x);
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        byte[] data = bufferStream(inputStream);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBlob(parameterIndex, data == null ? null : new ByteArrayInputStream(data));
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        byte[] data = bufferStream(inputStream);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBlob(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBoolean(parameterIndex, (Boolean) transformParameter(parameterIndex, x, Types.BOOLEAN));
        }
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setByte(parameterIndex, x);
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setBytes(parameterIndex, x);
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setCharacterStream(parameterIndex, data == null ? null : new StringReader(data));
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setCharacterStream(parameterIndex, data == null ? null : new StringReader(data), length);
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setCharacterStream(parameterIndex, data == null ? null : new StringReader(data), length);
        }
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setClob(parameterIndex, x);
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setClob(parameterIndex, data == null ? null : new StringReader(data));
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setClob(parameterIndex, data == null ? null : new StringReader(data), length);
        }
    }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setDate(parameterIndex, x);
        }
    }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setDate(parameterIndex, x, cal);
        }
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setDouble(parameterIndex, (Double) transformParameter(parameterIndex, x, Types.DOUBLE));
        }
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setFloat(parameterIndex, (Float) transformParameter(parameterIndex, x, Types.FLOAT));
        }
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setInt(parameterIndex, (Integer) transformParameter(parameterIndex, x, Types.INTEGER));
        }
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setLong(parameterIndex, (Long) transformParameter(parameterIndex, x, Types.BIGINT));
        }
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        String data = bufferReader(value);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNCharacterStream(parameterIndex, data == null ? null : new StringReader(data));
        }
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        String data = bufferReader(value);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNCharacterStream(parameterIndex, data == null ? null : new StringReader(data), length);
        }
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNClob(parameterIndex, value);
        }
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNClob(parameterIndex, data == null ? null : new StringReader(data));
        }
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        String data = bufferReader(reader);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNClob(parameterIndex, data == null ? null : new StringReader(data), length);
        }
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNString(parameterIndex, value);
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNull(parameterIndex, sqlType);
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setNull(parameterIndex, sqlType, typeName);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setObject(parameterIndex, transformParameter(parameterIndex, x, Types.JAVA_OBJECT));
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setObject(parameterIndex, transformParameter(parameterIndex, x, targetSqlType), targetSqlType);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setObject(parameterIndex, transformParameter(parameterIndex, x, targetSqlType), targetSqlType, scaleOrLength);
        }
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setRef(parameterIndex, x);
        }
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setRowId(parameterIndex, x);
        }
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setSQLXML(parameterIndex, xmlObject);
        }
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setShort(parameterIndex, x);
        }
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setString(parameterIndex, (String) transformParameter(parameterIndex, x, Types.VARCHAR));
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setTime(parameterIndex, x);
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setTime(parameterIndex, x, cal);
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setTimestamp(parameterIndex, x);
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setTimestamp(parameterIndex, x, cal);
        }
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        for (PreparedStatement d : getPreparedStatements()) {
            d.setURL(parameterIndex, x);
        }
    }

    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        byte[] data = bufferStream(x);
        for (PreparedStatement d : getPreparedStatements()) {
            d.setUnicodeStream(parameterIndex, data == null ? null : new ByteArrayInputStream(data), length);
        }
    }
}
