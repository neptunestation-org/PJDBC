package org.pjdbc.rmi;

import java.io.*;
import java.math.*;
import java.net.*;
import java.rmi.*;
import java.sql.*;
import java.util.*;

public interface ResultSetRemoteProxy extends WrapperRemoteProxy {
    boolean absolute (int row) throws RemoteException;
    void afterLast () throws RemoteException;
    void beforeFirst () throws RemoteException;
    void cancelRowUpdates () throws RemoteException;
    void clearWarnings () throws RemoteException;
    void close () throws RemoteException;
    void deleteRow () throws RemoteException;
    int findColumn (String columnLabel) throws RemoteException;
    boolean first () throws RemoteException;
    Array getArray (int columnIndex) throws RemoteException;
    Array getArray (String columnLabel) throws RemoteException;
    InputStream getAsciiStream (int columnIndex) throws RemoteException;
    InputStream getAsciiStream (String columnLabel) throws RemoteException;
    BigDecimal getBigDecimal (int columnIndex) throws RemoteException;
    BigDecimal getBigDecimal (int columnIndex, int scale) throws RemoteException;
    BigDecimal getBigDecimal (String columnLabel) throws RemoteException;
    BigDecimal getBigDecimal (String columnLabel, int scale) throws RemoteException;
    InputStream getBinaryStream (int columnIndex) throws RemoteException;
    InputStream getBinaryStream (String columnLabel) throws RemoteException;
    Blob getBlob (int columnIndex) throws RemoteException;
    Blob getBlob (String columnLabel) throws RemoteException;
    boolean getBoolean (int columnIndex) throws RemoteException;
    boolean getBoolean (String columnLabel) throws RemoteException;
    byte getByte (int columnIndex) throws RemoteException;
    byte getByte (String columnLabel) throws RemoteException;
    byte[] getBytes (int columnIndex) throws RemoteException;
    byte[] getBytes (String columnLabel) throws RemoteException;
    Reader getCharacterStream (int columnIndex) throws RemoteException;
    Reader getCharacterStream (String columnLabel) throws RemoteException;
    Clob getClob (int columnIndex) throws RemoteException;
    Clob getClob (String columnLabel) throws RemoteException;
    int getConcurrency () throws RemoteException;
    String getCursorName () throws RemoteException;
    java.sql.Date getDate (int columnIndex) throws RemoteException;
    java.sql.Date getDate (int columnIndex, Calendar cal) throws RemoteException;
    java.sql.Date getDate (String columnLabel) throws RemoteException;
    java.sql.Date getDate (String columnLabel, Calendar cal) throws RemoteException;
    double getDouble (int columnIndex) throws RemoteException;
    double getDouble (String columnLabel) throws RemoteException;
    int getFetchDirection () throws RemoteException;
    int getFetchSize () throws RemoteException;
    float getFloat (int columnIndex) throws RemoteException;
    float getFloat (String columnLabel) throws RemoteException;
    int getHoldability () throws RemoteException;
    int getInt (int columnIndex) throws RemoteException;
    int getInt (String columnLabel) throws RemoteException;
    long getLong (int columnIndex) throws RemoteException;
    long getLong (String columnLabel) throws RemoteException;
    ResultSetMetaData getMetaData () throws RemoteException;
    Reader getNCharacterStream (int columnIndex) throws RemoteException;
    Reader getNCharacterStream (String columnLabel) throws RemoteException;
    NClob getNClob (int columnIndex) throws RemoteException;
    NClob getNClob (String columnLabel) throws RemoteException;
    String getNString (int columnIndex) throws RemoteException;
    String getNString (String columnLabel) throws RemoteException;
    Object getObject (int columnIndex) throws RemoteException;
    <T> T getObject (int columnIndex, Class<T> type) throws RemoteException;
    Object getObject (int columnIndex, Map<String,Class<?>> map) throws RemoteException;
    Object getObject (String columnLabel) throws RemoteException;
    <T> T getObject (String columnLabel, Class<T> type) throws RemoteException;
    Object getObject (String columnLabel, Map<String,Class<?>> map) throws RemoteException;
    Ref getRef (int columnIndex) throws RemoteException;
    Ref getRef (String columnLabel) throws RemoteException;
    int getRow () throws RemoteException;
    RowId getRowId (int columnIndex) throws RemoteException;
    RowId getRowId (String columnLabel) throws RemoteException;
    short getShort (int columnIndex) throws RemoteException;
    short getShort (String columnLabel) throws RemoteException;
    SQLXML getSQLXML (int columnIndex) throws RemoteException;
    SQLXML getSQLXML (String columnLabel) throws RemoteException;
    Statement getStatement () throws RemoteException;
    String getString (int columnIndex) throws RemoteException;
    String getString (String columnLabel) throws RemoteException;
    Time getTime (int columnIndex) throws RemoteException;
    Time getTime (int columnIndex, Calendar cal) throws RemoteException;
    Time getTime (String columnLabel) throws RemoteException;
    Time getTime (String columnLabel, Calendar cal) throws RemoteException;
    Timestamp getTimestamp (int columnIndex) throws RemoteException;
    Timestamp getTimestamp (int columnIndex, Calendar cal) throws RemoteException;
    Timestamp getTimestamp (String columnLabel) throws RemoteException;
    Timestamp getTimestamp (String columnLabel, Calendar cal) throws RemoteException;
    int getType () throws RemoteException;
    InputStream getUnicodeStream (int columnIndex) throws RemoteException;
    InputStream getUnicodeStream (String columnLabel) throws RemoteException;
    URL getURL (int columnIndex) throws RemoteException;
    URL getURL (String columnLabel) throws RemoteException;
    SQLWarning getWarnings () throws RemoteException;
    void insertRow () throws RemoteException;
    boolean isAfterLast () throws RemoteException;
    boolean isBeforeFirst () throws RemoteException;
    boolean isClosed () throws RemoteException;
    boolean isFirst () throws RemoteException;
    boolean isLast () throws RemoteException;
    boolean last () throws RemoteException;
    void moveToCurrentRow () throws RemoteException;
    void moveToInsertRow () throws RemoteException;
    boolean next () throws RemoteException;
    boolean previous () throws RemoteException;
    void refreshRow () throws RemoteException;
    boolean relative (int rows) throws RemoteException;
    boolean rowDeleted () throws RemoteException;
    boolean rowInserted () throws RemoteException;
    boolean rowUpdated () throws RemoteException;
    void setFetchDirection (int direction) throws RemoteException;
    void setFetchSize (int rows) throws RemoteException;
    void updateArray (int columnIndex, Array x) throws RemoteException;
    void updateArray (String columnLabel, Array x) throws RemoteException;
    void updateAsciiStream (int columnIndex, InputStream x) throws RemoteException;
    void updateAsciiStream (int columnIndex, InputStream x, int length) throws RemoteException;
    void updateAsciiStream (int columnIndex, InputStream x, long length) throws RemoteException;
    void updateAsciiStream (String columnLabel, InputStream x) throws RemoteException;
    void updateAsciiStream (String columnLabel, InputStream x, int length) throws RemoteException;
    void updateAsciiStream (String columnLabel, InputStream x, long length) throws RemoteException;
    void updateBigDecimal (int columnIndex, BigDecimal x) throws RemoteException;
    void updateBigDecimal (String columnLabel, BigDecimal x) throws RemoteException;
    void updateBinaryStream (int columnIndex, InputStream x) throws RemoteException;
    void updateBinaryStream (int columnIndex, InputStream x, int length) throws RemoteException;
    void updateBinaryStream (int columnIndex, InputStream x, long length) throws RemoteException;
    void updateBinaryStream (String columnLabel, InputStream x) throws RemoteException;
    void updateBinaryStream (String columnLabel, InputStream x, int length) throws RemoteException;
    void updateBinaryStream (String columnLabel, InputStream x, long length) throws RemoteException;
    void updateBlob (int columnIndex, Blob x) throws RemoteException;
    void updateBlob (int columnIndex, InputStream inputStream) throws RemoteException;
    void updateBlob (int columnIndex, InputStream inputStream, long length) throws RemoteException;
    void updateBlob (String columnLabel, Blob x) throws RemoteException;
    void updateBlob (String columnLabel, InputStream inputStream) throws RemoteException;
    void updateBlob (String columnLabel, InputStream inputStream, long length) throws RemoteException;
    void updateBoolean (int columnIndex, boolean x) throws RemoteException;
    void updateBoolean (String columnLabel, boolean x) throws RemoteException;
    void updateByte (int columnIndex, byte x) throws RemoteException;
    void updateByte (String columnLabel, byte x) throws RemoteException;
    void updateBytes (int columnIndex, byte[] x) throws RemoteException;
    void updateBytes (String columnLabel, byte[] x) throws RemoteException;
    void updateCharacterStream (int columnIndex, Reader x) throws RemoteException;
    void updateCharacterStream (int columnIndex, Reader x, int length) throws RemoteException;
    void updateCharacterStream (int columnIndex, Reader x, long length) throws RemoteException;
    void updateCharacterStream (String columnLabel, Reader reader) throws RemoteException;
    void updateCharacterStream (String columnLabel, Reader reader, int length) throws RemoteException;
    void updateCharacterStream (String columnLabel, Reader reader, long length) throws RemoteException;
    void updateClob (int columnIndex, Clob x) throws RemoteException;
    void updateClob (int columnIndex, Reader reader) throws RemoteException;
    void updateClob (int columnIndex, Reader reader, long length) throws RemoteException;
    void updateClob (String columnLabel, Clob x) throws RemoteException;
    void updateClob (String columnLabel, Reader reader) throws RemoteException;
    void updateClob (String columnLabel, Reader reader, long length) throws RemoteException;
    void updateDate (int columnIndex, java.sql.Date x) throws RemoteException;
    void updateDate (String columnLabel, java.sql.Date x) throws RemoteException;
    void updateDouble (int columnIndex, double x) throws RemoteException;
    void updateDouble (String columnLabel, double x) throws RemoteException;
    void updateFloat (int columnIndex, float x) throws RemoteException;
    void updateFloat (String columnLabel, float x) throws RemoteException;
    void updateInt (int columnIndex, int x) throws RemoteException;
    void updateInt (String columnLabel, int x) throws RemoteException;
    void updateLong (int columnIndex, long x) throws RemoteException;
    void updateLong (String columnLabel, long x) throws RemoteException;
    void updateNCharacterStream (int columnIndex, Reader x) throws RemoteException;
    void updateNCharacterStream (int columnIndex, Reader x, long length) throws RemoteException;
    void updateNCharacterStream (String columnLabel, Reader reader) throws RemoteException;
    void updateNCharacterStream (String columnLabel, Reader reader, long length) throws RemoteException;
    void updateNClob (int columnIndex, NClob nClob) throws RemoteException;
    void updateNClob (int columnIndex, Reader reader) throws RemoteException;
    void updateNClob (int columnIndex, Reader reader, long length) throws RemoteException;
    void updateNClob (String columnLabel, NClob nClob) throws RemoteException;
    void updateNClob (String columnLabel, Reader reader) throws RemoteException;
    void updateNClob (String columnLabel, Reader reader, long length) throws RemoteException;
    void updateNString (int columnIndex, String nString) throws RemoteException;
    void updateNString (String columnLabel, String nString) throws RemoteException;
    void updateNull (int columnIndex) throws RemoteException;
    void updateNull (String columnLabel) throws RemoteException;
    void updateObject (int columnIndex, Object x) throws RemoteException;
    void updateObject (int columnIndex, Object x, int scaleOrLength) throws RemoteException;
    void updateObject (String columnLabel, Object x) throws RemoteException;
    void updateObject (String columnLabel, Object x, int scaleOrLength) throws RemoteException;
    void updateRef (int columnIndex, Ref x) throws RemoteException;
    void updateRef (String columnLabel, Ref x) throws RemoteException;
    void updateRow () throws RemoteException;
    void updateRowId (int columnIndex, RowId x) throws RemoteException;
    void updateRowId (String columnLabel, RowId x) throws RemoteException;
    void updateShort (int columnIndex, short x) throws RemoteException;
    void updateShort (String columnLabel, short x) throws RemoteException;
    void updateSQLXML (int columnIndex, SQLXML xmlObject) throws RemoteException;
    void updateSQLXML (String columnLabel, SQLXML xmlObject) throws RemoteException;
    void updateString (int columnIndex, String x) throws RemoteException;
    void updateString (String columnLabel, String x) throws RemoteException;
    void updateTime (int columnIndex, Time x) throws RemoteException;
    void updateTime (String columnLabel, Time x) throws RemoteException;
    void updateTimestamp (int columnIndex, Timestamp x) throws RemoteException;
    void updateTimestamp (String columnLabel, Timestamp x) throws RemoteException;
    boolean wasNull () throws RemoteException;}
