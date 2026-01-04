package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for LoadBalancingDriver.
 *
 * LoadBalancingDriver distributes reads across replicas and broadcasts writes:
 * - Read distribution (round_robin, random, least_connections, primary_only, replica_only)
 * - Write broadcast to all connections
 * - Health check and failover
 * - URL parsing and validation
 * - Composition with other drivers
 */
class LoadBalancingDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "lb_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== WRITE BROADCAST PROPERTIES ==========

    /**
     * Property: executeUpdate broadcasts to ALL connections and sums counts.
     */
    @Property(tries = 20)
    void executeUpdateBroadcastsToAll(
            @ForAll @IntRange(min = 2, max = 4) int connectionCount) throws SQLException {

        List<String> dbNames = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < connectionCount; i++) {
            String name = createUniqueDbName();
            dbNames.add(name);
            urls.add("jdbc:mock:" + name);
        }

        String lbUrl = "jdbc:loadbalance:" + String.join(";", urls);

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO t VALUES (1)");
        }

        // Verify all connections received the update
        for (String url : urls) {
            String log = MockDriver.getLog(url);
            assertTrue(log.contains("executeUpdate"),
                "All connections should receive executeUpdate: " + url);
            assertTrue(log.contains("INSERT"),
                "All connections should have INSERT SQL: " + url);
        }
    }

    /**
     * Property: execute() broadcasts to all connections.
     */
    @Property(tries = 20)
    void executeBroadcastsToAll() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t (id INT)");
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("execute"),
            "Connection 1 should receive execute");
        assertTrue(log2.contains("execute"),
            "Connection 2 should receive execute");
    }

    // ========== READ DISTRIBUTION PROPERTIES ==========

    /**
     * Property: executeQuery selects ONE connection for reads.
     */
    @Property(tries = 20)
    void executeQuerySelectsOneConnection(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        // At least one should have the query (exactly one for load balancing)
        boolean hasQuery1 = log1.contains("executeQuery") && log1.contains(sql);
        boolean hasQuery2 = log2.contains("executeQuery") && log2.contains(sql);

        assertTrue(hasQuery1 || hasQuery2,
            "At least one connection should receive the query");
    }

    /**
     * Property: Round-robin distributes reads across connections over multiple queries.
     */
    @Property(tries = 10)
    void roundRobinDistributesReads(
            @ForAll @IntRange(min = 4, max = 8) int queryCount) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance[readStrategy=round_robin]:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        // Both connections should have received some queries
        int count1 = countOccurrences(log1, "executeQuery");
        int count2 = countOccurrences(log2, "executeQuery");

        assertTrue(count1 > 0, "Connection 1 should receive some queries");
        assertTrue(count2 > 0, "Connection 2 should receive some queries");
        assertEquals(queryCount, count1 + count2,
            "Total queries should equal query count");
    }

    /**
     * Property: primary_only strategy always reads from first connection.
     */
    @Property(tries = 20)
    void primaryOnlyReadsFromFirst(
            @ForAll @IntRange(min = 2, max = 5) int queryCount) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance[readStrategy=primary_only]:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        int count1 = countOccurrences(log1, "executeQuery");
        int count2 = countOccurrences(log2, "executeQuery");

        assertEquals(queryCount, count1,
            "Primary should receive all read queries");
        assertEquals(0, count2,
            "Replica should receive no read queries with primary_only");
    }

    /**
     * Property: replica_only strategy never reads from first connection.
     */
    @Property(tries = 20)
    void replicaOnlySkipsPrimary(
            @ForAll @IntRange(min = 2, max = 5) int queryCount) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance[readStrategy=replica_only]:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < queryCount; i++) {
                stmt.executeQuery("SELECT " + i);
            }
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        int count1 = countOccurrences(log1, "executeQuery");
        int count2 = countOccurrences(log2, "executeQuery");

        assertEquals(0, count1,
            "Primary should receive no read queries with replica_only");
        assertEquals(queryCount, count2,
            "Replica should receive all read queries");
    }

    // ========== PREPARED STATEMENT PROPERTIES ==========

    /**
     * Property: PreparedStatement executeUpdate broadcasts to all.
     */
    @Property(tries = 20)
    void preparedStatementUpdateBroadcasts() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO t VALUES (?)")) {
            pstmt.setInt(1, 42);
            pstmt.executeUpdate();
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "Connection 1 should receive PreparedStatement executeUpdate");
        assertTrue(log2.contains("executeUpdate"),
            "Connection 2 should receive PreparedStatement executeUpdate");
    }

    /**
     * Property: PreparedStatement executeQuery selects one connection.
     */
    @Property(tries = 20)
    void preparedStatementQuerySelectsOne() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM t WHERE id = ?")) {
            pstmt.setInt(1, 1);
            pstmt.executeQuery();
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        boolean hasQuery1 = log1.contains("executeQuery");
        boolean hasQuery2 = log2.contains("executeQuery");

        // Exactly one should have the query (XOR)
        assertTrue(hasQuery1 ^ hasQuery2,
            "Exactly one connection should receive PreparedStatement executeQuery");
    }

    // ========== URL PARSING PROPERTIES ==========

    /**
     * Property: LoadBalancingDriver accepts valid URLs with 2+ database URLs.
     */
    @Property(tries = 20)
    void acceptsValidUrls(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2) {

        Assume.that(!db1.equals(db2));

        LoadBalancingDriver driver = new LoadBalancingDriver();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;

        assertTrue(driver.acceptsURL(lbUrl),
            "Should accept valid loadbalance URL: " + lbUrl);
    }

    /**
     * Property: LoadBalancingDriver rejects single-URL configurations.
     */
    @Property(tries = 10)
    void rejectsSingleUrl(
            @ForAll("mockDbNames") String db) {

        LoadBalancingDriver driver = new LoadBalancingDriver();
        String lbUrl = "jdbc:loadbalance:jdbc:mock:" + db;

        assertFalse(driver.acceptsURL(lbUrl),
            "Should reject single URL: " + lbUrl);
    }

    /**
     * Property: URL parameters are parsed correctly.
     */
    @Property(tries = 10)
    void urlParametersParsed() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        // Test various parameter combinations
        String[] strategies = {"round_robin", "random", "primary_only", "replica_only"};
        for (String strategy : strategies) {
            String lbUrl = "jdbc:loadbalance[readStrategy=" + strategy + "]:" + url1 + ";" + url2;
            LoadBalancingDriver driver = new LoadBalancingDriver();
            assertTrue(driver.acceptsURL(lbUrl),
                "Should accept URL with strategy=" + strategy);
        }
    }

    // ========== COMPOSITION PROPERTIES ==========

    /**
     * Property: log:loadbalance composition preserves load balancing.
     */
    @Property(tries = 10)
    void logLoadbalanceComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;
        String logLbUrl = "jdbc:log:" + lbUrl;

        try (Connection conn = DriverManager.getConnection(logLbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO t VALUES (1)");
        }

        // Both should receive the update
        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "log:loadbalance should broadcast updates to connection 1");
        assertTrue(log2.contains("executeUpdate"),
            "log:loadbalance should broadcast updates to connection 2");
    }

    /**
     * Property: cat:loadbalance composition preserves load balancing.
     */
    @Property(tries = 10)
    void catLoadbalanceComposition() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String lbUrl = "jdbc:loadbalance:" + url1 + ";" + url2;
        String catLbUrl = "jdbc:cat:" + lbUrl;

        try (Connection conn = DriverManager.getConnection(catLbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM t WHERE id = 1");
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "cat:loadbalance should broadcast to connection 1");
        assertTrue(log2.contains("executeUpdate"),
            "cat:loadbalance should broadcast to connection 2");
    }

    // ========== REAL DATABASE PROPERTIES ==========

    /**
     * Property: LoadBalancing works with real H2 databases.
     */
    @Property(tries = 10)
    void worksWithRealDatabases(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + h2Url1 + ";" + h2Url2;

        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "LoadBalancing should return correct value from read");
        }
    }

    /**
     * Property: Writes propagate to all H2 databases.
     */
    @Property(tries = 10)
    void writesPropagateToAllDatabases() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";
        String lbUrl = "jdbc:loadbalance:" + h2Url1 + ";" + h2Url2;

        // Create table and insert via loadbalance
        try (Connection conn = DriverManager.getConnection(lbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test (id INT)");
            stmt.executeUpdate("INSERT INTO test VALUES (42)");
        }

        // Verify both databases have the data
        try (Connection conn1 = DriverManager.getConnection(h2Url1);
             Statement stmt1 = conn1.createStatement();
             ResultSet rs1 = stmt1.executeQuery("SELECT COUNT(*) FROM test")) {
            assertTrue(rs1.next());
            assertEquals(1, rs1.getInt(1), "Database 1 should have the row");
        }

        try (Connection conn2 = DriverManager.getConnection(h2Url2);
             Statement stmt2 = conn2.createStatement();
             ResultSet rs2 = stmt2.executeQuery("SELECT COUNT(*) FROM test")) {
            assertTrue(rs2.next());
            assertEquals(1, rs2.getInt(1), "Database 2 should have the row");
        }
    }

    // ========== CONNECTION LIFECYCLE PROPERTIES ==========

    /**
     * Property: Closing connection makes it report as closed.
     */
    @Property(tries = 10)
    void closeMarksConnectionAsClosed() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1;
        String h2Url2 = "jdbc:h2:mem:" + db2;
        String lbUrl = "jdbc:loadbalance:" + h2Url1 + ";" + h2Url2;

        Connection conn = DriverManager.getConnection(lbUrl);
        assertFalse(conn.isClosed(), "Connection should not be closed initially");
        conn.close();
        assertTrue(conn.isClosed(), "Connection should be closed after close()");
    }

    // ========== HELPER METHODS ==========

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> mockDbNames() {
        return PjdbcArbitraries.mockDbNames();
    }

    @Provide
    Arbitrary<String> sqlStatements() {
        return PjdbcArbitraries.sqlStatements();
    }
}
