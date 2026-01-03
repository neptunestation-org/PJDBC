package org.pjdbc.drivers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractResultSet;
import org.pjdbc.sql.JdbcUrlParser;

import net.spy.memcached.MemcachedClient;

/**
 * MemcachedCachingDriver caches SELECT query results in Memcached for distributed caching.
 *
 * URL format: jdbc:memcache[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   servers          - Semicolon-separated list of host:port (default: localhost:11211)
 *   keyPrefix        - Prefix for cache keys (default: "pjdbc:")
 *   ttl              - Time-to-live in seconds for cache entries (default: 60)
 *   invalidateOnWrite - Clear cache on INSERT/UPDATE/DELETE (default: true)
 *   enabled          - Enable caching (default: true)
 *
 * Features:
 *   - Distributed caching across multiple application instances
 *   - TTL support via Memcached expiration
 *   - Support for multiple Memcached servers (consistent hashing)
 *   - Key prefix for namespace isolation
 *   - Cache statistics (hits, misses)
 *   - Thread-safe implementation
 *
 * Example URLs:
 *   jdbc:memcache:jdbc:postgresql://localhost/mydb
 *   jdbc:memcache[servers=cache1:11211;cache2:11211]:jdbc:postgresql://localhost/mydb
 *   jdbc:memcache[ttl=300,keyPrefix=myapp:]:jdbc:mysql://localhost/db
 */
public class MemcachedCachingDriver extends AbstractProxyDriver {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE|DROP|CREATE|ALTER)\\b",
        Pattern.CASE_INSENSITIVE
    );

    static {
        try {
            DriverManager.registerDriver(new MemcachedCachingDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "memcache".equals(subprotocol);
    }

    @Override
    protected boolean acceptsSubName(String subname) {
        return true;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        Connection delegate = DriverManager.getConnection(subname(url), info);
        return proxyConnection(delegate, url, info, this);
    }

    @Override
    protected Connection proxyConnection(Connection delegate, String url, Properties info, Driver driver) throws SQLException {
        return new MemcachedCachingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        MemcachedCachingConnection cacheConn = (MemcachedCachingConnection) conn;
        return new MemcachedCachingStatement(delegate, conn, cacheConn.getCache(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        MemcachedCachingConnection cacheConn = (MemcachedCachingConnection) conn;
        return new MemcachedCachingPreparedStatement(delegate, conn, cacheConn.getCache(), cacheConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        MemcachedCachingConnection cacheConn = (MemcachedCachingConnection) conn;
        return new MemcachedCachingCallableStatement(delegate, conn, cacheConn.getCache(), this);
    }

    /**
     * Configuration for Memcached cache.
     */
    public static class MemcachedCacheConfig {
        private final String servers;
        private final String keyPrefix;
        private final int ttlSeconds;
        private final int maxCacheRows;
        private final boolean includeContext;
        private final boolean invalidateOnWrite;
        private final boolean enabled;

        public MemcachedCacheConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.servers = parser.getParameter("servers", "localhost:11211");
            this.keyPrefix = parser.getParameter("keyPrefix", "pjdbc:");
            this.ttlSeconds = parseInt(parser.getParameter("ttl", "60"));
            this.maxCacheRows = parseInt(parser.getParameter("maxCacheRows", "10000"));
            // Default to true for distributed caches to prevent cross-user data leakage
            this.includeContext = parseBoolean(parser.getParameter("includeContext", "true"));
            this.invalidateOnWrite = parseBoolean(parser.getParameter("invalidateOnWrite", "true"));
            this.enabled = parseBoolean(parser.getParameter("enabled", "true"));
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 60; }
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public String getServers() { return servers; }
        public String getKeyPrefix() { return keyPrefix; }
        public int getTtlSeconds() { return ttlSeconds; }
        /** Maximum rows to cache per query. 0 = unlimited. Default: 10000 */
        public int getMaxCacheRows() { return maxCacheRows; }
        /** Include catalog/schema/user in cache key. Default: true for distributed cache */
        public boolean isIncludeContext() { return includeContext; }
        public boolean isInvalidateOnWrite() { return invalidateOnWrite; }
        public boolean isEnabled() { return enabled; }

        public List<InetSocketAddress> getServerAddresses() {
            List<InetSocketAddress> addresses = new ArrayList<>();
            for (String server : servers.split(";")) {
                String[] parts = server.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 11211;
                addresses.add(new InetSocketAddress(host, port));
            }
            return addresses;
        }
    }


    /**
     * Memcached-backed query cache.
     */
    public static class MemcachedQueryCache {
        private final MemcachedCacheConfig config;
        private final MemcachedClient client;
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        // Track keys we've stored for invalidation
        private final List<String> trackedKeys = new ArrayList<>();

        public MemcachedQueryCache(MemcachedCacheConfig config) throws IOException {
            this.config = config;
            this.client = new MemcachedClient(config.getServerAddresses());
        }

        public MemcachedCacheConfig getConfig() { return config; }

        private String makeKey(String sql, CacheKeyBuilder.ConnectionContext context) {
            // Memcached keys can't have spaces or control chars, max 250 bytes
            // SHA-256 produces 64 hex chars, well under limit with typical prefixes
            return CacheKeyBuilder.buildKey(config.getKeyPrefix(), sql, context);
        }

        public SafeResultSetSerializer.CachedData get(String sql, CacheKeyBuilder.ConnectionContext context) {
            if (!config.isEnabled()) return null;

            String key = makeKey(sql, context);
            try {
                Object data = client.get(key);
                if (data == null) {
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                if (data instanceof byte[] bytes) {
                    return SafeResultSetSerializer.deserialize(bytes);
                }
                return null;
            } catch (Exception e) {
                misses.incrementAndGet();
                return null;
            }
        }

        public void put(String sql, CacheKeyBuilder.ConnectionContext context, SafeResultSetSerializer.CachedData result) {
            if (!config.isEnabled()) return;

            String key = makeKey(sql, context);
            try {
                byte[] data = SafeResultSetSerializer.serialize(result);
                client.set(key, config.getTtlSeconds(), data);
                synchronized (trackedKeys) {
                    trackedKeys.add(key);
                }
            } catch (Exception e) {
                // Silently ignore cache write failures
            }
        }

        /**
         * Get cached data using a pre-built cache key (for PreparedStatements).
         */
        public SafeResultSetSerializer.CachedData getByKey(String cacheKey) {
            if (!config.isEnabled()) return null;

            try {
                Object data = client.get(cacheKey);
                if (data == null) {
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                if (data instanceof byte[] bytes) {
                    return SafeResultSetSerializer.deserialize(bytes);
                }
                return null;
            } catch (Exception e) {
                misses.incrementAndGet();
                return null;
            }
        }

        /**
         * Put cached data using a pre-built cache key (for PreparedStatements).
         */
        public void putByKey(String cacheKey, SafeResultSetSerializer.CachedData result) {
            if (!config.isEnabled()) return;

            try {
                byte[] data = SafeResultSetSerializer.serialize(result);
                client.set(cacheKey, config.getTtlSeconds(), data);
                synchronized (trackedKeys) {
                    trackedKeys.add(cacheKey);
                }
            } catch (Exception e) {
                // Silently ignore cache write failures
            }
        }

        public void clear() {
            // Memcached doesn't support prefix-based deletion easily
            // Delete tracked keys
            synchronized (trackedKeys) {
                for (String key : trackedKeys) {
                    try {
                        client.delete(key);
                    } catch (Exception e) {
                        // Ignore deletion errors
                    }
                }
                trackedKeys.clear();
            }
        }

        public void shutdown() {
            if (client != null) {
                client.shutdown();
            }
        }

        public long getHits() { return hits.get(); }
        public long getMisses() { return misses.get(); }

        public double getHitRatio() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0.0 : (double) hits.get() / total;
        }

        public void resetStats() {
            hits.set(0);
            misses.set(0);
        }
    }

    /**
     * ResultSet implementation that reads from cached data.
     */
    public static class CachedResultSetWrapper extends AbstractResultSet {
        private final SafeResultSetSerializer.CachedData cached;
        private int currentRow = -1;
        private boolean wasNull = false;

        public CachedResultSetWrapper(Statement stmt, SafeResultSetSerializer.CachedData cached) throws SQLException {
            super(stmt, null);
            this.cached = cached;
        }

        @Override
        public boolean next() throws SQLException {
            currentRow++;
            return currentRow < cached.getRowCount();
        }

        @Override
        public void close() throws SQLException {
            // No underlying ResultSet to close
        }

        @Override
        public boolean wasNull() throws SQLException {
            return wasNull;
        }

        @Override
        public String getString(int columnIndex) throws SQLException {
            Object val = getObject(columnIndex);
            return val == null ? null : val.toString();
        }

        @Override
        public String getString(String columnLabel) throws SQLException {
            return getString(findColumn(columnLabel));
        }

        @Override
        public int getInt(int columnIndex) throws SQLException {
            Object val = getObject(columnIndex);
            if (val == null) return 0;
            if (val instanceof Number n) return n.intValue();
            return Integer.parseInt(val.toString());
        }

        @Override
        public int getInt(String columnLabel) throws SQLException {
            return getInt(findColumn(columnLabel));
        }

        @Override
        public long getLong(int columnIndex) throws SQLException {
            Object val = getObject(columnIndex);
            if (val == null) return 0;
            if (val instanceof Number n) return n.longValue();
            return Long.parseLong(val.toString());
        }

        @Override
        public long getLong(String columnLabel) throws SQLException {
            return getLong(findColumn(columnLabel));
        }

        @Override
        public double getDouble(int columnIndex) throws SQLException {
            Object val = getObject(columnIndex);
            if (val == null) return 0.0;
            if (val instanceof Number n) return n.doubleValue();
            return Double.parseDouble(val.toString());
        }

        @Override
        public double getDouble(String columnLabel) throws SQLException {
            return getDouble(findColumn(columnLabel));
        }

        @Override
        public boolean getBoolean(int columnIndex) throws SQLException {
            Object val = getObject(columnIndex);
            if (val == null) return false;
            if (val instanceof Boolean b) return b;
            return Boolean.parseBoolean(val.toString());
        }

        @Override
        public boolean getBoolean(String columnLabel) throws SQLException {
            return getBoolean(findColumn(columnLabel));
        }

        @Override
        public Object getObject(int columnIndex) throws SQLException {
            if (currentRow < 0 || currentRow >= cached.getRowCount()) {
                throw new SQLException("Invalid cursor position");
            }
            Object[] row = cached.getRows().get(currentRow);
            if (columnIndex < 1 || columnIndex > row.length) {
                throw new SQLException("Invalid column index: " + columnIndex);
            }
            Object val = row[columnIndex - 1];
            wasNull = (val == null);
            return val;
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException {
            return getObject(findColumn(columnLabel));
        }

        @Override
        public int findColumn(String columnLabel) throws SQLException {
            String[] names = cached.getColumnNames();
            for (int i = 0; i < names.length; i++) {
                if (names[i].equalsIgnoreCase(columnLabel)) {
                    return i + 1;
                }
            }
            throw new SQLException("Column not found: " + columnLabel);
        }

        @Override
        public boolean isBeforeFirst() throws SQLException {
            return currentRow < 0;
        }

        @Override
        public boolean isAfterLast() throws SQLException {
            return currentRow >= cached.getRowCount();
        }

        @Override
        public boolean isFirst() throws SQLException {
            return currentRow == 0 && cached.getRowCount() > 0;
        }

        @Override
        public boolean isLast() throws SQLException {
            return currentRow == cached.getRowCount() - 1 && cached.getRowCount() > 0;
        }

        @Override
        public void beforeFirst() throws SQLException {
            currentRow = -1;
        }

        @Override
        public void afterLast() throws SQLException {
            currentRow = cached.getRowCount();
        }

        @Override
        public boolean first() throws SQLException {
            if (cached.getRowCount() == 0) return false;
            currentRow = 0;
            return true;
        }

        @Override
        public boolean last() throws SQLException {
            if (cached.getRowCount() == 0) return false;
            currentRow = cached.getRowCount() - 1;
            return true;
        }

        @Override
        public int getRow() throws SQLException {
            return currentRow + 1;
        }

        @Override
        public boolean absolute(int row) throws SQLException {
            if (row > 0) {
                currentRow = row - 1;
            } else if (row < 0) {
                currentRow = cached.getRowCount() + row;
            } else {
                currentRow = -1;
            }
            return currentRow >= 0 && currentRow < cached.getRowCount();
        }

        @Override
        public boolean relative(int rows) throws SQLException {
            currentRow += rows;
            return currentRow >= 0 && currentRow < cached.getRowCount();
        }

        @Override
        public boolean previous() throws SQLException {
            currentRow--;
            return currentRow >= 0;
        }
    }

    /**
     * Get the Memcached query cache from a connection.
     * Returns null if the connection is not a MemcachedCachingConnection.
     */
    public static MemcachedQueryCache getCache(Connection conn) {
        if (conn instanceof MemcachedCachingConnection mcc) {
            return mcc.getCache();
        }
        return null;
    }

    /**
     * Connection wrapper that holds the Memcached cache.
     */
    private class MemcachedCachingConnection extends AbstractConnection {
        private final MemcachedQueryCache cache;
        private String currentSql;

        public MemcachedCachingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            try {
                this.cache = new MemcachedQueryCache(new MemcachedCacheConfig(url));
            } catch (IOException e) {
                throw new SQLException("Failed to connect to Memcached: " + e.getMessage(), e);
            }
        }

        public MemcachedQueryCache getCache() { return cache; }

        public String getCurrentSql() { return currentSql; }
        public void setCurrentSql(String sql) { this.currentSql = sql; }

        @Override
        public void close() throws SQLException {
            try {
                cache.shutdown();
            } finally {
                super.close();
            }
        }

        @Override
        public Statement createStatement() throws SQLException {
            return proxyStatement(getDelegate().createStatement(), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, autoGeneratedKeys), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnIndexes), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            this.currentSql = sql;
            return proxyPreparedStatement(getDelegate().prepareStatement(sql, columnNames), this);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency), this);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            this.currentSql = sql;
            return proxyCallableStatement(getDelegate().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }
    }

    /**
     * Statement wrapper with Memcached caching support.
     */
    private class MemcachedCachingStatement extends AbstractStatement {
        private final MemcachedQueryCache cache;
        private final MemcachedCachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;

        public MemcachedCachingStatement(Statement delegate, Connection conn, MemcachedQueryCache cache, MemcachedCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
            this.context = cache.getConfig().isIncludeContext()
                ? CacheKeyBuilder.ConnectionContext.fromConnection(conn)
                : null;
        }

        private boolean isSelect(String sql) {
            return sql != null && SELECT_PATTERN.matcher(sql).find();
        }

        private boolean isWrite(String sql) {
            return sql != null && WRITE_PATTERN.matcher(sql).find();
        }

        private void invalidateIfWrite(String sql) {
            if (cache.getConfig().isInvalidateOnWrite() && isWrite(sql)) {
                cache.clear();
            }
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            if (!isSelect(sql)) {
                return super.executeQuery(sql);
            }

            // Check cache with connection context for isolation
            SafeResultSetSerializer.CachedData cached = cache.get(sql, context);
            if (cached != null) {
                return new CachedResultSetWrapper(this, cached);
            }

            // Execute and cache (respecting maxCacheRows limit)
            ResultSet rs = super.executeQuery(sql);
            SafeResultSetSerializer.CachedData cachedResult = SafeResultSetSerializer.fromResultSet(rs, cache.getConfig().getMaxCacheRows());
            rs.close();
            if (!cachedResult.isTooLargeToCache()) {
                cache.put(sql, context, cachedResult);
            }
            return new CachedResultSetWrapper(this, cachedResult);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            invalidateIfWrite(sql);
            return super.executeUpdate(sql);
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            invalidateIfWrite(sql);
            return super.executeUpdate(sql, autoGeneratedKeys);
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            invalidateIfWrite(sql);
            return super.executeUpdate(sql, columnIndexes);
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            invalidateIfWrite(sql);
            return super.executeUpdate(sql, columnNames);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            invalidateIfWrite(sql);
            return super.execute(sql);
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            invalidateIfWrite(sql);
            return super.execute(sql, autoGeneratedKeys);
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            invalidateIfWrite(sql);
            return super.execute(sql, columnIndexes);
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            invalidateIfWrite(sql);
            return super.execute(sql, columnNames);
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // Batch operations likely contain writes
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return super.executeBatch();
        }
    }

    /**
     * PreparedStatement wrapper with Memcached caching support.
     */
    private class MemcachedCachingPreparedStatement extends AbstractPreparedStatement {
        private final MemcachedQueryCache cache;
        private final String sql;
        private final MemcachedCachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;
        private final Map<Integer, Object> parameters = new ConcurrentHashMap<>();

        public MemcachedCachingPreparedStatement(PreparedStatement delegate, Connection conn, MemcachedQueryCache cache, String sql, MemcachedCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.sql = sql;
            this.driver = driver;
            this.context = cache.getConfig().isIncludeContext()
                ? CacheKeyBuilder.ConnectionContext.fromConnection(conn)
                : null;
        }

        private boolean isSelect() {
            return sql != null && SELECT_PATTERN.matcher(sql).find();
        }

        private boolean isWrite() {
            return sql != null && WRITE_PATTERN.matcher(sql).find();
        }

        private String getCacheKey() {
            // Build ordered parameter array for consistent hashing
            Object[] params = null;
            if (!parameters.isEmpty()) {
                int maxIndex = parameters.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                params = new Object[maxIndex];
                for (int i = 1; i <= maxIndex; i++) {
                    params[i - 1] = parameters.get(i);
                }
            }
            return CacheKeyBuilder.buildKeyWithContext(cache.getConfig().getKeyPrefix(), sql, context, params);
        }

        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setObject(parameterIndex, x);
        }

        @Override
        public void setString(int parameterIndex, String x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setString(parameterIndex, x);
        }

        @Override
        public void setInt(int parameterIndex, int x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setInt(parameterIndex, x);
        }

        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setLong(parameterIndex, x);
        }

        @Override
        public void setDouble(int parameterIndex, double x) throws SQLException {
            parameters.put(parameterIndex, x);
            super.setDouble(parameterIndex, x);
        }

        @Override
        public void clearParameters() throws SQLException {
            parameters.clear();
            super.clearParameters();
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            try {
                if (!isSelect()) {
                    return super.executeQuery();
                }

                String cacheKey = getCacheKey();
                SafeResultSetSerializer.CachedData cached = cache.getByKey(cacheKey);
                if (cached != null) {
                    return new CachedResultSetWrapper(this, cached);
                }

                ResultSet rs = super.executeQuery();
                SafeResultSetSerializer.CachedData cachedResult = SafeResultSetSerializer.fromResultSet(rs, cache.getConfig().getMaxCacheRows());
                rs.close();
                if (!cachedResult.isTooLargeToCache()) {
                    cache.putByKey(cacheKey, cachedResult);
                }
                return new CachedResultSetWrapper(this, cachedResult);
            } finally {
                // Clear parameters to prevent stale values leaking into subsequent executions
                parameters.clear();
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            try {
                if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                    cache.clear();
                }
                return super.executeUpdate();
            } finally {
                parameters.clear();
            }
        }

        @Override
        public boolean execute() throws SQLException {
            try {
                if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                    cache.clear();
                }
                return super.execute();
            } finally {
                parameters.clear();
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            try {
                if (cache.getConfig().isInvalidateOnWrite()) {
                    cache.clear();
                }
                return super.executeBatch();
            } finally {
                parameters.clear();
            }
        }
    }

    /**
     * CallableStatement wrapper - no caching for stored procedures.
     */
    private class MemcachedCachingCallableStatement extends AbstractCallableStatement {
        private final MemcachedQueryCache cache;
        private final MemcachedCachingDriver driver;

        public MemcachedCachingCallableStatement(CallableStatement delegate, Connection conn, MemcachedQueryCache cache, MemcachedCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
        }

        // Callable statements may have side effects, so invalidate cache on any execution
        @Override
        public ResultSet executeQuery() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return super.executeUpdate();
        }

        @Override
        public boolean execute() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return super.execute();
        }
    }
}
