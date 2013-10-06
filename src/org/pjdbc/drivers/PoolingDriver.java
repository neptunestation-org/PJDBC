package org.pjdbc.drivers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;
import org.pjdbc.util.AbstractProxyDriver;
import org.pjdbc.util.Pool;

public class PoolingDriver extends AbstractProxyDriver {
    private Pool<Properties, Connection> pool = new Pool<Properties, Connection>();

    public int getMajorVersion () {
	return 1;}

    public int getMinorVersion () {
	return 0;}

    public boolean jdbcCompliant () {
	return false;}

    public boolean acceptsSubProtocol (String subprotocol) {
	return "pool".equals(subprotocol);}

    public Connection connect (String url, Properties info) throws SQLException {
	if (!acceptsURL(url)) return null;
	Properties key = new Properties(info);
	key.setProperty("subname", subname(url));
	Connection conn = pool.take(key);
	return (conn!=null) ? conn : proxyConnection(new ConnectionHandler(DriverManager.getConnection(subname(url), info), info));}

    private class ConnectionHandler implements InvocationHandler {
    	private Connection delegate;
	private Properties key;
    	public ConnectionHandler (Connection delegate, Properties key) {
	    delegate = delegate;
	    key = key;}
    	public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {
	    if ("close".equals(method.getName())) pool.put(key, (Connection)proxy);
	    return method.invoke(delegate, args);}}}