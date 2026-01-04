package org.pjdbc.drivers;

import java.io.Serializable;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverDependency;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractResultSet;
import org.pjdbc.sql.JdbcUrlParser;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.map.IMap;

/**
 * HazelcastCachingDriver caches SELECT query results in Hazelcast for distributed caching.
 *
 * URL format: jdbc:hazelcast[param=value,...]:jdbc:target:...
 *
 * Parameters:
 *   mode             - "embedded" or "client" (default: embedded)
 *   clusterName      - Hazelcast cluster name (default: pjdbc-cache)
 *   members          - Semicolon-separated list of host:port for client mode (default: localhost:5701)
 *   mapName          - Name of the IMap to use for caching (default: pjdbc-query-cache)
 *   ttl              - Time-to-live in seconds for cache entries (default: 60)
 *   maxIdle          - Max idle time in seconds before eviction (default: 0, disabled)
 *   invalidateOnWrite - Clear cache on INSERT/UPDATE/DELETE (default: true)
 *   enabled          - Enable caching (default: true)
 *
 * Features:
 *   - Distributed caching across multiple application instances
 *   - Embedded mode for simple deployments or client mode for dedicated clusters
 *   - TTL and max-idle support via Hazelcast IMap
 *   - Near-cache support for read-heavy workloads
 *   - Cache statistics (hits, misses)
 *   - Thread-safe implementation
 *
 * Example URLs:
 *   jdbc:hazelcast:jdbc:postgresql://localhost/mydb
 *   jdbc:hazelcast[mode=client,members=hz1:5701;hz2:5701]:jdbc:postgresql://localhost/mydb
 *   jdbc:hazelcast[ttl=300,mapName=myapp-cache]:jdbc:mysql://localhost/db
 */
@DriverCapability(
    prefix = "hazelcast",
    description = "Caches SELECT query results in Hazelcast",
    capabilities = {"caching"}
)
@DriverParameter(name = "mode", type = ParameterType.STRING,
    description = "Hazelcast mode", defaultValue = "embedded",
    enumValues = {"embedded", "client"})
@DriverParameter(name = "clusterName", type = ParameterType.STRING,
    description = "Hazelcast cluster name", defaultValue = "pjdbc-cache")
@DriverParameter(name = "members", type = ParameterType.STRING,
    description = "Semicolon-separated member addresses", defaultValue = "127.0.0.1:5701")
@DriverParameter(name = "mapName", type = ParameterType.STRING,
    description = "IMap name for caching", defaultValue = "pjdbc_query_cache")
@DriverParameter(name = "ttl", type = ParameterType.INTEGER,
    description = "Time-to-live in seconds", defaultValue = "60", min = 1)
@DriverParameter(name = "maxIdle", type = ParameterType.INTEGER,
    description = "Maximum idle time in seconds (0 to disable)", defaultValue = "0", min = 0)
@DriverParameter(name = "invalidateOnWrite", type = ParameterType.BOOLEAN,
    description = "Clear cache on writes", defaultValue = "true")
@DriverParameter(name = "enabled", type = ParameterType.BOOLEAN,
    description = "Enable caching", defaultValue = "true")
@DriverDependency(groupId = "com.hazelcast", artifactId = "hazelcast", version = "5.3.6", optional = true)
@DriverSideEffects(network = true, stateful = true)
public class HazelcastCachingDriver extends AbstractProxyDriver {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE|DROP|CREATE|ALTER)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Shared Hazelcast instances to avoid creating multiple per connection
    private static final Map<String, HazelcastInstance> INSTANCES = new ConcurrentHashMap<>();

    static {
        try {
            DriverManager.registerDriver(new HazelcastCachingDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "hazelcast".equals(subprotocol);
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
        return new HazelcastCachingConnection(delegate, this, url, info);
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        HazelcastCachingConnection cacheConn = (HazelcastCachingConnection) conn;
        return new HazelcastCachingStatement(delegate, conn, cacheConn.getCache(), this);
    }

    @Override
    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {
        HazelcastCachingConnection cacheConn = (HazelcastCachingConnection) conn;
        return new HazelcastCachingPreparedStatement(delegate, conn, cacheConn.getCache(), cacheConn.getCurrentSql(), this);
    }

    @Override
    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {
        HazelcastCachingConnection cacheConn = (HazelcastCachingConnection) conn;
        return new HazelcastCachingCallableStatement(delegate, conn, cacheConn.getCache(), this);
    }

    /**
     * Configuration for Hazelcast cache.
     */
    public static class HazelcastCacheConfig {
        private static final String KEY_PREFIX = "q:";
        /** Default max bytes: 10 MB */
        private static final long DEFAULT_MAX_BYTES = 10 * 1024 * 1024;

        private final String mode;
        private final String clusterName;
        private final String members;
        private final String mapName;
        private final int ttlSeconds;
        private final int maxIdleSeconds;
        private final int maxCacheRows;
        private final long maxCacheBytes;
        private final boolean includeContext;
        private final boolean invalidateOnWrite;
        private final boolean enabled;

        public HazelcastCacheConfig(String url) {
            JdbcUrlParser parser = JdbcUrlParser.parse(url);
            this.mode = parser.getParameter("mode", "embedded");
            this.clusterName = parser.getParameter("clusterName", "pjdbc-cache");
            this.members = parser.getParameter("members", "localhost:5701");
            this.mapName = parser.getParameter("mapName", "pjdbc-query-cache");
            this.ttlSeconds = parseInt(parser.getParameter("ttl", "60"));
            this.maxIdleSeconds = parseInt(parser.getParameter("maxIdle", "0"));
            this.maxCacheRows = parseIntDefault(parser.getParameter("maxCacheRows", "10000"), 10000);
            this.maxCacheBytes = parseLong(parser.getParameter("maxCacheBytes", String.valueOf(DEFAULT_MAX_BYTES)));
            // Default to true for distributed caches to prevent cross-user data leakage
            this.includeContext = parseBoolean(parser.getParameter("includeContext", "true"));
            this.invalidateOnWrite = parseBoolean(parser.getParameter("invalidateOnWrite", "true"));
            this.enabled = parseBoolean(parser.getParameter("enabled", "true"));
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 60; }
        }

        private static int parseIntDefault(String s, int defaultVal) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }

        private static long parseLong(String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return DEFAULT_MAX_BYTES; }
        }

        private static boolean parseBoolean(String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }

        public String getMode() { return mode; }
        public String getClusterName() { return clusterName; }
        public String getMembers() { return members; }
        public String getMapName() { return mapName; }
        public int getTtlSeconds() { return ttlSeconds; }
        public int getMaxIdleSeconds() { return maxIdleSeconds; }
        /** Maximum rows to cache per query. 0 = unlimited. Default: 10000 */
        public int getMaxCacheRows() { return maxCacheRows; }
        /** Maximum estimated bytes to cache per query. 0 = unlimited. Default: 10MB */
        public long getMaxCacheBytes() { return maxCacheBytes; }
        /** Include catalog/schema/user in cache key. Default: true for distributed cache */
        public boolean isIncludeContext() { return includeContext; }
        public boolean isInvalidateOnWrite() { return invalidateOnWrite; }
        public boolean isEnabled() { return enabled; }
        public String getKeyPrefix() { return KEY_PREFIX; }

        public boolean isEmbedded() {
            return "embedded".equalsIgnoreCase(mode);
        }

        public String getInstanceKey() {
            if (isEmbedded()) {
                return "embedded:" + clusterName;
            } else {
                return "client:" + clusterName + ":" + members;
            }
        }
    }

    /**
     * Serializable cached result set data for Hazelcast storage.
     */
    public static class SerializableCachedResultSet implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String[] columnNames;
        private final int[] columnTypes;
        private final List<Object[]> rows;
        private final boolean tooLargeToCache;

        public SerializableCachedResultSet(ResultSet rs) throws SQLException {
            this(rs, 0, 0);
        }

        public SerializableCachedResultSet(ResultSet rs, int maxRows) throws SQLException {
            this(rs, maxRows, 0);
        }

        public SerializableCachedResultSet(ResultSet rs, int maxRows, long maxBytes) throws SQLException {
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
                    // Ensure value is serializable
                    if (val != null && !(val instanceof Serializable)) {
                        val = val.toString();
                    }
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
     * Hazelcast-backed query cache.
     */
    public static class HazelcastQueryCache {
        private final HazelcastCacheConfig config;
        private final HazelcastInstance hazelcast;
        private final IMap<String, SerializableCachedResultSet> cache;
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final boolean ownedInstance;

        public HazelcastQueryCache(HazelcastCacheConfig config) {
            this.config = config;

            String instanceKey = config.getInstanceKey();

            // Try to reuse existing instance
            HazelcastInstance existing = INSTANCES.get(instanceKey);
            if (existing != null && existing.getLifecycleService().isRunning()) {
                this.hazelcast = existing;
                this.ownedInstance = false;
            } else {
                // Create new instance
                if (config.isEmbedded()) {
                    Config hzConfig = new Config();
                    hzConfig.setClusterName(config.getClusterName());
                    // Disable multicast for simpler local testing
                    hzConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
                    hzConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);

                    // Configure map with TTL
                    MapConfig mapConfig = new MapConfig(config.getMapName());
                    mapConfig.setTimeToLiveSeconds(config.getTtlSeconds());
                    if (config.getMaxIdleSeconds() > 0) {
                        mapConfig.setMaxIdleSeconds(config.getMaxIdleSeconds());
                    }
                    hzConfig.addMapConfig(mapConfig);

                    this.hazelcast = Hazelcast.newHazelcastInstance(hzConfig);
                } else {
                    ClientConfig clientConfig = new ClientConfig();
                    clientConfig.setClusterName(config.getClusterName());
                    for (String member : config.getMembers().split(";")) {
                        clientConfig.getNetworkConfig().addAddress(member.trim());
                    }
                    this.hazelcast = HazelcastClient.newHazelcastClient(clientConfig);
                }
                INSTANCES.put(instanceKey, this.hazelcast);
                this.ownedInstance = true;
            }

            this.cache = hazelcast.getMap(config.getMapName());
        }

        public HazelcastCacheConfig getConfig() { return config; }

        private String makeKey(String sql, CacheKeyBuilder.ConnectionContext context) {
            return CacheKeyBuilder.buildKey(config.getKeyPrefix(), sql, context);
        }

        public SerializableCachedResultSet get(String sql, CacheKeyBuilder.ConnectionContext context) {
            if (!config.isEnabled()) return null;

            String key = makeKey(sql, context);
            try {
                SerializableCachedResultSet result = cache.get(key);
                if (result == null) {
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                return result;
            } catch (Exception e) {
                misses.incrementAndGet();
                return null;
            }
        }

        public void put(String sql, CacheKeyBuilder.ConnectionContext context, SerializableCachedResultSet result) {
            if (!config.isEnabled()) return;

            String key = makeKey(sql, context);
            try {
                if (config.getTtlSeconds() > 0) {
                    cache.put(key, result, config.getTtlSeconds(), TimeUnit.SECONDS);
                } else {
                    cache.put(key, result);
                }
            } catch (Exception e) {
                // Silently ignore cache write failures
            }
        }

        /**
         * Get cached data using a pre-built cache key (for PreparedStatements).
         */
        public SerializableCachedResultSet getByKey(String cacheKey) {
            if (!config.isEnabled()) return null;

            try {
                SerializableCachedResultSet result = cache.get(cacheKey);
                if (result == null) {
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                return result;
            } catch (Exception e) {
                misses.incrementAndGet();
                return null;
            }
        }

        /**
         * Put cached data using a pre-built cache key (for PreparedStatements).
         */
        public void putByKey(String cacheKey, SerializableCachedResultSet result) {
            if (!config.isEnabled()) return;

            try {
                if (config.getTtlSeconds() > 0) {
                    cache.put(cacheKey, result, config.getTtlSeconds(), TimeUnit.SECONDS);
                } else {
                    cache.put(cacheKey, result);
                }
            } catch (Exception e) {
                // Silently ignore cache write failures
            }
        }

        public void clear() {
            try {
                cache.clear();
            } catch (Exception e) {
                // Silently ignore cache clear failures
            }
        }

        public int size() {
            try {
                return cache.size();
            } catch (Exception e) {
                return 0;
            }
        }

        public void shutdown() {
            // Don't shutdown shared instances
            // They will be cleaned up when the JVM exits
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

        /**
         * Get the underlying Hazelcast instance.
         */
        public HazelcastInstance getHazelcastInstance() {
            return hazelcast;
        }

        /**
         * Get the underlying IMap.
         */
        public IMap<String, SerializableCachedResultSet> getMap() {
            return cache;
        }
    }

    /**
     * ResultSet implementation that reads from cached data.
     */
    public static class CachedResultSetWrapper extends AbstractResultSet {
        private final SerializableCachedResultSet cached;
        private int currentRow = -1;
        private boolean wasNull = false;

        public CachedResultSetWrapper(Statement stmt, SerializableCachedResultSet cached) throws SQLException {
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
     * Get the Hazelcast query cache from a connection.
     * Returns null if the connection is not a HazelcastCachingConnection.
     */
    public static HazelcastQueryCache getCache(Connection conn) {
        if (conn instanceof HazelcastCachingConnection hcc) {
            return hcc.getCache();
        }
        return null;
    }

    /**
     * Shutdown all Hazelcast instances created by this driver.
     * Call this during application shutdown for clean resource release.
     */
    public static void shutdownAll() {
        for (HazelcastInstance instance : INSTANCES.values()) {
            try {
                instance.shutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
        INSTANCES.clear();
    }

    /**
     * Connection wrapper that holds the Hazelcast cache.
     */
    private class HazelcastCachingConnection extends AbstractConnection {
        private final HazelcastQueryCache cache;
        private String currentSql;

        public HazelcastCachingConnection(Connection conn, Driver driver, String url, Properties info) throws SQLException {
            super(conn, driver, url, info);
            this.cache = new HazelcastQueryCache(new HazelcastCacheConfig(url));
        }

        public HazelcastQueryCache getCache() { return cache; }

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
     * Statement wrapper with Hazelcast caching support.
     */
    private class HazelcastCachingStatement extends AbstractStatement {
        private final HazelcastQueryCache cache;
        private final HazelcastCachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;

        public HazelcastCachingStatement(Statement delegate, Connection conn, HazelcastQueryCache cache, HazelcastCachingDriver driver) throws SQLException {
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
            SerializableCachedResultSet cached = cache.get(sql, context);
            if (cached != null) {
                return new CachedResultSetWrapper(this, cached);
            }

            // Execute and cache (respecting maxCacheRows and maxCacheBytes limits)
            ResultSet rs = super.executeQuery(sql);
            SerializableCachedResultSet cachedResult = new SerializableCachedResultSet(rs,
                cache.getConfig().getMaxCacheRows(),
                cache.getConfig().getMaxCacheBytes());
            rs.close();
            if (!cachedResult.isTooLargeToCache()) {
                cache.put(sql, context, cachedResult);
            }
            return new CachedResultSetWrapper(this, cachedResult);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            // Execute first, then invalidate on success
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
     * PreparedStatement wrapper with Hazelcast caching support.
     */
    private class HazelcastCachingPreparedStatement extends AbstractPreparedStatement {
        private final HazelcastQueryCache cache;
        private final String sql;
        private final HazelcastCachingDriver driver;
        private final CacheKeyBuilder.ConnectionContext context;
        private final Map<Integer, Object> parameters = new ConcurrentHashMap<>();

        public HazelcastCachingPreparedStatement(PreparedStatement delegate, Connection conn, HazelcastQueryCache cache, String sql, HazelcastCachingDriver driver) throws SQLException {
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
                SerializableCachedResultSet cached = cache.getByKey(cacheKey);
                if (cached != null) {
                    return new CachedResultSetWrapper(this, cached);
                }

                ResultSet rs = super.executeQuery();
                SerializableCachedResultSet cachedResult = new SerializableCachedResultSet(rs,
                    cache.getConfig().getMaxCacheRows(),
                    cache.getConfig().getMaxCacheBytes());
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
    private class HazelcastCachingCallableStatement extends AbstractCallableStatement {
        private final HazelcastQueryCache cache;
        private final HazelcastCachingDriver driver;

        public HazelcastCachingCallableStatement(CallableStatement delegate, Connection conn, HazelcastQueryCache cache, HazelcastCachingDriver driver) throws SQLException {
            super(delegate, conn);
            this.cache = cache;
            this.driver = driver;
        }

        // Callable statements may have side effects, so invalidate cache on any execution
        @Override
        public ResultSet executeQuery() throws SQLException {
            // Execute first, then invalidate on success
            ResultSet result = super.executeQuery();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }

        @Override
        public int executeUpdate() throws SQLException {
            // Execute first, then invalidate on success
            int result = super.executeUpdate();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }

        @Override
        public boolean execute() throws SQLException {
            // Execute first, then invalidate on success
            boolean result = super.execute();
            if (cache.getConfig().isInvalidateOnWrite()) {
                cache.clear();
            }
            return result;
        }
    }
}
