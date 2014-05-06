package org.pjdbc.util;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.pjdbc.sql.*;
import org.pjdbc.util.*;

public abstract class AbstractProxyDriver extends AbstractDriver {
    protected boolean acceptsSubName (String subname) {
	try {return DriverManager.getDriver(subname)!=null;}
	catch (Exception e) {};
	return false;}

    protected Connection proxyConnection (Connection conn, String url, Properties info, Driver driver) throws SQLException {
	return new AbstractConnection(conn, this, url, info) {
	    public Statement createStatement () throws SQLException {
		return proxyStatement(d.createStatement(), this);}
	    public Statement createStatement (int resultSetType, int resultSetConcurrency) throws SQLException {
		return proxyStatement(d.createStatement(resultSetType, resultSetConcurrency), this);}
	    public Statement createStatement (int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return proxyStatement(d.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);}
	    public CallableStatement prepareCall (String sql) throws SQLException {
		return proxyCallableStatement(d.prepareCall(sql), this);}
	    public CallableStatement prepareCall (String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return proxyCallableStatement(d.prepareCall(sql, resultSetType, resultSetConcurrency), this);}
	    public CallableStatement prepareCall (String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return proxyCallableStatement(d.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);}
	    public PreparedStatement prepareStatement (String sql) throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql), this);}
	    public PreparedStatement prepareStatement (String sql, int autoGeneratedKeys) throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql, autoGeneratedKeys), this);}
	    public PreparedStatement prepareStatement (String sql, int[] columnIndexes)	throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql, columnIndexes), this);}
	    public PreparedStatement prepareStatement (String sql, int resultSetType, int resultSetConcurrency)	throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql, resultSetType, resultSetConcurrency), this);}
	    public PreparedStatement prepareStatement (String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);}
	    public PreparedStatement prepareStatement (String sql, String[] columnNames) throws SQLException {
		return proxyPreparedStatement(d.prepareStatement(sql, columnNames), this);}};}

    protected Connection proxyConnection (Connection delegate, String url, Properties info, List<Connection> delegates) throws SQLException {
	return new AbstractConnection(delegate, this, url, info) {};}

    protected Statement proxyStatement (Statement delegate, Connection conn) throws SQLException {
	return new AbstractStatement(conn, delegate) {};}

    protected CallableStatement proxyCallableStatement (CallableStatement delegate, Connection conn) throws SQLException {
	return new AbstractCallableStatement(conn, delegate) {};}

    protected PreparedStatement proxyPreparedStatement (PreparedStatement delegate, Connection conn) throws SQLException {
	return new AbstractPreparedStatement(conn, delegate) {};}

    protected ResultSet proxyResultSet (Statement stmt, ResultSet delegate) throws SQLException {
	return delegate;}

    public Connection connect (String url, Properties info) throws SQLException {
    	if (!acceptsURL(url)) return null;
	return proxyConnection(DriverManager.getConnection(subname(url)), subname(url), info, this);}

    public DriverPropertyInfo[] getPropertyInfo (String url, Properties info) throws SQLException {
	if (!acceptsURL(url)) throw new SQLException("Invalid url:  " + url);
	Driver driver = DriverManager.getDriver(subname(url));
	if (driver==null) throw new SQLException("No valid target driver registered for:  " + subname(url));
	return driver.getPropertyInfo(subname(url), info);}}
