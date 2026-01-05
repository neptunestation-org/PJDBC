package org.pjdbc.sql;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractConnection extends AbstractWrapper implements Connection {
    protected Driver driver;
    protected String url;
    protected Properties info;

    protected List<Connection> connectionDelegates = new ArrayList<Connection>();

    protected List<Connection> getConnections() {
        return connectionDelegates;
    }

    protected Connection getDelegate() {
        for (Connection c : connectionDelegates) {
            return c;
        }
        return null;
    }

    public Driver getDriver() {
        return driver;
    }

    protected Statement wrap(Statement stmt) throws SQLException {
        return new AbstractStatement(stmt, this) {};
    }

    protected Statement wrap(Statement[] stmts) throws SQLException {
        return new AbstractStatement(stmts, this) {};
    }

    protected PreparedStatement wrap(PreparedStatement s) throws SQLException {
        return new AbstractPreparedStatement(s, this) {};
    }

    protected PreparedStatement wrap(PreparedStatement[] stmts) throws SQLException {
        return new AbstractPreparedStatement(stmts, this) {};
    }

    protected CallableStatement wrap(CallableStatement s) throws SQLException {
        return new AbstractCallableStatement(s, this) {};
    }

    protected DatabaseMetaData wrap(DatabaseMetaData d) throws SQLException {
        return new AbstractDatabaseMetaData(this, d) {};
    }

    public AbstractConnection(Connection conn) throws SQLException {
        super(conn);
        connectionDelegates.add(conn);
    }

    public AbstractConnection(Connection[] conns) throws SQLException {
        super(conns);
        connectionDelegates = Arrays.asList(conns);
    }

    public AbstractConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
        this(conn);
        this.url = url;
        this.info = info;
        this.driver = driver;
    }

    public AbstractConnection(Connection[] conns, Driver driver, String url, Properties info) throws SQLException {
        this(conns);
        this.url = url;
        this.info = info;
        this.driver = driver;
    }

    public String getURL() {
        return this.url;
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        for (Connection d : getConnections()) {
            return d.createArrayOf(typeName, elements);
        }
        throw new SQLException();
    }

    @Override
    public Blob createBlob() throws SQLException {
        for (Connection d : getConnections()) {
            return d.createBlob();
        }
        throw new SQLException();
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.prepareCall(sql));
        }
        throw new SQLException();
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.prepareCall(sql, resultSetType, resultSetConcurrency));
        }
        throw new SQLException();
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        throw new SQLException();
    }

    @Override
    public Clob createClob() throws SQLException {
        for (Connection d : getConnections()) {
            return d.createClob();
        }
        throw new SQLException();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.getMetaData());
        }
        throw new SQLException();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getTypeMap();
        }
        throw new SQLException();
    }

    @Override
    public NClob createNClob() throws SQLException {
        for (Connection d : getConnections()) {
            return d.createNClob();
        }
        throw new SQLException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql, columnNames));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql, autoGeneratedKeys));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql, resultSetType, resultSetConcurrency));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        ArrayList<PreparedStatement> stmts = new ArrayList<PreparedStatement>();
        for (Connection d : getConnections()) {
            stmts.add(d.prepareStatement(sql, columnIndexes));
        }
        return wrap(stmts.toArray(new PreparedStatement[0]));
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getClientInfo();
        }
        throw new SQLException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getWarnings();
        }
        throw new SQLException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        for (Connection d : getConnections()) {
            return d.createSQLXML();
        }
        throw new SQLException();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        for (Connection d : getConnections()) {
            return d.setSavepoint();
        }
        throw new SQLException();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        for (Connection d : getConnections()) {
            return d.setSavepoint(name);
        }
        throw new SQLException();
    }

    @Override
    public Statement createStatement() throws SQLException {
        ArrayList<Statement> stmts = new ArrayList<Statement>();
        for (Connection d : getConnections()) {
            stmts.add(d.createStatement());
        }
        return wrap(stmts.toArray(new Statement[0]));
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.createStatement(resultSetType, resultSetConcurrency));
        }
        throw new SQLException();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        for (Connection d : getConnections()) {
            return wrap(d.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        throw new SQLException();
    }

    @Override
    public String getCatalog() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getCatalog();
        }
        throw new SQLException();
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        for (Connection d : getConnections()) {
            return d.getClientInfo(name);
        }
        throw new SQLException();
    }

    @Override
    public String getSchema() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getSchema();
        }
        throw new SQLException();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        for (Connection d : getConnections()) {
            return d.nativeSQL(sql);
        }
        throw new SQLException();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        for (Connection d : getConnections()) {
            return d.createStruct(typeName, attributes);
        }
        throw new SQLException();
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        for (Connection d : getConnections()) {
            if (!d.getAutoCommit()) return false;
        }
        return true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        for (Connection d : getConnections()) {
            if (!d.isClosed()) return false;
        }
        return true;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        for (Connection d : getConnections()) {
            if (!d.isReadOnly()) return false;
        }
        return true;
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        for (Connection d : getConnections()) {
            if (!d.isValid(timeout)) return false;
        }
        return true;
    }

    @Override
    public int getHoldability() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getHoldability();
        }
        throw new SQLException();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getNetworkTimeout();
        }
        throw new SQLException();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        for (Connection d : getConnections()) {
            return d.getTransactionIsolation();
        }
        throw new SQLException();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        for (Connection d : getConnections()) {
            d.abort(executor);
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        for (Connection d : getConnections()) {
            d.clearWarnings();
        }
    }

    @Override
    public void close() throws SQLException {
        for (Connection d : getConnections()) {
            d.close();
        }
    }

    @Override
    public void commit() throws SQLException {
        for (Connection d : getConnections()) {
            d.commit();
        }
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        for (Connection d : getConnections()) {
            d.releaseSavepoint(savepoint);
        }
    }

    @Override
    public void rollback() throws SQLException {
        for (Connection d : getConnections()) {
            d.rollback();
        }
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        for (Connection d : getConnections()) {
            d.rollback(savepoint);
        }
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        for (Connection d : getConnections()) {
            d.setAutoCommit(autoCommit);
        }
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        for (Connection d : getConnections()) {
            d.setCatalog(catalog);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        for (Connection d : getConnections()) {
            d.setClientInfo(properties);
        }
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        for (Connection d : getConnections()) {
            d.setClientInfo(name, value);
        }
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        for (Connection d : getConnections()) {
            d.setHoldability(holdability);
        }
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        for (Connection d : getConnections()) {
            d.setNetworkTimeout(executor, milliseconds);
        }
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        for (Connection d : getConnections()) {
            d.setReadOnly(readOnly);
        }
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        for (Connection d : getConnections()) {
            d.setSchema(schema);
        }
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        for (Connection d : getConnections()) {
            d.setTransactionIsolation(level);
        }
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        for (Connection d : getConnections()) {
            d.setTypeMap(map);
        }
    }
}
