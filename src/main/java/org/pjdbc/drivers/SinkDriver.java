package org.pjdbc.drivers;

import java.sql.*;
import java.util.*;
import org.pjdbc.sql.*;

public class SinkDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new SinkDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "sink".equals(subprotocol);}

    protected Statement proxyStatement (Statement delegate, Connection conn) throws SQLException {
	return new AbstractStatement(delegate, conn) {
	    public void addBatch (String sql) throws SQLException {}
	    public boolean execute (String sql) throws SQLException {return true;}
	    public boolean execute (String sql, int[] columnIndexes) throws SQLException {return true;}
	    public boolean execute (String sql, String[] columnNames) throws SQLException {return true;}
	    public ResultSet executeQuery (String sql) throws SQLException {return null;}
	    public int executeUpdate (String sql) throws SQLException {return 0;}
	    public int executeUpdate (String sql, int autoGeneratedKeys) throws SQLException {return 0;}
	    public int executeUpdate (String sql, int[] columnIndexes) throws SQLException {return 0;}
	    public int executeUpdate (String sql, String[] columnNames) throws SQLException {return 0;}};}}
