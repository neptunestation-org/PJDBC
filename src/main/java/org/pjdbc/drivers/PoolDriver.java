package org.pjdbc.drivers;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.pjdbc.sql.*;

public class PoolDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new PoolDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    private static final ConcurrentHashMap<String, BlockingQueue<Connection>> pools = new ConcurrentHashMap<String, BlockingQueue<Connection>>();
    private static final ConcurrentHashMap<String, PoolConfig> configs = new ConcurrentHashMap<String, PoolConfig>();

    // Default configuration values
    private static final int DEFAULT_MIN = 0;
    private static final int DEFAULT_MAX = -1; // unlimited
    private static final long DEFAULT_TIMEOUT = 1000L; // 1 second

    /**
     * Pool configuration parsed from URL parameters.
     */
    protected record PoolConfig(int min, int max, long timeout) { }

    /**
     * Parse pool configuration from URL parameters.
     * Supports: min, max, timeout (case-insensitive)
     */
    protected PoolConfig parseConfig(String url) {
        int min = DEFAULT_MIN;
        int max = DEFAULT_MAX;
        long timeout = DEFAULT_TIMEOUT;

        try {
            JdbcUrlParser parser = parseUrl(url);
            Map<String, String> params = parser.getParameters();

            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey().toLowerCase();
                String value = entry.getValue();
                try {
                    switch (key) {
                        case "min" -> min = Integer.parseInt(value);
                        case "max" -> max = Integer.parseInt(value);
                        case "timeout" -> timeout = Long.parseLong(value);
                        default -> { }
                    }
                } catch (NumberFormatException e) {
                    // Invalid value, use default
                }
            }
        } catch (IllegalArgumentException e) {
            // URL parsing failed, use defaults
        }

        return new PoolConfig(min, max, timeout);
    }

    protected String getPoolKey(String url, Properties info) throws SQLException {
        // Use subname as pool key (the underlying URL)
        return subname(url);
    }

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "pool".equals(subprotocol);}

    protected Connection proxyConnection (final Connection conn, final String url, final Properties info, Driver driver) throws SQLException {
        final String poolKey = getPoolKey(url, info);
        final PoolConfig config = configs.get(poolKey);
        final BlockingQueue<Connection> pool = pools.get(poolKey);

	return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, new InvocationHandler() {
		public Object invoke (Object proxy, Method method, Object[] args) throws SQLException {
		    if ("close".equals(method.getName())) {
                        // If max is set and pool is full, don't return to pool
                        if (config != null && config.max > 0 && pool.size() >= config.max) {
                            try { conn.close(); } catch (SQLException e) {}
                        } else {
                            pool.add(conn);
                        }
                        return proxy;
                    }
		    if (!"close".equals(method.getName())) if (pool.contains(conn)) throw new SQLException();
		    if ("toString".equals(method.getName())) return conn.toString();
		    if ("equals".equals(method.getName())) return proxy==args[0];
		    try {return method.invoke(conn, args);} catch (Exception e) {throw new SQLException(e);}}});}

    public Connection connect (String url, Properties info) throws SQLException {
	if (!acceptsURL(url)) return null;
        String poolKey = getPoolKey(url, info);
        String targetUrl = subname(url);

        // Parse and cache configuration
        configs.putIfAbsent(poolKey, parseConfig(url));
        PoolConfig config = configs.get(poolKey);

        // Create pool if not exists
        pools.putIfAbsent(poolKey, new LinkedBlockingQueue<Connection>());
        BlockingQueue<Connection> pool = pools.get(poolKey);

        // Try to get connection from pool - only wait if pool has connections
        Connection conn = null;
	if (!pool.isEmpty()) {
	    try {conn = pool.poll(config.timeout, TimeUnit.MILLISECONDS);} catch (InterruptedException e) {}
	    if (conn!=null) return proxyConnection(conn, url, info, this);
	}

        // Create new connection
	return proxyConnection(DriverManager.getConnection(targetUrl, info), url, info, this);}}
