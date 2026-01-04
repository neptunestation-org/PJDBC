package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * CachingDriver caches SELECT query results to reduce database load.
 *
 * URL format: jdbc:cache[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   ttl              - Time-to-live in seconds for cache entries (default: 60)
 *   maxSize          - Maximum number of cached queries (default: 1000)
 *   invalidateOnWrite - Clear cache on INSERT/UPDATE/DELETE (default: true)
 *   enabled          - Enable caching (default: true)
 *
 * Features:
 *   - Caches SELECT query results in memory
 *   - LRU eviction when maxSize is exceeded
 *   - Automatic invalidation on write operations
 *   - Cache statistics (hits, misses, evictions)
 *   - Thread-safe implementation
 *
 * Example URLs:
 *   jdbc:cache:jdbc:postgresql://localhost/mydb
 *   jdbc:cache[ttl=300,maxSize=5000]:jdbc:postgresql://localhost/mydb
 *   jdbc:cache[invalidateOnWrite=false]:jdbc:mysql://localhost/db
 */
public class CachingDriver extends AbstractProxyDriver {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE|DROP|CREATE|ALTER)\\b",
        Pattern.CASE_INSENSITIVE
    );

    static {
        try {
            DriverManager.registerDriver(new CachingDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "cache".equals(subprotocol);
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
        return new CachingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        CachingConnection cacheConn = (CachingConnection) conn;
        return new CachingStatement(delegate, conn, cacheConn.getCache(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        CachingConnection cacheConn = (CachingConnection) conn;
        return new CachingPreparedStatement(delegate, conn, cacheConn.getCache(), cacheConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        CachingConnection cacheConn = (CachingConnection) conn;
        return new CachingCallableStatement(delegate, conn, cacheConn.getCache(), this);
    }

    /**
     * Configuration for the cache.
     */
    public static class CacheConfig {
        /** Default max bytes: 10 MB */
        private static final long DEFAULT_MAX_BYTES = 10 * 1024 * 1024;

        private final long ttlMs;
        private final int maxSize;
        private final int maxCacheRows;
        private final long maxCacheBytes;
        private final boolean includeContext;
        private final boolean invalidateOnWrite;
        private final boolean enabled;

        public CacheConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.ttlMs = parseLong(parser.getParameter("ttl", "60")) * 1000;
            this.maxSize = parseInt(parser.getParameter("maxSize", "1000"));
            this.maxCacheRows = parseInt(parser.getParameter("maxCacheRows", "10000"));
            this.maxCacheBytes = parseLong(parser.getParameter("maxCacheBytes", String.valueOf(DEFAULT_MAX_BYTES)));
            this.includeContext = parseBoolean(parser.getParameter("includeContext", "false"));
            this.invalidateOnWrite = parseBoolean(parser.getParameter("invalidateOnWrite", "true"));
            this.enabled = parseBoolean(parser.getParameter("enabled", "true"));
        }

        private static long parseLong(String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 60; }
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1000; }
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public long getTtlMs() { return ttlMs; }
        public int getMaxSize() { return maxSize; }
        /** Maximum rows to cache per query. 0 = unlimited. Default: 10000 */
        public int getMaxCacheRows() { return maxCacheRows; }
        /** Maximum estimated bytes to cache per query. 0 = unlimited. Default: 10MB */
        public long getMaxCacheBytes() { return maxCacheBytes; }
        /** Include catalog/schema/user in cache key. Default: false for in-memory cache */
        public boolean isIncludeContext() { return includeContext; }
        public boolean isInvalidateOnWrite() { return invalidateOnWrite; }
        public boolean isEnabled() { return enabled; }
    }

    /**
     * Thread-safe query cache with LRU eviction and TTL support.
     */
    public static class QueryCache {
        private final CacheConfig config;
        private final Map<String, CacheEntry> cache;
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong evictions = new AtomicLong(0);

        public QueryCache(CacheConfig config) {
            this.config = config;
            // LinkedHashMap with access-order for LRU eviction
            this.cache = new LinkedHashMap<String, CacheEntry>(config.getMaxSize(), 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    if (size() > config.getMaxSize()) {
                        evictions.incrementAndGet();
                        return true;
                    }
                    return false;
                }
            };
        }

        public CacheConfig getConfig() { return config; }

        public synchronized CachedResultSet get(String key) {
            if (!config.isEnabled()) return null;

            CacheEntry entry = cache.get(key);
            if (entry == null) {
                misses.incrementAndGet();
                return null;
            }

            if (entry.isExpired()) {
                cache.remove(key);
                misses.incrementAndGet();
                return null;
            }

            hits.incrementAndGet();
            return entry.getResult();
        }

        public synchronized void put(String key, CachedResultSet result) {
            if (!config.isEnabled()) return;
            cache.put(key, new CacheEntry(result, config.getTtlMs()));
        }

        public synchronized void clear() {
            cache.clear();
        }

        public synchronized int size() {
            return cache.size();
        }

        public long getHits() { return hits.get(); }
        public long getMisses() { return misses.get(); }
        public long getEvictions() { return evictions.get(); }

        public double getHitRatio() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0.0 : (double) hits.get() / total;
        }

        public void resetStats() {
            hits.set(0);
            misses.set(0);
            evictions.set(0);
        }
    }

    /**
     * Cache entry with expiration time.
     */
    private static class CacheEntry {
        private final CachedResultSet result;
        private final long expiresAt;

        public CacheEntry(CachedResultSet result, long ttlMs) {
            this.result = result;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        public CachedResultSet getResult() { return result; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * In-memory cached result set data.
     */
    public static class CachedResultSet {
        private final String[] columnNames;
        private final int[] columnTypes;
        private final List<Object[]> rows;
        private final boolean tooLargeToCache;

        public CachedResultSet(ResultSet rs) throws SQLException {
            this(rs, 0, 0);
        }

        public CachedResultSet(ResultSet rs, int maxRows) throws SQLException {
            this(rs, maxRows, 0);
        }

        public CachedResultSet(ResultSet rs, int maxRows, long maxBytes) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            this.columnNames = new String[columnCount];
            this.columnTypes = new int[columnCount];
            for (int i = 0; i < columnCount; i++) {
                columnNames[i] = meta.getColumnLabel(i + 1);
                columnTypes[i] = meta.getColumnType(i + 1);
            }

            this.rows = new ArrayList<>();
            boolean exceeded = false;
            long estimatedBytes = 0;
            while (rs.next()) {
                if (maxRows > 0 && rows.size() >= maxRows) {
                    exceeded = true;
                    break;
                }
                Object[] row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    Object val = rs.getObject(i + 1);
                    row[i] = val;
                    estimatedBytes += SafeResultSetSerializer.estimateSize(val);
                }
                // Check byte limit after reading the row
                if (maxBytes > 0 && estimatedBytes >= maxBytes) {
                    exceeded = true;
                    break;
                }
                rows.add(row);
            }
            this.tooLargeToCache = exceeded;
        }

        public String[] getColumnNames() { return columnNames; }
        public int[] getColumnTypes() { return columnTypes; }
        public List<Object[]> getRows() { return rows; }
        public int getRowCount() { return rows.size(); }
        /** Returns true if the result set exceeded maxRows/maxBytes and should not be cached */
        public boolean isTooLargeToCache() { return tooLargeToCache; }
    }

    /**
     * ResultSet implementation that reads from cached data.
     */
    public static class CachedResultSetWrapper extends AbstractResultSet {
        private final CachedResultSet cached;
        private int currentRow = -1;
        private boolean wasNull = false;

        public CachedResultSetWrapper(Statement stmt, CachedResultSet cached) throws SQLException {
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
     * Get the query cache from a caching connection.
     * Returns null if the connection is not a caching connection.
     */
    public static QueryCache getCache(Connection conn) {
        if (conn instanceof CachingConnection cc) {
            return cc.getCache();
        }
        return null;
    }

    /**
     * Connection wrapper that holds the cache.
     */
    private class CachingConnection extends AbstractConnection {
        private final QueryCache cache;
        private String currentSql;

        public CachingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.cache = new QueryCache(new CacheConfig(url));
        }

        public QueryCache getCache() { return cache; }

        public String getCurrentSql() { return currentSql; }
        public void setCurrentSql(String sql) { this.currentSql = sql; }

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
     * Statement wrapper with caching support.
     */
    private class CachingStatement extends AbstractStatement {
        private static final String CACHE_KEY_PREFIX = "pjdbc:cache:";
        private final QueryCache cache;
        private final CachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;

        public CachingStatement(Statement delegate, Connection conn, QueryCache cache, CachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
            this.context = cache.getConfig().isIncludeContext()
                ? CacheKeyBuilder.ConnectionContext.fromConnection(conn)
                : null;
        }

        private String buildCacheKey(String sql) {
            return CacheKeyBuilder.buildKey(CACHE_KEY_PREFIX, sql, context);
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

            // Check cache using secure key with optional connection context
            String cacheKey = buildCacheKey(sql);
            CachedResultSet cached = cache.get(cacheKey);
            if (cached != null) {
                return new CachedResultSetWrapper(this, cached);
            }

            // Execute and cache (respecting maxCacheRows and maxCacheBytes limits)
            ResultSet rs = super.executeQuery(sql);
            CachedResultSet cachedResult = new CachedResultSet(rs,
                cache.getConfig().getMaxCacheRows(),
                cache.getConfig().getMaxCacheBytes());
            rs.close();
            // Only cache if result set didn't exceed the limits
            if (!cachedResult.isTooLargeToCache()) {
                cache.put(cacheKey, cachedResult);
            }
            return new CachedResultSetWrapper(this, cachedResult);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            // Execute first, then invalidate on success (not before, to avoid clearing cache on failed writes)
            int result = super.executeUpdate(sql);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            int result = super.executeUpdate(sql, autoGeneratedKeys);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            int result = super.executeUpdate(sql, columnIndexes);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            int result = super.executeUpdate(sql, columnNames);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            boolean result = super.execute(sql);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            boolean result = super.execute(sql, autoGeneratedKeys);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            boolean result = super.execute(sql, columnIndexes);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            boolean result = super.execute(sql, columnNames);
            invalidateIfWrite(sql);
            return result;
        }

        @Override
        public int[] executeBatch() throws SQLException {
            // Execute first, then invalidate on success
            int[] result = super.executeBatch();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }
    }

    /**
     * PreparedStatement wrapper with caching support.
     */
    private class CachingPreparedStatement extends AbstractPreparedStatement {
        private static final String CACHE_KEY_PREFIX = "pjdbc:cache:";
        private final QueryCache cache;
        private final String sql;
        private final CachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;
        private final Map<Integer, Object> parameters = new ConcurrentHashMap<>();

        public CachingPreparedStatement(PreparedStatement delegate, Connection conn, QueryCache cache, String sql, CachingDriver driver) throws SQLException {
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
            // Take atomic snapshot of parameters for thread-safe key generation
            // ConcurrentHashMap's toArray provides a consistent snapshot
            Map.Entry<Integer, Object>[] snapshot = parameters.entrySet().toArray(new Map.Entry[0]);

            Object[] params = null;
            if (snapshot.length > 0) {
                // Find max index from snapshot
                int maxIndex = 0;
                for (Map.Entry<Integer, Object> entry : snapshot) {
                    if (entry.getKey() > maxIndex) {
                        maxIndex = entry.getKey();
                    }
                }
                // Build ordered parameter array
                params = new Object[maxIndex];
                for (Map.Entry<Integer, Object> entry : snapshot) {
                    params[entry.getKey() - 1] = entry.getValue();
                }
            }
            return CacheKeyBuilder.buildKeyWithContext(CACHE_KEY_PREFIX, sql, context, params);
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
                CachedResultSet cached = cache.get(cacheKey);
                if (cached != null) {
                    return new CachedResultSetWrapper(this, cached);
                }

                ResultSet rs = super.executeQuery();
                CachedResultSet cachedResult = new CachedResultSet(rs,
                    cache.getConfig().getMaxCacheRows(),
                    cache.getConfig().getMaxCacheBytes());
                rs.close();
                if (!cachedResult.isTooLargeToCache()) {
                    cache.put(cacheKey, cachedResult);
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
                // Execute first, then invalidate on success
                int result = super.executeUpdate();
                if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                    cache.clear();
                }
                return result;
            } finally {
                parameters.clear();
            }
        }

        @Override
        public boolean execute() throws SQLException {
            try {
                // Execute first, then invalidate on success
                boolean result = super.execute();
                if (cache.getConfig().isInvalidateOnWrite() && isWrite()) {
                    cache.clear();
                }
                return result;
            } finally {
                parameters.clear();
            }
        }

        @Override
        public int[] executeBatch() throws SQLException {
            try {
                // Execute first, then invalidate on success
                int[] result = super.executeBatch();
                if (cache.getConfig().isInvalidateOnWrite()) {
                    cache.clear();
                }
                return result;
            } finally {
                parameters.clear();
            }
        }
    }

    /**
     * CallableStatement wrapper - no caching for stored procedures.
     */
    private class CachingCallableStatement extends AbstractCallableStatement {
        private final QueryCache cache;
        private final CachingDriver driver;

        public CachingCallableStatement(CallableStatement delegate, Connection conn, QueryCache cache, CachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
        }

        // Callable statements may have side effects, so invalidate cache on any execution
        // Execute first, then invalidate on success
        @Override
        public ResultSet executeQuery() throws SQLException {
            ResultSet result = super.executeQuery();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }

        @Override
        public int executeUpdate() throws SQLException {
            int result = super.executeUpdate();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }

        @Override
        public boolean execute() throws SQLException {
            boolean result = super.execute();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }
    }
}
