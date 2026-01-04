package org.pjdbc.drivers;

import java.io.*;
import java.math.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.*;

/**
 * FederatingDriver queries multiple databases and merges results.
 *
 * <p>Unlike TeeDriver (write replication) or LoadBalancingDriver (read scaling),
 * FederatingDriver merges query results from all data sources into a unified view.
 *
 * <p>URL format: {@code jdbc:federate[mergeStrategy=concat,parallelExecution=false]:url1;url2;...}
 *
 * <p>Example URLs:
 * <pre>
 * jdbc:federate:jdbc:h2:mem:db1;jdbc:h2:mem:db2
 * jdbc:federate[mergeStrategy=first_non_empty]:jdbc:postgresql://db1/sales;jdbc:mysql://db2/inventory
 * </pre>
 */
@DriverCapability(
    prefix = "federate",
    description = "Queries multiple databases and merges results",
    capabilities = {"federation", "multi-database"}
)
@DriverParameter(name = "mergeStrategy", type = ParameterType.STRING,
    description = "Result merge strategy", defaultValue = "concat",
    enumValues = {"concat", "union_all", "first_non_empty"})
@DriverParameter(name = "parallelExecution", type = ParameterType.BOOLEAN,
    description = "Execute queries in parallel", defaultValue = "false")
@DriverParameter(name = "timeout", type = ParameterType.INTEGER,
    description = "Timeout in milliseconds for parallel execution", defaultValue = "30000", min = 0)
@DriverSideEffects(stateful = true)
public class FederatingDriver extends AbstractDriver {
    static {try {DriverManager.registerDriver(new FederatingDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    /**
     * Strategy for merging ResultSets from multiple sources.
     */
    public enum MergeStrategy {
        /** Sequential iteration through all ResultSets (default) */
        CONCAT,
        /** Same as CONCAT - combine all rows from all sources */
        UNION_ALL,
        /** Return first ResultSet that has rows */
        FIRST_NON_EMPTY
    }

    /**
     * Configuration for the federating driver.
     */
    public static class FederateConfig {
        private final MergeStrategy mergeStrategy;
        private final boolean parallelExecution;
        private final long timeout;

        public FederateConfig(MergeStrategy mergeStrategy, boolean parallelExecution, long timeout) {
            this.mergeStrategy = mergeStrategy;
            this.parallelExecution = parallelExecution;
            this.timeout = timeout;
        }

        public MergeStrategy getMergeStrategy() { return mergeStrategy; }
        public boolean isParallelExecution() { return parallelExecution; }
        public long getTimeout() { return timeout; }

        public static FederateConfig fromUrl(String url) {
            MergeStrategy strategy = MergeStrategy.CONCAT;
            boolean parallel = false;
            long timeout = 30000;

            try {
                JdbcUrlParser parser = JdbcUrlParser.parse(url);
                String strategyParam = parser.getParameter("mergeStrategy", "concat");
                switch (strategyParam.toLowerCase()) {
                    case "union_all": strategy = MergeStrategy.UNION_ALL; break;
                    case "first_non_empty": strategy = MergeStrategy.FIRST_NON_EMPTY; break;
                    default: strategy = MergeStrategy.CONCAT;
                }
                parallel = "true".equalsIgnoreCase(parser.getParameter("parallelExecution", "false"));
                String timeoutParam = parser.getParameter("timeout", "30000");
                timeout = Long.parseLong(timeoutParam);
            } catch (Exception e) {
                // Use defaults on parse error
            }

            return new FederateConfig(strategy, parallel, timeout);
        }
    }

    protected boolean acceptsSubProtocol(String subprotocol) {
        return "federate".equals(subprotocol);
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
        // Split on semicolon, being careful about embedded semicolons in URLs
        // For simplicity, split on ; that's followed by jdbc:
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

        FederateConfig config = FederateConfig.fromUrl(url);
        String subname = subname(url);
        String[] urls = splitUrls(subname);

        List<Connection> delegates = new ArrayList<>();
        for (String s : urls) {
            delegates.add(DriverManager.getConnection(s.trim(), info));
        }

        return new FederatingConnection(delegates, this, url, info, config);
    }

    /**
     * Connection that federates queries across multiple databases.
     */
    private static class FederatingConnection extends AbstractConnection {
        private final FederateConfig config;
        private final FederatingDriver driver;

        public FederatingConnection(List<Connection> delegates, FederatingDriver driver,
                                    String url, Properties info, FederateConfig config) throws SQLException {
            super(delegates.toArray(new Connection[0]), driver, url, info);
            this.config = config;
            this.driver = driver;
        }

        @Override
        public Statement createStatement() throws SQLException {
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement());
            }
            return new FederatingStatement(stmts, this, config);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement(resultSetType, resultSetConcurrency));
            }
            return new FederatingStatement(stmts, this, config);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            List<Statement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
            }
            return new FederatingStatement(stmts, this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, autoGeneratedKeys));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, columnIndexes));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, resultSetType, resultSetConcurrency));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            List<PreparedStatement> stmts = new ArrayList<>();
            for (Connection c : getConnections()) {
                stmts.add(c.prepareStatement(sql, columnNames));
            }
            return new FederatingPreparedStatement(stmts.toArray(new PreparedStatement[0]), this, config);
        }
    }

    /**
     * Statement that federates queries across multiple databases.
     */
    private static class FederatingStatement extends AbstractStatement {
        private final FederateConfig config;
        private final List<Statement> delegates;

        public FederatingStatement(List<Statement> delegates, Connection conn, FederateConfig config) throws SQLException {
            super(delegates.get(0), conn);
            this.delegates = delegates;
            this.config = config;
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            List<ResultSet> results = new ArrayList<>();

            if (config.isParallelExecution()) {
                results = executeQueryParallel(sql);
            } else {
                for (Statement s : delegates) {
                    results.add(s.executeQuery(sql));
                }
            }

            return createMergingResultSet(results);
        }

        private List<ResultSet> executeQueryParallel(String sql) throws SQLException {
            ExecutorService executor = Executors.newFixedThreadPool(delegates.size());
            List<Future<ResultSet>> futures = new ArrayList<>();

            for (Statement s : delegates) {
                futures.add(executor.submit(() -> s.executeQuery(sql)));
            }

            List<ResultSet> results = new ArrayList<>();
            try {
                for (Future<ResultSet> f : futures) {
                    results.add(f.get(config.getTimeout(), TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new SQLException("Parallel query execution failed", e);
            } finally {
                executor.shutdown();
            }

            return results;
        }

        private ResultSet createMergingResultSet(List<ResultSet> results) throws SQLException {
            switch (config.getMergeStrategy()) {
                case FIRST_NON_EMPTY:
                    for (ResultSet rs : results) {
                        if (rs.isBeforeFirst() || rs.next()) {
                            // Reset position if we moved
                            if (!rs.isBeforeFirst()) {
                                rs.beforeFirst();
                            }
                            // Close others
                            for (ResultSet other : results) {
                                if (other != rs) {
                                    try { other.close(); } catch (SQLException ignored) {}
                                }
                            }
                            return rs;
                        }
                    }
                    // All empty, return first
                    return results.isEmpty() ? null : results.get(0);

                case CONCAT:
                case UNION_ALL:
                default:
                    return new MergingResultSet(this, results);
            }
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            // Broadcast update to all and sum counts
            int total = 0;
            for (Statement s : delegates) {
                total += s.executeUpdate(sql);
            }
            return total;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            // Execute on all, return first result
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
     * PreparedStatement that federates queries across multiple databases.
     */
    private static class FederatingPreparedStatement extends AbstractPreparedStatement {
        private final FederateConfig config;

        public FederatingPreparedStatement(PreparedStatement[] delegates, Connection conn, FederateConfig config) throws SQLException {
            super(delegates, conn);
            this.config = config;
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            List<ResultSet> results = new ArrayList<>();

            if (config.isParallelExecution()) {
                ExecutorService executor = Executors.newFixedThreadPool(getPreparedStatements().size());
                List<Future<ResultSet>> futures = new ArrayList<>();

                for (PreparedStatement s : getPreparedStatements()) {
                    futures.add(executor.submit(() -> s.executeQuery()));
                }

                try {
                    for (Future<ResultSet> f : futures) {
                        results.add(f.get(config.getTimeout(), TimeUnit.MILLISECONDS));
                    }
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new SQLException("Parallel query execution failed", e);
                } finally {
                    executor.shutdown();
                }
            } else {
                for (PreparedStatement s : getPreparedStatements()) {
                    results.add(s.executeQuery());
                }
            }

            return createMergingResultSet(results);
        }

        private ResultSet createMergingResultSet(List<ResultSet> results) throws SQLException {
            switch (config.getMergeStrategy()) {
                case FIRST_NON_EMPTY:
                    for (ResultSet rs : results) {
                        if (rs.isBeforeFirst() || rs.next()) {
                            if (!rs.isBeforeFirst()) {
                                rs.beforeFirst();
                            }
                            for (ResultSet other : results) {
                                if (other != rs) {
                                    try { other.close(); } catch (SQLException ignored) {}
                                }
                            }
                            return rs;
                        }
                    }
                    return results.isEmpty() ? null : results.get(0);

                case CONCAT:
                case UNION_ALL:
                default:
                    return new MergingResultSet(this, results);
            }
        }
    }

    /**
     * ResultSet that merges multiple ResultSets sequentially.
     */
    private static class MergingResultSet extends AbstractResultSet {
        private final List<ResultSet> delegates;
        private int currentIndex = 0;
        private boolean closed = false;

        public MergingResultSet(Statement stmt, List<ResultSet> delegates) throws SQLException {
            super(stmt, delegates.get(0));
            this.delegates = delegates;
        }

        private ResultSet current() {
            return currentIndex < delegates.size() ? delegates.get(currentIndex) : null;
        }

        @Override
        public boolean next() throws SQLException {
            if (closed) throw new SQLException("ResultSet is closed");

            while (currentIndex < delegates.size()) {
                if (delegates.get(currentIndex).next()) {
                    return true;
                }
                currentIndex++;
            }
            return false;
        }

        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                for (ResultSet rs : delegates) {
                    try { rs.close(); } catch (SQLException ignored) {}
                }
            }
        }

        @Override
        public boolean isClosed() throws SQLException {
            return closed;
        }

        @Override
        public ResultSetMetaData getMetaData() throws SQLException {
            // Use metadata from first ResultSet
            return delegates.get(0).getMetaData();
        }

        // Override getters to delegate to current ResultSet
        @Override
        public String getString(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getString(columnIndex) : null;
        }

        @Override
        public String getString(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getString(columnLabel) : null;
        }

        @Override
        public int getInt(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getInt(columnIndex) : 0;
        }

        @Override
        public int getInt(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getInt(columnLabel) : 0;
        }

        @Override
        public long getLong(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getLong(columnIndex) : 0;
        }

        @Override
        public long getLong(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getLong(columnLabel) : 0;
        }

        @Override
        public double getDouble(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getDouble(columnIndex) : 0;
        }

        @Override
        public double getDouble(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getDouble(columnLabel) : 0;
        }

        @Override
        public boolean getBoolean(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null && c.getBoolean(columnIndex);
        }

        @Override
        public boolean getBoolean(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null && c.getBoolean(columnLabel);
        }

        @Override
        public Object getObject(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getObject(columnIndex) : null;
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getObject(columnLabel) : null;
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getBigDecimal(columnIndex) : null;
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getBigDecimal(columnLabel) : null;
        }

        @Override
        public java.sql.Date getDate(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getDate(columnIndex) : null;
        }

        @Override
        public java.sql.Date getDate(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getDate(columnLabel) : null;
        }

        @Override
        public Time getTime(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getTime(columnIndex) : null;
        }

        @Override
        public Time getTime(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getTime(columnLabel) : null;
        }

        @Override
        public Timestamp getTimestamp(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getTimestamp(columnIndex) : null;
        }

        @Override
        public Timestamp getTimestamp(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getTimestamp(columnLabel) : null;
        }

        @Override
        public byte[] getBytes(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getBytes(columnIndex) : null;
        }

        @Override
        public byte[] getBytes(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getBytes(columnLabel) : null;
        }

        @Override
        public float getFloat(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getFloat(columnIndex) : 0;
        }

        @Override
        public float getFloat(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getFloat(columnLabel) : 0;
        }

        @Override
        public short getShort(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getShort(columnIndex) : 0;
        }

        @Override
        public short getShort(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getShort(columnLabel) : 0;
        }

        @Override
        public byte getByte(int columnIndex) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getByte(columnIndex) : 0;
        }

        @Override
        public byte getByte(String columnLabel) throws SQLException {
            ResultSet c = current();
            return c != null ? c.getByte(columnLabel) : 0;
        }

        @Override
        public boolean wasNull() throws SQLException {
            ResultSet c = current();
            return c != null && c.wasNull();
        }

        @Override
        public int findColumn(String columnLabel) throws SQLException {
            // Use first ResultSet for column lookup
            return delegates.get(0).findColumn(columnLabel);
        }

        @Override
        public int getRow() throws SQLException {
            // Row number tracking across multiple result sets is complex
            // For now, return 0 (unknown)
            return 0;
        }

        @Override
        public boolean isBeforeFirst() throws SQLException {
            return currentIndex == 0 && delegates.get(0).isBeforeFirst();
        }

        @Override
        public boolean isAfterLast() throws SQLException {
            return currentIndex >= delegates.size();
        }

        @Override
        public boolean isFirst() throws SQLException {
            return currentIndex == 0 && delegates.get(0).isFirst();
        }

        @Override
        public boolean isLast() throws SQLException {
            if (currentIndex < delegates.size() - 1) return false;
            return currentIndex == delegates.size() - 1 && delegates.get(currentIndex).isLast();
        }

        @Override
        public void beforeFirst() throws SQLException {
            currentIndex = 0;
            for (ResultSet rs : delegates) {
                rs.beforeFirst();
            }
        }
    }
}
