package org.pjdbc.drivers;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.logging.Level;
import org.pjdbc.util.AbstractProxyDriver;
import org.pjdbc.util.AbstractProxyStatement;

public class LoggingDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new LoggingDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    private static final Logger LOGGER = Logger.getLogger(LoggingDriver.class.getName());
    
    static {LOGGER.setLevel(Level.INFO);}

    protected Statement proxyStatement (Connection conn, Statement delegate) {
	return new AbstractProxyStatement(conn, delegate) {
	    public void addBatch (String sql) throws SQLException {
		LOGGER.info(sql);
		super.addBatch(sql);}
	    public boolean execute (String sql) throws SQLException {
		LOGGER.info(sql);
		return super.execute(sql);}
	    public boolean execute (String sql, int[] columnIndexes) throws SQLException {
		LOGGER.info(sql);
		return super.execute(sql, columnIndexes);}
	    public boolean execute (String sql, String[] columnNames) throws SQLException {
		LOGGER.info(sql);
		return super.execute(sql, columnNames);}
	    public ResultSet executeQuery (String sql) throws SQLException {
		LOGGER.info(sql);
		return super.executeQuery(sql);}
	    public int executeUpdate (String sql) throws SQLException {
		LOGGER.info(sql);
		return super.executeUpdate(sql);}
	    public int executeUpdate (String sql, int autoGeneratedKeys) throws SQLException {
		LOGGER.info(sql);
		return super.executeUpdate(sql, autoGeneratedKeys);}
	    public int executeUpdate (String sql, int[] columnIndexes) throws SQLException {
		LOGGER.info(sql);
		return super.executeUpdate(sql, columnIndexes);}
	    public int executeUpdate (String sql, String[] columnNames) throws SQLException {
		LOGGER.info(sql);
		return super.executeUpdate(sql, columnNames);}};}

    public boolean acceptsSubProtocol (String subprotocol) {
	return "log".equals(subprotocol);}}

