package org.pjdbc.sql;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractConnection extends AbstractWrapper implements Connection {
    private Driver driver;
    private String url;
    private Properties info;

    protected Connection d;

    protected Statement wrap (Statement s) throws SQLException {
	return new AbstractStatement(this, s){};}

    protected PreparedStatement wrap (PreparedStatement s) throws SQLException {
	return new AbstractPreparedStatement(this, s){};}

    protected CallableStatement wrap (CallableStatement s) throws SQLException {
	return new AbstractCallableStatement(this, s){};}

    protected DatabaseMetaData wrap (DatabaseMetaData d) throws SQLException {
	return new AbstractDatabaseMetaData(this, d){};}

    public AbstractConnection (Connection conn) throws SQLException {
	super(conn);
	this.d = conn;}

    public AbstractConnection (Connection conn, Driver driver, String url, Properties info) throws SQLException {
	this(conn);
	this.url = url;
	this.info = info;
	this.driver = driver;}

    public Array createArrayOf (String typeName, Object[] elements) throws SQLException {return d.createArrayOf(typeName, elements);}
    public Blob createBlob () throws SQLException {return d.createBlob();}
    public CallableStatement prepareCall (String sql) throws SQLException {return wrap(d.prepareCall(sql));}
    public CallableStatement prepareCall (String sql, int resultSetType, int resultSetConcurrency) throws SQLException {return wrap(d.prepareCall(sql, resultSetType, resultSetConcurrency));}
    public CallableStatement prepareCall (String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {return wrap(d.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));}
    public Clob createClob () throws SQLException {return d.createClob();}
    public DatabaseMetaData getMetaData () throws SQLException {return wrap(d.getMetaData());}
    public Map<String,Class<?>> getTypeMap () throws SQLException {return d.getTypeMap();}
    public NClob createNClob () throws SQLException {return d.createNClob();}
    public PreparedStatement prepareStatement (String sql) throws SQLException {return wrap(d.prepareStatement(sql));}
    public PreparedStatement prepareStatement (String sql, String[] columnNames) throws SQLException {return wrap(d.prepareStatement(sql, columnNames));}
    public PreparedStatement prepareStatement (String sql, int autoGeneratedKeys) throws SQLException {return wrap(d.prepareStatement(sql, autoGeneratedKeys));}
    public PreparedStatement prepareStatement (String sql, int resultSetType, int resultSetConcurrency) throws SQLException {return wrap(d.prepareStatement(sql, resultSetType, resultSetConcurrency));}
    public PreparedStatement prepareStatement (String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {return wrap(d.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));}
    public PreparedStatement prepareStatement (String sql, int[] columnIndexes) throws SQLException {return wrap(d.prepareStatement(sql, columnIndexes));}
    public Properties getClientInfo () throws SQLException {return d.getClientInfo();}
    public SQLWarning getWarnings () throws SQLException {return d.getWarnings();}
    public SQLXML createSQLXML () throws SQLException {return d.createSQLXML();}
    public Savepoint setSavepoint () throws SQLException {return d.setSavepoint();}
    public Savepoint setSavepoint (String name) throws SQLException {return d.setSavepoint(name);}
    public Statement createStatement () throws SQLException {return wrap(d.createStatement());}
    public Statement createStatement (int resultSetType, int resultSetConcurrency) throws SQLException {return wrap(d.createStatement(resultSetType, resultSetConcurrency));}
    public Statement createStatement (int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {return wrap(d.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));}
    public String getCatalog () throws SQLException {return d.getCatalog();}
    public String getClientInfo (String name) throws SQLException {return d.getClientInfo(name);}
    public String getSchema () throws SQLException {return d.getSchema();}
    public String nativeSQL (String sql) throws SQLException {return d.nativeSQL(sql);}
    public Struct createStruct (String typeName, Object[] attributes) throws SQLException {return d.createStruct(typeName, attributes);}
    public boolean getAutoCommit () throws SQLException {return d.getAutoCommit();}
    public boolean isClosed () throws SQLException {return d.isClosed();}
    public boolean isReadOnly () throws SQLException {return d.isReadOnly();}
    public boolean isValid (int timeout) throws SQLException {return d.isValid(timeout);}
    public int getHoldability () throws SQLException {return d.getHoldability();}
    public int getNetworkTimeout () throws SQLException {return d.getNetworkTimeout();}
    public int getTransactionIsolation () throws SQLException {return d.getTransactionIsolation();}
    public void abort (Executor executor) throws SQLException {d.abort(executor);}
    public void clearWarnings () throws SQLException {d.clearWarnings();}
    public void close () throws SQLException {d.close();}
    public void commit () throws SQLException {d.commit();}
    public void releaseSavepoint (Savepoint savepoint) throws SQLException {d.releaseSavepoint(savepoint);}
    public void rollback () throws SQLException {d.rollback();}
    public void rollback (Savepoint savepoint) throws SQLException {d.rollback(savepoint);}
    public void setAutoCommit (boolean autoCommit) throws SQLException {d.setAutoCommit(autoCommit);}
    public void setCatalog (String catalog) throws SQLException {d.setCatalog(catalog);}
    public void setClientInfo (Properties properties) throws SQLClientInfoException {d.setClientInfo(properties);}
    public void setClientInfo (String name, String value) throws SQLClientInfoException {d.setClientInfo(name, value);}
    public void setHoldability (int holdability) throws SQLException {d.setHoldability(holdability);}
    public void setNetworkTimeout (Executor executor, int milliseconds) throws SQLException {d.setNetworkTimeout(executor, milliseconds);}
    public void setReadOnly (boolean readOnly) throws SQLException {d.setReadOnly( readOnly);}
    public void setSchema (String schema) throws SQLException {d.setSchema(schema);}
    public void setTransactionIsolation (int level) throws SQLException {d.setTransactionIsolation(level);}
    public void setTypeMap (Map<String,Class<?>> map) throws SQLException {d.setTypeMap(map);}}