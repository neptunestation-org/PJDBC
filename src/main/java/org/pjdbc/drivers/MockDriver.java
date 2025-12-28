package org.pjdbc.drivers;

import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.pjdbc.sql.*;

public class MockDriver extends AbstractDriver {
    static {try {DriverManager.registerDriver(new MockDriver());} catch (Exception e) {throw new RuntimeException(e);}}
    static {System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s\n");}

    public static class MyPrintWriter extends PrintWriter {
	private OutputStream out;
	public MyPrintWriter (OutputStream out) {super(out); this.out = out;}
	public OutputStream getStream () {return this.out;}}

    private static class LoggingInvocationHandler implements InvocationHandler {
	private PrintWriter l;
	public LoggingInvocationHandler (PrintWriter log) {
	    this.l = log;}
	public Object invoke (Object proxy, Method method, Object[] args) {
	    l.println(method.getName() + (args!=null && args.length>0 ? Arrays.asList(args) : new ArrayList<Object>()));
	    return null;}}

    private static Map<String, MyPrintWriter> logs = new HashMap<String, MyPrintWriter>();

    public static String getLog (String url) {
	if (logs.containsKey(url)) {
	    logs.get(url).flush();
	    return logs.get(url).getStream().toString().trim();}
	return "";}

    public static void clearLogs () {
	logs.clear();}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "mock".equals(subprotocol);}

    protected boolean acceptsSubName (String subname) {
	return true;}

    public Connection connect (final String url, Properties info) throws SQLException {
	if (!acceptsURL(url)) return null;
	logs.put(url, new MyPrintWriter(new ByteArrayOutputStream()));
	final PrintWriter l = logs.get(url);
	return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, new InvocationHandler() {
		public Object invoke (Object proxy, Method method, Object[] args) throws SQLException {
		    if ("createStatement".equals(method.getName()))
			return (Statement)
			    Proxy.newProxyInstance(getClass().getClassLoader(),
						   new Class<?>[]{Statement.class},
						   new LoggingInvocationHandler(l));
		    if ("prepareCall".equals(method.getName()))
			return (CallableStatement)
			    Proxy.newProxyInstance(getClass().getClassLoader(),
						   new Class<?>[]{CallableStatement.class},
						   new LoggingInvocationHandler(l));
		    if ("prepareStatement".equals(method.getName()))
			return (PreparedStatement)
			    Proxy.newProxyInstance(getClass().getClassLoader(),
						   new Class<?>[]{PreparedStatement.class},
						   new LoggingInvocationHandler(l));
		    if ("getMetaData".equals(method.getName()))
			return (DatabaseMetaData)
			    Proxy.newProxyInstance(getClass().getClassLoader(),
						   new Class<?>[]{DatabaseMetaData.class},
						   new InvocationHandler() {
						       public Object invoke (Object proxy, Method method, Object[] args) {
							   return ("getURL".equals(method.getName())) ? url : null;}});
		    if ("toString".equals(method.getName())) return "MockDriver[" + url + "]";
		    if ("equals".equals(method.getName())) return proxy==args[0];
		    if ("isWrapperFor".equals(method.getName())) return false;
		    if ("unwrap".equals(method.getName()) && args.length==1 && Connection.class.isInstance(args[0])) return this;
		    if ("close".equals(method.getName())) return null;
		    if ("isClosed".equals(method.getName())) return false;
		    if ("getAutoCommit".equals(method.getName())) return true;
		    if ("setAutoCommit".equals(method.getName())) return null;
		    if ("commit".equals(method.getName())) return null;
		    if ("rollback".equals(method.getName())) return null;
		    throw new SQLException(String.format("%s unimplemented by MockDriver", method.getName()));}});}}
