package org.pjdbc.sql;

import java.io.*;
import java.math.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public abstract class AbstractResultSet extends AbstractWrapper implements ResultSet {
    private ResultSet d;
    private Statement stmt;

    public AbstractResultSet (ResultSet rset) throws SQLException {
	super(rset);
	this.d = rset;}

    public AbstractResultSet (Statement stmt, ResultSet rset) throws SQLException {
	this(rset);
	this.stmt = stmt;}

    protected ResultSet getDelegate() {
	return d;}

    protected Statement getParentStatement() {
	return stmt;}

    /**
     * Transform a value retrieved from the ResultSet.
     * Subclasses can override this to apply transformations to output values.
     * @param columnIndex 1-based column index
     * @param value the original value from the delegate ResultSet
     * @param sqlType SQL type from java.sql.Types (or 0 if unknown)
     * @return the transformed value (default implementation returns value unchanged)
     */
    protected Object transformValue(int columnIndex, Object value, int sqlType) throws SQLException {
	return value;}

    @Deprecated public BigDecimal getBigDecimal (String columnLabel, int scale) throws SQLException {return d.getBigDecimal(columnLabel, scale);}
    @Deprecated public BigDecimal getBigDecimal (int columnIndex, int scale) throws SQLException {return d.getBigDecimal(columnIndex, scale);}
    @Deprecated public InputStream getUnicodeStream (int columnIndex) throws SQLException {return d.getUnicodeStream(columnIndex);}
    @Deprecated public InputStream getUnicodeStream(String columnLabel) throws SQLException {return d.getUnicodeStream(columnLabel);}
    public <T> T getObject (String columnLabel, Class<T> type) throws SQLException {return d.getObject(columnLabel, type);}
    public <T> T getObject (int columnIndex, Class<T> type) throws SQLException {return d.getObject(columnIndex, type);}
    public Array getArray (String columnLabel) throws SQLException {return d.getArray(columnLabel);}
    public Array getArray (int columnIndex) throws SQLException {return d.getArray(columnIndex);}
    public BigDecimal getBigDecimal (String columnLabel) throws SQLException {return d.getBigDecimal(columnLabel);}
    public BigDecimal getBigDecimal (int columnIndex) throws SQLException {return d.getBigDecimal(columnIndex);}
    public Blob getBlob (String columnLabel) throws SQLException {return d.getBlob(columnLabel);}
    public Blob getBlob (int columnIndex) throws SQLException {return d.getBlob(columnIndex);}
    public Clob getClob (String columnLabel) throws SQLException {return d.getClob(columnLabel);}
    public Clob getClob (int columnIndex) throws SQLException {return d.getClob(columnIndex);}
    public InputStream getAsciiStream (String columnLabel) throws SQLException {return d.getAsciiStream(columnLabel);}
    public InputStream getAsciiStream (int columnIndex) throws SQLException {return d.getAsciiStream(columnIndex);}
    public InputStream getBinaryStream (String columnLabel) throws SQLException {return d.getBinaryStream(columnLabel);}
    public InputStream getBinaryStream (int columnIndex) throws SQLException {return d.getBinaryStream(columnIndex);}
    public NClob getNClob (String columnLabel) throws SQLException {return d.getNClob(columnLabel);}
    public NClob getNClob (int columnIndex) throws SQLException {return d.getNClob(columnIndex);}
    public Object getObject (String columnLabel) throws SQLException {return transformValue(d.findColumn(columnLabel), d.getObject(columnLabel), Types.JAVA_OBJECT);}
    public Object getObject (String columnLabel, Map<String,Class<?>> map) throws SQLException {return transformValue(d.findColumn(columnLabel), d.getObject(columnLabel, map), Types.JAVA_OBJECT);}
    public Object getObject (int columnIndex) throws SQLException {return transformValue(columnIndex, d.getObject(columnIndex), Types.JAVA_OBJECT);}
    public Object getObject (int columnIndex, Map<String,Class<?>> map) throws SQLException {return transformValue(columnIndex, d.getObject(columnIndex, map), Types.JAVA_OBJECT);}
    public Reader getCharacterStream (String columnLabel) throws SQLException {return d.getCharacterStream(columnLabel);}
    public Reader getCharacterStream (int columnIndex) throws SQLException {return d.getCharacterStream(columnIndex);}
    public Reader getNCharacterStream (String columnLabel) throws SQLException {return d.getNCharacterStream(columnLabel);}
    public Reader getNCharacterStream (int columnIndex) throws SQLException {return d.getNCharacterStream(columnIndex);}
    public Ref getRef (String columnLabel) throws SQLException {return d.getRef(columnLabel);}
    public Ref getRef (int columnIndex) throws SQLException {return d.getRef(columnIndex);}
    public ResultSetMetaData getMetaData () throws SQLException {return d.getMetaData();}
    public RowId getRowId (String columnLabel) throws SQLException {return d.getRowId(columnLabel);}
    public RowId getRowId (int columnIndex) throws SQLException {return d.getRowId(columnIndex);}
    public SQLWarning getWarnings () throws SQLException {return d.getWarnings();}
    public SQLXML getSQLXML (String columnLabel) throws SQLException {return d.getSQLXML(columnLabel);}
    public SQLXML getSQLXML (int columnIndex) throws SQLException {return d.getSQLXML(columnIndex);}
    public Statement getStatement () throws SQLException {return d.getStatement();}
    public String getCursorName () throws SQLException {return d.getCursorName();}
    public String getNString (String columnLabel) throws SQLException {return d.getNString(columnLabel);}
    public String getNString (int columnIndex) throws SQLException {return d.getNString(columnIndex);}
    public String getString (String columnLabel) throws SQLException {return (String)transformValue(d.findColumn(columnLabel), d.getString(columnLabel), Types.VARCHAR);}
    public String getString (int columnIndex) throws SQLException {return (String)transformValue(columnIndex, d.getString(columnIndex), Types.VARCHAR);}
    public Time getTime (String columnLabel) throws SQLException {return d.getTime(columnLabel);}
    public Time getTime (String columnLabel, Calendar cal) throws SQLException {return d.getTime(columnLabel, cal);}
    public Time getTime (int columnIndex) throws SQLException {return d.getTime(columnIndex);}
    public Time getTime (int columnIndex, Calendar cal) throws SQLException {return d.getTime(columnIndex, cal);}
    public Timestamp getTimestamp (String columnLabel) throws SQLException {return d.getTimestamp(columnLabel);}
    public Timestamp getTimestamp (String columnLabel, Calendar cal) throws SQLException {return d.getTimestamp(columnLabel, cal);}
    public Timestamp getTimestamp (int columnIndex) throws SQLException {return d.getTimestamp(columnIndex);}
    public Timestamp getTimestamp (int columnIndex, Calendar cal) throws SQLException {return d.getTimestamp(columnIndex, cal);}
    public URL getURL (String columnLabel) throws SQLException {return d.getURL(columnLabel);}
    public URL getURL (int columnIndex) throws SQLException {return d.getURL(columnIndex);}
    public boolean absolute (int row) throws SQLException {return d.absolute(row);}
    public boolean first () throws SQLException {return d.first();}
    public boolean getBoolean (String columnLabel) throws SQLException {return (Boolean)transformValue(d.findColumn(columnLabel), d.getBoolean(columnLabel), Types.BOOLEAN);}
    public boolean getBoolean (int columnIndex) throws SQLException {return (Boolean)transformValue(columnIndex, d.getBoolean(columnIndex), Types.BOOLEAN);}
    public boolean isAfterLast () throws SQLException {return d.isAfterLast();}
    public boolean isBeforeFirst () throws SQLException {return d.isBeforeFirst();}
    public boolean isClosed () throws SQLException {return d.isClosed();}
    public boolean isFirst () throws SQLException {return d.isFirst();}
    public boolean isLast () throws SQLException {return d.isLast();}
    public boolean last () throws SQLException {return d.last();}
    public boolean next () throws SQLException {return d.next();}
    public boolean previous () throws SQLException {return d.previous();}
    public boolean relative (int rows) throws SQLException {return d.relative(rows);}
    public boolean rowDeleted () throws SQLException {return d.rowDeleted();}
    public boolean rowInserted () throws SQLException {return d.rowInserted();}
    public boolean rowUpdated () throws SQLException {return d.rowUpdated();}
    public boolean wasNull () throws SQLException {return d.wasNull();}
    public byte getByte (String columnLabel) throws SQLException {return d.getByte(columnLabel);}
    public byte getByte (int columnIndex) throws SQLException {return d.getByte(columnIndex);}
    public byte[] getBytes (String columnLabel) throws SQLException {return d.getBytes(columnLabel);}
    public byte[] getBytes (int columnIndex) throws SQLException {return d.getBytes(columnIndex);}
    public double getDouble (String columnLabel) throws SQLException {return (Double)transformValue(d.findColumn(columnLabel), d.getDouble(columnLabel), Types.DOUBLE);}
    public double getDouble (int columnIndex) throws SQLException {return (Double)transformValue(columnIndex, d.getDouble(columnIndex), Types.DOUBLE);}
    public float getFloat (String columnLabel) throws SQLException {return (Float)transformValue(d.findColumn(columnLabel), d.getFloat(columnLabel), Types.FLOAT);}
    public float getFloat (int columnIndex) throws SQLException {return (Float)transformValue(columnIndex, d.getFloat(columnIndex), Types.FLOAT);}
    public int findColumn (String columnLabel) throws SQLException {return d.findColumn(columnLabel);}
    public int getConcurrency () throws SQLException {return d.getConcurrency();}
    public int getFetchDirection () throws SQLException {return d.getFetchDirection();}
    public int getFetchSize () throws SQLException {return d.getFetchSize();}
    public int getHoldability () throws SQLException {return d.getHoldability();}
    public int getInt (String columnLabel) throws SQLException {return (Integer)transformValue(d.findColumn(columnLabel), d.getInt(columnLabel), Types.INTEGER);}
    public int getInt (int columnIndex) throws SQLException {return (Integer)transformValue(columnIndex, d.getInt(columnIndex), Types.INTEGER);}
    public int getRow () throws SQLException {return d.getRow();}
    public int getType () throws SQLException {return d.getType();}
    public java.sql.Date getDate (String columnLabel) throws SQLException {return d.getDate(columnLabel);}
    public java.sql.Date getDate (String columnLabel, Calendar cal) throws SQLException {return d.getDate(columnLabel, cal);}
    public java.sql.Date getDate (int columnIndex) throws SQLException {return d.getDate(columnIndex);}
    public java.sql.Date getDate (int columnIndex, Calendar cal) throws SQLException {return d.getDate(columnIndex, cal);}
    public long getLong (String columnLabel) throws SQLException {return (Long)transformValue(d.findColumn(columnLabel), d.getLong(columnLabel), Types.BIGINT);}
    public long getLong (int columnIndex) throws SQLException {return (Long)transformValue(columnIndex, d.getLong(columnIndex), Types.BIGINT);}
    public short getShort (String columnLabel) throws SQLException {return d.getShort(columnLabel);}
    public short getShort (int columnIndex) throws SQLException {return d.getShort(columnIndex);}
    public void afterLast () throws SQLException {d.afterLast();}
    public void beforeFirst () throws SQLException {d.beforeFirst();}
    public void cancelRowUpdates () throws SQLException {d.cancelRowUpdates();}
    public void clearWarnings () throws SQLException {d.clearWarnings();}
    public void close () throws SQLException {d.close();}
    public void deleteRow () throws SQLException {d.deleteRow();}
    public void insertRow () throws SQLException {d.insertRow();}
    public void moveToCurrentRow () throws SQLException {d.moveToCurrentRow();}
    public void moveToInsertRow () throws SQLException {d.moveToInsertRow();}
    public void refreshRow () throws SQLException {d.refreshRow();}
    public void setFetchDirection (int direction) throws SQLException {d.setFetchDirection(direction);}
    public void setFetchSize (int rows) throws SQLException {d.setFetchSize(rows);}
    public void updateArray (String columnLabel, Array x) throws SQLException {d.updateArray(columnLabel, x);}
    public void updateArray (int columnIndex, Array x) throws SQLException {d.updateArray(columnIndex, x);}
    public void updateAsciiStream (String columnLabel, InputStream x) throws SQLException {d.updateAsciiStream(columnLabel, x);}
    public void updateAsciiStream (String columnLabel, InputStream x, int length) throws SQLException {d.updateAsciiStream(columnLabel, x, length);}
    public void updateAsciiStream (String columnLabel, InputStream x, long length) throws SQLException {d.updateAsciiStream(columnLabel, x, length);}
    public void updateAsciiStream (int columnIndex, InputStream x) throws SQLException {d.updateAsciiStream(columnIndex, x);}
    public void updateAsciiStream (int columnIndex, InputStream x, int length) throws SQLException {d.updateAsciiStream(columnIndex, x, length);}
    public void updateAsciiStream (int columnIndex, InputStream x, long length) throws SQLException {d.updateAsciiStream(columnIndex, x, length);}
    public void updateBigDecimal (String columnLabel, BigDecimal x) throws SQLException {d.updateBigDecimal(columnLabel, x);}
    public void updateBigDecimal (int columnIndex, BigDecimal x) throws SQLException {d.updateBigDecimal(columnIndex, x);}
    public void updateBinaryStream (String columnLabel, InputStream x) throws SQLException {d.updateBinaryStream(columnLabel, x);}
    public void updateBinaryStream (String columnLabel, InputStream x, int length) throws SQLException {d.updateBinaryStream(columnLabel, x, length);}
    public void updateBinaryStream (String columnLabel, InputStream x, long length) throws SQLException {d.updateBinaryStream(columnLabel, x, length);}
    public void updateBinaryStream (int columnIndex, InputStream x) throws SQLException {d.updateBinaryStream(columnIndex, x);}
    public void updateBinaryStream (int columnIndex, InputStream x, int length) throws SQLException {d.updateBinaryStream(columnIndex, x, length);}
    public void updateBinaryStream (int columnIndex, InputStream x, long length) throws SQLException {d.updateBinaryStream(columnIndex, x, length);}
    public void updateBlob (String columnLabel, Blob x) throws SQLException {d.updateBlob(columnLabel, x);}
    public void updateBlob (String columnLabel, InputStream inputStream) throws SQLException {d.updateBlob(columnLabel, inputStream);}
    public void updateBlob (String columnLabel, InputStream inputStream, long length) throws SQLException {d.updateBlob(columnLabel, inputStream, length);}
    public void updateBlob (int columnIndex, Blob x) throws SQLException {d.updateBlob(columnIndex, x);}
    public void updateBlob (int columnIndex, InputStream inputStream) throws SQLException {d.updateBlob(columnIndex, inputStream);}
    public void updateBlob (int columnIndex, InputStream inputStream, long length) throws SQLException {d.updateBlob(columnIndex, inputStream, length);}
    public void updateBoolean (String columnLabel, boolean x) throws SQLException {d.updateBoolean(columnLabel, x);}
    public void updateBoolean (int columnIndex, boolean x) throws SQLException {d.updateBoolean(columnIndex, x);}
    public void updateByte (String columnLabel, byte x) throws SQLException {d.updateByte(columnLabel, x);}
    public void updateByte (int columnIndex, byte x) throws SQLException {d.updateByte(columnIndex, x);}
    public void updateBytes (String columnLabel, byte[] x) throws SQLException {d.updateBytes(columnLabel, x);}
    public void updateBytes (int columnIndex, byte[] x) throws SQLException {d.updateBytes(columnIndex, x);}
    public void updateCharacterStream (String columnLabel, Reader reader) throws SQLException {d.updateCharacterStream(columnLabel, reader);}
    public void updateCharacterStream (String columnLabel, Reader reader, int length) throws SQLException {d.updateCharacterStream(columnLabel, reader, length);}
    public void updateCharacterStream (String columnLabel, Reader reader, long length) throws SQLException {d.updateCharacterStream(columnLabel, reader, length);}
    public void updateCharacterStream (int columnIndex, Reader x) throws SQLException {d.updateCharacterStream(columnIndex, x);}
    public void updateCharacterStream (int columnIndex, Reader x, int length) throws SQLException {d.updateCharacterStream(columnIndex, x, length);}
    public void updateCharacterStream (int columnIndex, Reader x, long length) throws SQLException {d.updateCharacterStream(columnIndex, x, length);}
    public void updateClob (String columnLabel, Clob x) throws SQLException {d.updateClob(columnLabel, x);}
    public void updateClob (String columnLabel, Reader reader) throws SQLException {d.updateClob(columnLabel, reader);}
    public void updateClob (String columnLabel, Reader reader, long length) throws SQLException {d.updateClob(columnLabel, reader, length);}
    public void updateClob (int columnIndex, Clob x) throws SQLException {d.updateClob(columnIndex, x);}
    public void updateClob (int columnIndex, Reader reader) throws SQLException {d.updateClob(columnIndex, reader);}
    public void updateClob (int columnIndex, Reader reader, long length) throws SQLException {d.updateClob(columnIndex, reader, length);}
    public void updateDate (String columnLabel, java.sql.Date x) throws SQLException {d.updateDate(columnLabel, x);}
    public void updateDate (int columnIndex, java.sql.Date x) throws SQLException {d.updateDate(columnIndex, x);}
    public void updateDouble (String columnLabel, double x) throws SQLException {d.updateDouble(columnLabel, x);}
    public void updateDouble (int columnIndex, double x) throws SQLException {d.updateDouble(columnIndex, x);}
    public void updateFloat (String columnLabel, float x) throws SQLException {d.updateFloat(columnLabel, x);}
    public void updateFloat (int columnIndex, float x) throws SQLException {d.updateFloat(columnIndex, x);}
    public void updateInt (String columnLabel, int x) throws SQLException {d.updateInt(columnLabel, x);}
    public void updateInt (int columnIndex, int x) throws SQLException {d.updateInt(columnIndex, x);}
    public void updateLong (String columnLabel, long x) throws SQLException {d.updateLong(columnLabel, x);}
    public void updateLong (int columnIndex, long x) throws SQLException {d.updateLong(columnIndex, x);}
    public void updateNCharacterStream (String columnLabel, Reader reader) throws SQLException {d.updateNCharacterStream(columnLabel, reader);}
    public void updateNCharacterStream (String columnLabel, Reader reader, long length) throws SQLException {d.updateNCharacterStream(columnLabel, reader, length);}
    public void updateNCharacterStream (int columnIndex, Reader x) throws SQLException {d.updateNCharacterStream(columnIndex, x);}
    public void updateNCharacterStream (int columnIndex, Reader x, long length) throws SQLException {d.updateNCharacterStream(columnIndex, x, length);}
    public void updateNClob (String columnLabel, NClob nClob) throws SQLException {d.updateNClob(columnLabel, nClob);}
    public void updateNClob (String columnLabel, Reader reader) throws SQLException {d.updateNClob(columnLabel, reader);}
    public void updateNClob (String columnLabel, Reader reader, long length) throws SQLException {d.updateNClob(columnLabel, reader, length);}
    public void updateNClob (int columnIndex, NClob nClob) throws SQLException {d.updateNClob(columnIndex, nClob);}
    public void updateNClob (int columnIndex, Reader reader) throws SQLException {d.updateNClob(columnIndex, reader);}
    public void updateNClob (int columnIndex, Reader reader, long length) throws SQLException {d.updateNClob(columnIndex, reader, length);}
    public void updateNString (String columnLabel, String nString) throws SQLException {d.updateNString(columnLabel, nString);}
    public void updateNString (int columnIndex, String nString) throws SQLException {d.updateNString(columnIndex, nString);}
    public void updateNull (String columnLabel) throws SQLException {d.updateNull(columnLabel);}
    public void updateNull (int columnIndex) throws SQLException {d.updateNull(columnIndex);}
    public void updateObject (String columnLabel, Object x) throws SQLException {d.updateObject(columnLabel, x);}
    public void updateObject (String columnLabel, Object x, int scaleOrLength) throws SQLException {d.updateObject(columnLabel, x, scaleOrLength);}
    public void updateObject (int columnIndex, Object x) throws SQLException {d.updateObject(columnIndex, x);}
    public void updateObject (int columnIndex, Object x, int scaleOrLength) throws SQLException {d.updateObject(columnIndex, x, scaleOrLength);}
    public void updateRef (String columnLabel, Ref x) throws SQLException {d.updateRef(columnLabel, x);}
    public void updateRef (int columnIndex, Ref x) throws SQLException {d.updateRef(columnIndex, x);}
    public void updateRow () throws SQLException {d.updateRow();}
    public void updateRowId (String columnLabel, RowId x) throws SQLException {d.updateRowId(columnLabel, x);}
    public void updateRowId (int columnIndex, RowId x) throws SQLException {d.updateRowId(columnIndex, x);}
    public void updateSQLXML (String columnLabel, SQLXML xmlObject) throws SQLException {d.updateSQLXML(columnLabel, xmlObject);}
    public void updateSQLXML (int columnIndex, SQLXML xmlObject) throws SQLException {d.updateSQLXML(columnIndex, xmlObject);}
    public void updateShort (String columnLabel, short x) throws SQLException {d.updateShort(columnLabel, x);}
    public void updateShort (int columnIndex, short x) throws SQLException {d.updateShort(columnIndex, x);}
    public void updateString (String columnLabel, String x) throws SQLException {d.updateString(columnLabel, x);}
    public void updateString (int columnIndex, String x) throws SQLException {d.updateString(columnIndex, x);}
    public void updateTime (String columnLabel, Time x) throws SQLException {d.updateTime(columnLabel, x);}
    public void updateTime (int columnIndex, Time x) throws SQLException {d.updateTime(columnIndex, x);}
    public void updateTimestamp (String columnLabel, Timestamp x) throws SQLException {d.updateTimestamp(columnLabel, x);}
    public void updateTimestamp (int columnIndex, Timestamp x) throws SQLException {d.updateTimestamp(columnIndex, x);}}
