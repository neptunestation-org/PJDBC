package org.pjdbc.drivers;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * RedisCachingDriver caches SELECT query results in Redis for distributed caching.
 *
 * URL format: jdbc:rediscache[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   host             - Redis server hostname (default: localhost)
 *   port             - Redis server port (default: 6379)
 *   password         - Redis password (default: none)
 *   database         - Redis database number (default: 0)
 *   keyPrefix        - Prefix for cache keys (default: "pjdbc:")
 *   ttl              - Time-to-live in seconds for cache entries (default: 60)
 *   maxPoolSize      - Maximum connections in pool (default: 8)
 *   invalidateOnWrite - Clear cache on INSERT/UPDATE/DELETE (default: true)
 *   enabled          - Enable caching (default: true)
 *
 * Features:
 *   - Distributed caching across multiple application instances
 *   - TTL support via Redis SETEX
 *   - Connection pooling with JedisPool
 *   - Key prefix for namespace isolation
 *   - Cache statistics (hits, misses)
 *   - Thread-safe implementation
 *
 * Example URLs:
 *   jdbc:rediscache:jdbc:postgresql://localhost/mydb
 *   jdbc:rediscache[host=redis.example.com,port=6379]:jdbc:postgresql://localhost/mydb
 *   jdbc:rediscache[password=secret,ttl=300]:jdbc:mysql://localhost/db
 *   jdbc:rediscache[keyPrefix=myapp:,database=1]:jdbc:postgresql://localhost/mydb
 */
public class RedisCachingDriver extends AbstractProxyDriver {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE|DROP|CREATE|ALTER)\\b",
        Pattern.CASE_INSENSITIVE
    );

    static {
        try {
            DriverManager.registerDriver(new RedisCachingDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "rediscache".equals(subprotocol);
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
        return new RedisCachingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        RedisCachingConnection cacheConn = (RedisCachingConnection) conn;
        return new RedisCachingStatement(delegate, conn, cacheConn.getCache(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        RedisCachingConnection cacheConn = (RedisCachingConnection) conn;
        return new RedisCachingPreparedStatement(delegate, conn, cacheConn.getCache(), cacheConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        RedisCachingConnection cacheConn = (RedisCachingConnection) conn;
        return new RedisCachingCallableStatement(delegate, conn, cacheConn.getCache(), this);
    }

    /**
     * Configuration for Redis cache.
     */
    public static class RedisCacheConfig {
        private final String host;
        private final int port;
        private final String password;
        private final int database;
        private final String keyPrefix;
        private final int ttlSeconds;
        private final int maxPoolSize;
        private final boolean invalidateOnWrite;
        private final boolean enabled;

        public RedisCacheConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.host = parser.getParameter("host", "localhost");
            this.port = parseInt(parser.getParameter("port", "6379"));
            this.password = parser.getParameter("password", null);
            this.database = parseInt(parser.getParameter("database", "0"));
            this.keyPrefix = parser.getParameter("keyPrefix", "pjdbc:");
            this.ttlSeconds = parseInt(parser.getParameter("ttl", "60"));
            this.maxPoolSize = parseInt(parser.getParameter("maxPoolSize", "8"));
            this.invalidateOnWrite = parseBoolean(parser.getParameter("invalidateOnWrite", "true"));
            this.enabled = parseBoolean(parser.getParameter("enabled", "true"));
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getPassword() { return password; }
        public int getDatabase() { return database; }
        public String getKeyPrefix() { return keyPrefix; }
        public int getTtlSeconds() { return ttlSeconds; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public boolean isInvalidateOnWrite() { return invalidateOnWrite; }
        public boolean isEnabled() { return enabled; }
    }


    /**
     * Redis-backed query cache.
     */
    public static class RedisQueryCache {
        private final RedisCacheConfig config;
        private final JedisPool pool;
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);

        public RedisQueryCache(RedisCacheConfig config) {
            this.config = config;

            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(config.getMaxPoolSize());
            poolConfig.setMaxIdle(config.getMaxPoolSize());
            poolConfig.setMinIdle(1);

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                this.pool = new JedisPool(poolConfig, config.getHost(), config.getPort(),
                    2000, config.getPassword(), config.getDatabase());
            } else {
                this.pool = new JedisPool(poolConfig, config.getHost(), config.getPort(),
                    2000, null, config.getDatabase());
            }
        }

        public RedisCacheConfig getConfig() { return config; }

        private String makeKey(String sql) {
            return CacheKeyBuilder.buildKey(config.getKeyPrefix(), sql);
        }

        public SafeResultSetSerializer.CachedData get(String sql) {
            if (!config.isEnabled()) return null;

            String key = makeKey(sql);
            try (Jedis jedis = pool.getResource()) {
                byte[] data = jedis.get(key.getBytes());
                if (data == null) {
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                return SafeResultSetSerializer.deserialize(data);
            } catch (Exception e) {
                misses.incrementAndGet();
                return null;
            }
        }

        public void put(String sql, SafeResultSetSerializer.CachedData result) {
            if (!config.isEnabled()) return;

            String key = makeKey(sql);
            try (Jedis jedis = pool.getResource()) {
                byte[] data = SafeResultSetSerializer.serialize(result);
                jedis.setex(key.getBytes(), config.getTtlSeconds(), data);
            } catch (Exception e) {
                // Silently ignore cache write failures
            }
        }

        public void clear() {
            try (Jedis jedis = pool.getResource()) {
                // Delete all keys with our prefix using SCAN to avoid blocking
                String pattern = config.getKeyPrefix() + "*";
                String cursor = "0";
                do {
                    redis.clients.jedis.resps.ScanResult<String> scanResult =
                        jedis.scan(cursor, new redis.clients.jedis.params.ScanParams().match(pattern).count(100));
                    cursor = scanResult.getCursor();
                    List<String> keys = scanResult.getResult();
                    if (!keys.isEmpty()) {
                        jedis.del(keys.toArray(new String[0]));
                    }
                } while (!cursor.equals("0"));
            } catch (Exception e) {
                // Silently ignore cache clear failures
            }
        }

        public void close() {
            if (pool != null && !pool.isClosed()) {
                pool.close();
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
     * Get the Redis query cache from a connection.
     * Returns null if the connection is not a RedisCachingConnection.
     */
    public static RedisQueryCache getCache(Connection conn) {
        if (conn instanceof RedisCachingConnection rcc) {
            return rcc.getCache();
        }
        return null;
    }

    /**
     * Connection wrapper that holds the Redis cache.
     */
    private class RedisCachingConnection extends AbstractConnection {
        private final RedisQueryCache cache;
        private String currentSql;

        public RedisCachingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.cache = new RedisQueryCache(new RedisCacheConfig(url));
        }

        public RedisQueryCache getCache() { return cache; }

        public String getCurrentSql() { return currentSql; }
        public void setCurrentSql(String sql) { this.currentSql = sql; }

        @Override
        public void close() throws SQLException {
            try {
                cache.close();
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
     * Statement wrapper with Redis caching support.
     */
    private class RedisCachingStatement extends AbstractStatement {
        private final RedisQueryCache cache;
        private final RedisCachingDriver driver;

        public RedisCachingStatement(Statement delegate, Connection conn, RedisQueryCache cache, RedisCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
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

            // Check cache
            SafeResultSetSerializer.CachedData cached = cache.get(sql);
            if (cached != null) {
                return new CachedResultSetWrapper(this, cached);
            }

            // Execute and cache
            ResultSet rs = super.executeQuery(sql);
            SafeResultSetSerializer.CachedData cachedResult = SafeResultSetSerializer.fromResultSet(rs);
            rs.close();
            cache.put(sql, cachedResult);
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
     * PreparedStatement wrapper with Redis caching support.
     */
    private class RedisCachingPreparedStatement extends AbstractPreparedStatement {
        private final RedisQueryCache cache;
        private final String sql;
        private final RedisCachingDriver driver;
        private final Map<Integer, Object> parameters = new ConcurrentHashMap<>();

        public RedisCachingPreparedStatement(PreparedStatement delegate, Connection conn, RedisQueryCache cache, String sql, RedisCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.sql = sql;
            this.driver = driver;
        }

        private boolean isSelect() {
            return sql != null && SELECT_PATTERN.matcher(sql).find();
        }

        private boolean isWrite() {
            return sql != null && WRITE_PATTERN.matcher(sql).find();
        }

        private String getCacheKey() {
            StringBuilder sb = new StringBuilder(sql);
            if (!parameters.isEmpty()) {
                sb.append("::params::");
                for (Map.Entry<Integer, Object> e : parameters.entrySet()) {
                    sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
                }
            }
            return sb.toString();
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
            if (!isSelect()) {
                return super.executeQuery();
            }

            String cacheKey = getCacheKey();
            SafeResultSetSerializer.CachedData cached = cache.get(cacheKey);
            if (cached != null) {
                return new CachedResultSetWrapper(this, cached);
            }

            ResultSet rs = super.executeQuery();
            SafeResultSetSerializer.CachedData cachedResult = SafeResultSetSerializer.fromResultSet(rs);
            rs.close();
            cache.put(cacheKey, cachedResult);
            return new CachedResultSetWrapper(this, cachedResult);
        }

        @Override
        public int executeUpdate() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                cache.clear();
            }
            return super.executeUpdate();
        }

        @Override
        public boolean execute() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                cache.clear();
            }
            return super.execute();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return super.executeBatch();
        }
    }

    /**
     * CallableStatement wrapper - no caching for stored procedures.
     */
    private class RedisCachingCallableStatement extends AbstractCallableStatement {
        private final RedisQueryCache cache;
        private final RedisCachingDriver driver;

        public RedisCachingCallableStatement(CallableStatement delegate, Connection conn, RedisQueryCache cache, RedisCachingDriver driver) throws SQLException {
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
