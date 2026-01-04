package org.pjdbc.drivers;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.*;

/**
 * LoadBalancingDriver distributes read load across replicas while broadcasting writes to all.
 *
 * <p>Unlike TeeDriver (sends all ops to all) or FederatingDriver (merges query results),
 * LoadBalancingDriver selects ONE connection for reads and broadcasts writes.
 *
 * <p>URL format: {@code jdbc:loadbalance[readStrategy=round_robin,failoverEnabled=true]:url1;url2;...}
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:loadbalance:jdbc:postgresql://primary:5432/db;jdbc:postgresql://replica1:5432/db
 * jdbc:loadbalance[readStrategy=random]:jdbc:h2:mem:db1;jdbc:h2:mem:db2
 * </pre>
 */
@DriverCapability(
    prefix = "loadbalance",
    description = "Distributes read load across replicas while broadcasting writes to all",
    capabilities = {"load-balancing", "resilience"}
)
@DriverParameter(name = "readStrategy", type = ParameterType.STRING,
    description = "Strategy for selecting read connection", defaultValue = "round_robin",
    enumValues = {"round_robin", "random", "least_connections", "primary_only", "replica_only"})
@DriverParameter(name = "healthCheckInterval", type = ParameterType.INTEGER,
    description = "Milliseconds between health checks", defaultValue = "30000", min = 0)
@DriverParameter(name = "failoverEnabled", type = ParameterType.BOOLEAN,
    description = "Automatically failover to healthy connections", defaultValue = "true")
@DriverSideEffects(stateful = true)
public class LoadBalancingDriver extends AbstractDriver {
    static {try {DriverManager.registerDriver(new LoadBalancingDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    /**
     * Strategy for selecting which connection to use for reads.
     */
    public enum ReadStrategy {
        /** Cycle through connections in order */
        ROUND_ROBIN,
        /** Random selection */
        RANDOM,
        /** Track active queries per connection, pick least loaded */
        LEAST_CONNECTIONS,
        /** Always use first connection (primary) for reads */
        PRIMARY_ONLY,
        /** Never read from primary (index 0), only replicas */
        REPLICA_ONLY
    }

    /**
     * Configuration for the load balancing driver.
     */
    public static class LoadBalanceConfig {
        private final ReadStrategy readStrategy;
        private final long healthCheckInterval;
        private final boolean failoverEnabled;

        public LoadBalanceConfig(ReadStrategy readStrategy, long healthCheckInterval, boolean failoverEnabled) {
            this.readStrategy = readStrategy;
            this.healthCheckInterval = healthCheckInterval;
            this.failoverEnabled = failoverEnabled;
        }

        public ReadStrategy getReadStrategy() { return readStrategy; }
        public long getHealthCheckInterval() { return healthCheckInterval; }
        public boolean isFailoverEnabled() { return failoverEnabled; }

        public static LoadBalanceConfig fromUrl(String url) {
            ReadStrategy strategy = ReadStrategy.ROUND_ROBIN;
            long healthCheckInterval = 30000;
            boolean failoverEnabled = true;

            try {
                JdbcUrlParser parser = JdbcUrlParser.parse(url);
                String strategyParam = parser.getParameter("readStrategy", "round_robin");
                switch (strategyParam.toLowerCase()) {
                    case "random": strategy = ReadStrategy.RANDOM; break;
                    case "least_connections": strategy = ReadStrategy.LEAST_CONNECTIONS; break;
                    case "primary_only": strategy = ReadStrategy.PRIMARY_ONLY; break;
                    case "replica_only": strategy = ReadStrategy.REPLICA_ONLY; break;
                    default: strategy = ReadStrategy.ROUND_ROBIN;
                }
                String intervalParam = parser.getParameter("healthCheckInterval", "30000");
                healthCheckInterval = Long.parseLong(intervalParam);
                failoverEnabled = !"false".equalsIgnoreCase(parser.getParameter("failoverEnabled", "true"));
            } catch (Exception e) {
                // Use defaults on parse error
            }

            return new LoadBalanceConfig(strategy, healthCheckInterval, failoverEnabled);
        }
    }

    protected boolean acceptsSubProtocol(String subprotocol) {
        return "loadbalance".equals(subprotocol);
    }

    protected boolean acceptsSubName(String subname) {
        // Must have at least one semicolon (2+ URLs)
        String[] urls = splitUrls(subname);
        if (urls.length < 2) return false;
        try {
            for (String url : urls) {
                if (DriverManager.getDriver(url.trim()) == null) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private String[] splitUrls(String subname) {
        List<String> urls = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] parts = subname.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (current.length() == 0) {
                current.append(part);
            } else if (part.startsWith("jdbc:")) {
                urls.add(current.toString());
                current = new StringBuilder(part);
            } else {
                current.append(";").append(part);
            }
        }
        if (current.length() > 0) {
            urls.add(current.toString());
        }
        return urls.toArray(new String[0]);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;

        LoadBalanceConfig config = LoadBalanceConfig.fromUrl(url);
        String subname = subname(url);
        String[] urls = splitUrls(subname);

        List<Connection> delegates = new ArrayList<>();
        for (String s : urls) {
            delegates.add(DriverManager.getConnection(s.trim(), info));
        }

        return new LoadBalancingConnection(delegates, this, url, info, config);
    }

    /**
     * Tracks health status of a connection.
     */
    static class ConnectionHealth {
        private final Connection connection;
        private volatile boolean healthy = true;
        private volatile long lastCheckTime = System.currentTimeMillis();
        private final AtomicInteger activeQueries = new AtomicInteger(0);

        public ConnectionHealth(Connection connection) {
            this.connection = connection;
        }

        public Connection getConnection() { return connection; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public long getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(long time) { this.lastCheckTime = time; }
        public int getActiveQueries() { return activeQueries.get(); }
        public void incrementQueries() { activeQueries.incrementAndGet(); }
        public void decrementQueries() { activeQueries.decrementAndGet(); }

        public boolean checkHealth(long healthCheckInterval) {
            long now = System.currentTimeMillis();
            if (now - lastCheckTime < healthCheckInterval) {
                return healthy;
            }
            lastCheckTime = now;
            try {
                healthy = connection.isValid(5); // 5 second timeout
            } catch (SQLException e) {
                healthy = false;
            }
            return healthy;
        }
    }

    /**
     * Connection that load balances reads across multiple databases.
     */
    private static class LoadBalancingConnection extends AbstractConnection {
        private final LoadBalanceConfig config;
        private final List<ConnectionHealth> healthTrackers;
        private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
        private final Random random = new Random();

        public LoadBalancingConnection(List<Connection> delegates, LoadBalancingDriver driver,
                                       String url, Properties info, LoadBalanceConfig config) throws SQLException {
            super(delegates.toArray(new Connection[0]), driver, url, info);
            this.config = config;
            this.healthTrackers = new ArrayList<>();
            for (Connection c : delegates) {
                healthTrackers.add(new ConnectionHealth(c));
            }
        }

        LoadBalanceConfig getConfig() { return config; }
        List<ConnectionHealth> getHealthTrackers() { return healthTrackers; }

        /**
         * Select a connection for reading based on the configured strategy.
         */
        Connection selectForRead() throws SQLException {
            List<ConnectionHealth> candidates = getReadCandidates();
            if (candidates.isEmpty()) {
                throw new SQLException("No healthy connections available for read");
            }

            ConnectionHealth selected;
            switch (config.getReadStrategy()) {
                case RANDOM:
                    selected = candidates.get(random.nextInt(candidates.size()));
                    break;

                case LEAST_CONNECTIONS:
                    selected = candidates.stream()
                        .min(Comparator.comparingInt(ConnectionHealth::getActiveQueries))
                        .orElse(candidates.get(0));
                    break;

                case PRIMARY_ONLY:
                    // Primary is index 0, but check if healthy
                    if (healthTrackers.get(0).isHealthy()) {
                        selected = healthTrackers.get(0);
                    } else if (config.isFailoverEnabled() && !candidates.isEmpty()) {
                        selected = candidates.get(0);
                    } else {
                        throw new SQLException("Primary connection is not healthy");
                    }
                    break;

                case REPLICA_ONLY:
                    // Filter out primary (index 0)
                    List<ConnectionHealth> replicas = new ArrayList<>();
                    for (int i = 1; i < healthTrackers.size(); i++) {
                        ConnectionHealth h = healthTrackers.get(i);
                        if (h.isHealthy() || !config.isFailoverEnabled()) {
                            replicas.add(h);
                        }
                    }
                    if (replicas.isEmpty()) {
                        throw new SQLException("No healthy replica connections available");
                    }
                    int idx = roundRobinIndex.getAndUpdate(i -> (i + 1) % replicas.size());
                    selected = replicas.get(idx % replicas.size());
                    break;

                case ROUND_ROBIN:
                default:
                    int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % candidates.size());
                    selected = candidates.get(index % candidates.size());
                    break;
            }

            return selected.getConnection();
        }

        private List<ConnectionHealth> getReadCandidates() {
            List<ConnectionHealth> candidates = new ArrayList<>();
            for (ConnectionHealth h : healthTrackers) {
                if (config.isFailoverEnabled()) {
                    h.checkHealth(config.getHealthCheckInterval());
                    if (h.isHealthy()) {
                        candidates.add(h);
                    }
                } else {
                    candidates.add(h);
                }
            }
            return candidates;
        }

        ConnectionHealth getHealthTracker(Connection c) {
            for (ConnectionHealth h : healthTrackers) {
                if (h.getConnection() == c) {
                    return h;
                }
            }
            return null;
        }

        @Override
        public Statement createStatement() throws SQLException {
            // Create statement on all connections for write capability
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement());
            }
            return new LoadBalancingStatement(stmts, this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement(resultSetType, resultSetConcurrency));
            }
            return new LoadBalancingStatement(stmts, this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
            }
            return new LoadBalancingStatement(stmts, this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, autoGeneratedKeys));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, columnIndexes));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, resultSetType, resultSetConcurrency));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, columnNames));
            }
            return new LoadBalancingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this);
        }
    }

    /**
     * Statement that load balances reads and broadcasts writes.
     */
    private static class LoadBalancingStatement extends AbstractStatement {
        private final List<Statement> delegates;
        private final LoadBalancingConnection lbConnection;

        public LoadBalancingStatement(List<Statement> delegates, LoadBalancingConnection conn) throws SQLException {
            super(delegates.get(0), conn);
            this.delegates = delegates;
            this.lbConnection = conn;
        }

        private Statement getStatementFor(Connection c) {
            List<ConnectionHealth> trackers = lbConnection.getHealthTrackers();
            for (int i = 0; i < trackers.size(); i++) {
                if (trackers.get(i).getConnection() == c) {
                    return delegates.get(i);
                }
            }
            return delegates.get(0);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            // Select ONE connection for reads
            Connection readConn = lbConnection.selectForRead();
            ConnectionHealth health = lbConnection.getHealthTracker(readConn);
            if (health != null) health.incrementQueries();
            try {
                return getStatementFor(readConn).executeQuery(sql);
            } finally {
                if (health != null) health.decrementQueries();
            }
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            // Broadcast to ALL connections
            int total = 0;
            for (Statement s : delegates) {
                total += s.executeUpdate(sql);
            }
            return total;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            // Broadcast to all, return first result
            Boolean first = null;
            for (Statement s : delegates) {
                boolean r = s.execute(sql);
                if (first == null) first = r;
            }
            return first != null && first;
        }

        @Override
        public void close() throws SQLException {
            for (Statement s : delegates) {
                s.close();
            }
        }
    }

    /**
     * PreparedStatement that load balances reads and broadcasts writes.
     */
    private static class LoadBalancingPreparedStatement extends AbstractPreparedStatement {
        private final LoadBalancingConnection lbConnection;

        public LoadBalancingPreparedStatement(PreparedStatement[] delegates, LoadBalancingConnection conn) throws SQLException {
            super(delegates, conn);
            this.lbConnection = conn;
        }

        private PreparedStatement getStatementFor(Connection c) {
            List<PreparedStatement> pstmts = getPreparedStatements();
            List<ConnectionHealth> trackers = lbConnection.getHealthTrackers();
            for (int i = 0; i < trackers.size(); i++) {
                if (trackers.get(i).getConnection() == c) {
                    return pstmts.get(i);
                }
            }
            return pstmts.get(0);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            // Select ONE connection for reads
            Connection readConn = lbConnection.selectForRead();
            ConnectionHealth health = lbConnection.getHealthTracker(readConn);
            if (health != null) health.incrementQueries();
            try {
                return getStatementFor(readConn).executeQuery();
            } finally {
                if (health != null) health.decrementQueries();
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            // Broadcast to ALL connections
            int total = 0;
            for (PreparedStatement s : getPreparedStatements()) {
                total += s.executeUpdate();
            }
            return total;
        }

        @Override
        public boolean execute() throws SQLException {
            // Broadcast to all, return first result
            Boolean first = null;
            for (PreparedStatement s : getPreparedStatements()) {
                boolean r = s.execute();
                if (first == null) first = r;
            }
            return first != null && first;
        }
    }
}
