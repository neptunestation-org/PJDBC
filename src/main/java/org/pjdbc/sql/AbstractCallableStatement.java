package org.pjdbc.sql;

import java.io.*;
import java.math.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public abstract class AbstractCallableStatement extends AbstractPreparedStatement implements CallableStatement {
    private CallableStatement d;

    public AbstractCallableStatement(CallableStatement stmt, Connection conn) throws SQLException {
        super(stmt, conn);
        this.d = stmt;
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return d.getBigDecimal(parameterIndex);
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return d.getBigDecimal(parameterIndex, scale);
    }

    @Override
    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return d.getObject(parameterName, type);
    }

    @Override
    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return d.getObject(parameterIndex, type);
    }

    @Override
    public Array getArray(String parameterName) throws SQLException {
        return d.getArray(parameterName);
    }

    @Override
    public Array getArray(int parameterIndex) throws SQLException {
        return d.getArray(parameterIndex);
    }

    @Override
    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return d.getBigDecimal(parameterName);
    }

    @Override
    public Blob getBlob(String parameterName) throws SQLException {
        return d.getBlob(parameterName);
    }

    @Override
    public Blob getBlob(int parameterIndex) throws SQLException {
        return d.getBlob(parameterIndex);
    }

    @Override
    public Clob getClob(String parameterName) throws SQLException {
        return d.getClob(parameterName);
    }

    @Override
    public Clob getClob(int parameterIndex) throws SQLException {
        return d.getClob(parameterIndex);
    }

    @Override
    public NClob getNClob(String parameterName) throws SQLException {
        return d.getNClob(parameterName);
    }

    @Override
    public NClob getNClob(int parameterIndex) throws SQLException {
        return d.getNClob(parameterIndex);
    }

    @Override
    public Object getObject(String parameterName) throws SQLException {
        return d.getObject(parameterName);
    }

    @Override
    public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return d.getObject(parameterName, map);
    }

    @Override
    public Object getObject(int parameterIndex) throws SQLException {
        return d.getObject(parameterIndex);
    }

    @Override
    public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return d.getObject(parameterIndex, map);
    }

    @Override
    public Reader getCharacterStream(String parameterName) throws SQLException {
        return d.getCharacterStream(parameterName);
    }

    @Override
    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return d.getCharacterStream(parameterIndex);
    }

    @Override
    public Reader getNCharacterStream(String parameterName) throws SQLException {
        return d.getNCharacterStream(parameterName);
    }

    @Override
    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return d.getNCharacterStream(parameterIndex);
    }

    @Override
    public Ref getRef(String parameterName) throws SQLException {
        return d.getRef(parameterName);
    }

    @Override
    public Ref getRef(int parameterIndex) throws SQLException {
        return d.getRef(parameterIndex);
    }

    @Override
    public RowId getRowId(String parameterName) throws SQLException {
        return d.getRowId(parameterName);
    }

    @Override
    public RowId getRowId(int parameterIndex) throws SQLException {
        return d.getRowId(parameterIndex);
    }

    @Override
    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return d.getSQLXML(parameterName);
    }

    @Override
    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return d.getSQLXML(parameterIndex);
    }

    @Override
    public String getNString(String parameterName) throws SQLException {
        return d.getNString(parameterName);
    }

    @Override
    public String getNString(int parameterIndex) throws SQLException {
        return d.getNString(parameterIndex);
    }

    @Override
    public String getString(String parameterName) throws SQLException {
        return d.getString(parameterName);
    }

    @Override
    public String getString(int parameterIndex) throws SQLException {
        return d.getString(parameterIndex);
    }

    @Override
    public Time getTime(String parameterName) throws SQLException {
        return d.getTime(parameterName);
    }

    @Override
    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return d.getTime(parameterName, cal);
    }

    @Override
    public Time getTime(int parameterIndex) throws SQLException {
        return d.getTime(parameterIndex);
    }

    @Override
    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return d.getTime(parameterIndex, cal);
    }

    @Override
    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return d.getTimestamp(parameterName);
    }

    @Override
    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return d.getTimestamp(parameterName, cal);
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return d.getTimestamp(parameterIndex);
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return d.getTimestamp(parameterIndex, cal);
    }

    @Override
    public URL getURL(String parameterName) throws SQLException {
        return d.getURL(parameterName);
    }

    @Override
    public URL getURL(int parameterIndex) throws SQLException {
        return d.getURL(parameterIndex);
    }

    @Override
    public boolean getBoolean(String parameterName) throws SQLException {
        return d.getBoolean(parameterName);
    }

    @Override
    public boolean getBoolean(int parameterIndex) throws SQLException {
        return d.getBoolean(parameterIndex);
    }

    @Override
    public boolean wasNull() throws SQLException {
        return d.wasNull();
    }

    @Override
    public byte getByte(String parameterName) throws SQLException {
        return d.getByte(parameterName);
    }

    @Override
    public byte getByte(int parameterIndex) throws SQLException {
        return d.getByte(parameterIndex);
    }

    @Override
    public byte[] getBytes(String parameterName) throws SQLException {
        return d.getBytes(parameterName);
    }

    @Override
    public byte[] getBytes(int parameterIndex) throws SQLException {
        return d.getBytes(parameterIndex);
    }

    @Override
    public double getDouble(String parameterName) throws SQLException {
        return d.getDouble(parameterName);
    }

    @Override
    public double getDouble(int parameterIndex) throws SQLException {
        return d.getDouble(parameterIndex);
    }

    @Override
    public float getFloat(String parameterName) throws SQLException {
        return d.getFloat(parameterName);
    }

    @Override
    public float getFloat(int parameterIndex) throws SQLException {
        return d.getFloat(parameterIndex);
    }

    @Override
    public int getInt(String parameterName) throws SQLException {
        return d.getInt(parameterName);
    }

    @Override
    public int getInt(int parameterIndex) throws SQLException {
        return d.getInt(parameterIndex);
    }

    @Override
    public java.sql.Date getDate(String parameterName) throws SQLException {
        return d.getDate(parameterName);
    }

    @Override
    public java.sql.Date getDate(String parameterName, Calendar cal) throws SQLException {
        return d.getDate(parameterName, cal);
    }

    @Override
    public java.sql.Date getDate(int parameterIndex) throws SQLException {
        return d.getDate(parameterIndex);
    }

    @Override
    public java.sql.Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return d.getDate(parameterIndex, cal);
    }

    @Override
    public long getLong(String parameterName) throws SQLException {
        return d.getLong(parameterName);
    }

    @Override
    public long getLong(int parameterIndex) throws SQLException {
        return d.getLong(parameterIndex);
    }

    @Override
    public short getShort(String parameterName) throws SQLException {
        return d.getShort(parameterName);
    }

    @Override
    public short getShort(int parameterIndex) throws SQLException {
        return d.getShort(parameterIndex);
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        d.registerOutParameter(parameterName, sqlType);
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        d.registerOutParameter(parameterName, sqlType, typeName);
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        d.registerOutParameter(parameterName, sqlType, scale);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        d.registerOutParameter(parameterIndex, sqlType);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        d.registerOutParameter(parameterIndex, sqlType, typeName);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        d.registerOutParameter(parameterIndex, sqlType, scale);
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
        d.setAsciiStream(parameterName, x);
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
        d.setAsciiStream(parameterName, x, length);
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
        d.setAsciiStream(parameterName, x, length);
    }

    @Override
    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        d.setBigDecimal(parameterName, x);
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
        d.setBinaryStream(parameterName, x);
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
        d.setBinaryStream(parameterName, x, length);
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
        d.setBinaryStream(parameterName, x, length);
    }

    @Override
    public void setBlob(String parameterName, Blob x) throws SQLException {
        d.setBlob(parameterName, x);
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        d.setBlob(parameterName, inputStream);
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        d.setBlob(parameterName, inputStream, length);
    }

    @Override
    public void setBoolean(String parameterName, boolean x) throws SQLException {
        d.setBoolean(parameterName, x);
    }

    @Override
    public void setByte(String parameterName, byte x) throws SQLException {
        d.setByte(parameterName, x);
    }

    @Override
    public void setBytes(String parameterName, byte[] x) throws SQLException {
        d.setBytes(parameterName, x);
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        d.setCharacterStream(parameterName, reader);
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
        d.setCharacterStream(parameterName, reader, length);
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        d.setCharacterStream(parameterName, reader, length);
    }

    @Override
    public void setClob(String parameterName, Clob x) throws SQLException {
        d.setClob(parameterName, x);
    }

    @Override
    public void setClob(String parameterName, Reader reader) throws SQLException {
        d.setClob(parameterName, reader);
    }

    @Override
    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        d.setClob(parameterName, reader, length);
    }

    @Override
    public void setDate(String parameterName, java.sql.Date x) throws SQLException {
        d.setDate(parameterName, x);
    }

    @Override
    public void setDate(String parameterName, java.sql.Date x, Calendar cal) throws SQLException {
        d.setDate(parameterName, x, cal);
    }

    @Override
    public void setDouble(String parameterName, double x) throws SQLException {
        d.setDouble(parameterName, x);
    }

    @Override
    public void setFloat(String parameterName, float x) throws SQLException {
        d.setFloat(parameterName, x);
    }

    @Override
    public void setInt(String parameterName, int x) throws SQLException {
        d.setInt(parameterName, x);
    }

    @Override
    public void setLong(String parameterName, long x) throws SQLException {
        d.setLong(parameterName, x);
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
        d.setNCharacterStream(parameterName, value);
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
        d.setNCharacterStream(parameterName, value, length);
    }

    @Override
    public void setNClob(String parameterName, NClob value) throws SQLException {
        d.setNClob(parameterName, value);
    }

    @Override
    public void setNClob(String parameterName, Reader reader) throws SQLException {
        d.setNClob(parameterName, reader);
    }

    @Override
    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        d.setNClob(parameterName, reader, length);
    }

    @Override
    public void setNString(String parameterName, String value) throws SQLException {
        d.setNString(parameterName, value);
    }

    @Override
    public void setNull(String parameterName, int sqlType) throws SQLException {
        d.setNull(parameterName, sqlType);
    }

    @Override
    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        d.setNull(parameterName, sqlType, typeName);
    }

    @Override
    public void setObject(String parameterName, Object x) throws SQLException {
        d.setObject(parameterName, x);
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        d.setObject(parameterName, x, targetSqlType);
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        d.setObject(parameterName, x, targetSqlType, scale);
    }

    @Override
    public void setRowId(String parameterName, RowId x) throws SQLException {
        d.setRowId(parameterName, x);
    }

    @Override
    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        d.setSQLXML(parameterName, xmlObject);
    }

    @Override
    public void setShort(String parameterName, short x) throws SQLException {
        d.setShort(parameterName, x);
    }

    @Override
    public void setString(String parameterName, String x) throws SQLException {
        d.setString(parameterName, x);
    }

    @Override
    public void setTime(String parameterName, Time x) throws SQLException {
        d.setTime(parameterName, x);
    }

    @Override
    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        d.setTime(parameterName, x, cal);
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        d.setTimestamp(parameterName, x);
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        d.setTimestamp(parameterName, x, cal);
    }

    @Override
    public void setURL(String parameterName, URL val) throws SQLException {
        d.setURL(parameterName, val);
    }
}
