package org.pjdbc.drivers;

import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.function.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.*;

/**
 * In-memory mock driver for testing JDBC code without a real database.
 *
 * <h2>Basic Usage</h2>
 * <pre>
 * // Get a connection (logs all method calls)
 * Connection conn = DriverManager.getConnection("jdbc:mock:test");
 *
 * // Check what was executed
 * String log = MockDriver.getLog("jdbc:mock:test");
 * </pre>
 *
 * <h2>Result Configuration API</h2>
 * <p>Configure what results queries should return:</p>
 * <pre>
 * // Configure a query to return specific data
 * MockDriver.when("SELECT * FROM users")
 *     .thenReturn(MockResultSet.create()
 *         .columns("id", "name", "email")
 *         .row(1, "Alice", "alice@example.com")
 *         .row(2, "Bob", "bob@example.com")
 *         .build());
 *
 * // Configure an update count
 * MockDriver.when("INSERT INTO users").thenUpdate(1);
 *
 * // Configure an exception
 * MockDriver.when("DROP TABLE").thenThrow(new SQLException("Not allowed"));
 *
 * // Pattern matching with regex
 * MockDriver.whenMatches("SELECT.*FROM users WHERE id = .*")
 *     .thenReturn(MockResultSet.create()
 *         .columns("id", "name")
 *         .row(1, "Alice")
 *         .build());
 *
 * // Dynamic result based on SQL
 * MockDriver.whenMatches("SELECT.*FROM users WHERE id = (\\d+)")
 *     .thenAnswer(sql -&gt; {
 *         Matcher m = Pattern.compile("id = (\\d+)").matcher(sql);
 *         if (m.find()) {
 *             int id = Integer.parseInt(m.group(1));
 *             return MockResultSet.create()
 *                 .columns("id", "name")
 *                 .row(id, "User" + id)
 *                 .build();
 *         }
 *         return MockResultSet.empty();
 *     });
 *
 * // Reset all configurations
 * MockDriver.reset();
 * </pre>
 */
@DriverCapability(
    prefix = "mock",
    description = "In-memory mock driver for testing",
    capabilities = {"testing"},
    composable = false,
    terminal = true
)
@DriverSideEffects(stateful = true)
public class MockDriver extends AbstractDriver {
    static {try {DriverManager.registerDriver(new MockDriver());} catch (Exception e) {throw new RuntimeException(e);}}
    static {System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s\n");}

    // ========== Result Configuration API ==========

    /**
     * Represents a configured expectation for SQL execution.
     */
    public static class Expectation {
        private final String sqlPattern;
        private final boolean isRegex;
        private ResultSet resultSet;
        private Integer updateCount;
        private SQLException exception;
        private java.util.function.Function<String, ResultSet> resultFunction;
        private java.util.function.Function<String, Integer> updateFunction;

        Expectation(String sqlPattern, boolean isRegex) {
            this.sqlPattern = sqlPattern;
            this.isRegex = isRegex;
        }

        /**
         * Configure this expectation to return a ResultSet.
         */
        public Expectation thenReturn(ResultSet rs) {
            this.resultSet = rs;
            return this;
        }

        /**
         * Configure this expectation to return a ResultSet from a MockResultSet.Builder.
         */
        public Expectation thenReturn(MockResultSet.Builder builder) {
            return thenReturn(builder.build());
        }

        /**
         * Configure this expectation to return an update count.
         */
        public Expectation thenUpdate(int count) {
            this.updateCount = count;
            return this;
        }

        /**
         * Configure this expectation to throw an exception.
         */
        public Expectation thenThrow(SQLException ex) {
            this.exception = ex;
            return this;
        }

        /**
         * Configure this expectation to compute results dynamically from the SQL.
         */
        public Expectation thenAnswer(java.util.function.Function<String, ResultSet> fn) {
            this.resultFunction = fn;
            return this;
        }

        /**
         * Configure this expectation to compute update count dynamically from the SQL.
         */
        public Expectation thenAnswerUpdate(java.util.function.Function<String, Integer> fn) {
            this.updateFunction = fn;
            return this;
        }

        boolean matches(String sql) {
            if (sql == null) return false;
            if (isRegex) {
                return Pattern.compile(sqlPattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql).matches();
            } else {
                return sql.toLowerCase().contains(sqlPattern.toLowerCase());
            }
        }

        ResultSet getResultSet(String sql) throws SQLException {
            if (exception != null) throw exception;
            if (resultFunction != null) return resultFunction.apply(sql);
            if (resultSet != null) {
                // Reset the result set position if possible
                try { resultSet.beforeFirst(); } catch (SQLException e) { /* ignore */ }
                return resultSet;
            }
            return null;
        }

        Integer getUpdateCount(String sql) throws SQLException {
            if (exception != null) throw exception;
            if (updateFunction != null) return updateFunction.apply(sql);
            return updateCount;
        }

        boolean hasResult() {
            return resultSet != null || resultFunction != null;
        }

        boolean hasUpdateCount() {
            return updateCount != null || updateFunction != null;
        }

        boolean hasException() {
            return exception != null;
        }
    }

    private static final List<Expectation> expectations = new CopyOnWriteArrayList<>();

    /**
     * Configure an expectation for SQL containing the given string.
     * <p>Example: {@code MockDriver.when("SELECT * FROM users").thenReturn(resultSet);}
     *
     * @param sqlContains SQL substring to match (case-insensitive)
     * @return an Expectation to configure the result
     */
    public static Expectation when(String sqlContains) {
        Expectation exp = new Expectation(sqlContains, false);
        expectations.add(exp);
        return exp;
    }

    /**
     * Configure an expectation for SQL matching the given regex pattern.
     * <p>Example: {@code MockDriver.whenMatches("SELECT.*FROM users WHERE id = \\d+").thenReturn(resultSet);}
     *
     * @param sqlPattern regex pattern to match (case-insensitive, DOTALL mode)
     * @return an Expectation to configure the result
     */
    public static Expectation whenMatches(String sqlPattern) {
        Expectation exp = new Expectation(sqlPattern, true);
        expectations.add(exp);
        return exp;
    }

    /**
     * Find the first matching expectation for the given SQL.
     */
    private static Expectation findExpectation(String sql) {
        for (Expectation exp : expectations) {
            if (exp.matches(sql)) {
                return exp;
            }
        }
        return null;
    }

    /**
     * Reset all configured expectations.
     */
    public static void reset() {
        expectations.clear();
        clearLogs();
    }

    // ========== Original MockDriver implementation ==========

    public static class MyPrintWriter extends PrintWriter {
        private OutputStream out;
        public MyPrintWriter (OutputStream out) {super(out); this.out = out;}
        public OutputStream getStream () {return this.out;}}

    private static class LoggingInvocationHandler implements InvocationHandler {
        private PrintWriter l;
        private ClassLoader cl;
        private String lastSql;

        public LoggingInvocationHandler (PrintWriter log, ClassLoader classLoader) {
            this.l = log;
            this.cl = classLoader;
        }

        public Object invoke (Object proxy, Method method, Object[] args) throws SQLException {
            l.println(method.getName() + (args!=null && args.length>0 ? Arrays.asList(args) : new ArrayList<Object>()));

            // Track SQL for pattern matching
            if (args != null && args.length > 0 && args[0] instanceof String) {
                lastSql = (String) args[0];
            }

            // Check for configured expectations
            Expectation exp = findExpectation(lastSql);

            if ("executeQuery".equals(method.getName()) || "getResultSet".equals(method.getName())) {
                if (exp != null && (exp.hasResult() || exp.hasException())) {
                    return exp.getResultSet(lastSql);
                }
                // Default: empty result set
                return (ResultSet)Proxy.newProxyInstance(cl, new Class<?>[]{ResultSet.class},
                    new InvocationHandler() {
                        public Object invoke(Object p, Method m, Object[] a) {
                            if ("close".equals(m.getName())) return null;
                            if ("isClosed".equals(m.getName())) return false;
                            if ("next".equals(m.getName())) return false;
                            if ("getMetaData".equals(m.getName())) return null;
                            return null;}});
            }

            if ("execute".equals(method.getName())) {
                if (exp != null && exp.hasException()) {
                    exp.getResultSet(lastSql); // throws exception
                }
                if (exp != null && exp.hasResult()) {
                    return true; // has result set
                }
                if (exp != null && exp.hasUpdateCount()) {
                    return false; // has update count
                }
                return false;
            }

            if ("executeUpdate".equals(method.getName())) {
                if (exp != null && (exp.hasUpdateCount() || exp.hasException())) {
                    Integer count = exp.getUpdateCount(lastSql);
                    return count != null ? count : 0;
                }
                return 0;
            }

            if ("getUpdateCount".equals(method.getName())) {
                if (exp != null && exp.hasUpdateCount()) {
                    try {
                        Integer count = exp.getUpdateCount(lastSql);
                        return count != null ? count : -1;
                    } catch (SQLException e) {
                        return -1;
                    }
                }
                return -1;
            }

            if ("close".equals(method.getName())) return null;
            if ("isClosed".equals(method.getName())) return false;
            if ("getConnection".equals(method.getName())) return null;
            if ("getMoreResults".equals(method.getName())) return false;
            return null;
        }
    }

    private static Map<String, MyPrintWriter> logs = new ConcurrentHashMap<String, MyPrintWriter>();
    private static Map<String, Properties> infos = new ConcurrentHashMap<String, Properties>();

    public static Properties getLastConnectionInfo (String url) {
        return infos.get(url);
    }

    public static String getLog (String url) {
        if (logs.containsKey(url)) {
            logs.get(url).flush();
            return logs.get(url).getStream().toString().trim();}
        return "";}

    public static void clearLogs () {
        logs.clear();}

    public static void clearLog (String url) {
        logs.remove(url);}

    protected boolean acceptsSubProtocol (String subprotocol) {
        return "mock".equals(subprotocol);}

    protected boolean acceptsSubName (String subname) {
        return true;}

    public Connection connect (final String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        logs.put(url, new MyPrintWriter(new ByteArrayOutputStream()));
        if (info != null) infos.put(url, info);
        final PrintWriter l = logs.get(url);
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, new InvocationHandler() {
                public Object invoke (Object proxy, Method method, Object[] args) throws SQLException {
                    if ("createStatement".equals(method.getName()))
                        return (Statement)
                            Proxy.newProxyInstance(getClass().getClassLoader(),
                                                   new Class<?>[]{Statement.class},
                                                   new LoggingInvocationHandler(l, getClass().getClassLoader()));
                    if ("prepareCall".equals(method.getName()))
                        return (CallableStatement)
                            Proxy.newProxyInstance(getClass().getClassLoader(),
                                                   new Class<?>[]{CallableStatement.class},
                                                   new LoggingInvocationHandler(l, getClass().getClassLoader()));
                    if ("prepareStatement".equals(method.getName())) {
                        // Track the SQL from prepareStatement
                        String sql = args != null && args.length > 0 ? (String) args[0] : null;
                        LoggingInvocationHandler handler = new LoggingInvocationHandler(l, getClass().getClassLoader());
                        handler.lastSql = sql;
                        return (PreparedStatement)
                            Proxy.newProxyInstance(getClass().getClassLoader(),
                                                   new Class<?>[]{PreparedStatement.class},
                                                   handler);
                    }
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
