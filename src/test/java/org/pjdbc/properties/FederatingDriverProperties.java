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
 * Property-based tests for FederatingDriver.
 *
 * FederatingDriver merges query results from multiple databases:
 * - CONCAT/UNION_ALL: combines all rows from all sources
 * - FIRST_NON_EMPTY: returns first non-empty result set
 * - Write broadcast to all connections
 * - URL parsing and validation
 * - Composition with other drivers
 */
class FederatingDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "fed_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== RESULT MERGING PROPERTIES ==========

    /**
     * Property: CONCAT strategy merges rows from all databases.
     * Uses DB_CLOSE_DELAY=-1 to keep H2 in-memory databases alive.
     */
    @Property(tries = 10)
    void concatMergesAllRows() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // Setup data in each database independently
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE data (val INT)");
            s1.executeUpdate("INSERT INTO data VALUES (1)");
            s1.executeUpdate("INSERT INTO data VALUES (2)");
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE data (val INT)");
            s2.executeUpdate("INSERT INTO data VALUES (3)");
            s2.executeUpdate("INSERT INTO data VALUES (4)");
        }

        // Query via federate - should get all 4 rows
        String fedUrl = "jdbc:federate[mergeStrategy=concat]:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT val FROM data")) {

            Set<Integer> values = new HashSet<>();
            while (rs.next()) {
                values.add(rs.getInt(1));
            }

            assertEquals(4, values.size(), "Should get all 4 rows from both databases");
            assertTrue(values.contains(1), "Should contain value 1");
            assertTrue(values.contains(2), "Should contain value 2");
            assertTrue(values.contains(3), "Should contain value 3");
            assertTrue(values.contains(4), "Should contain value 4");
        }
    }

    /**
     * Property: UNION_ALL behaves same as CONCAT.
     */
    @Property(tries = 10)
    void unionAllBehavesLikeConcat() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // Setup
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE nums (n INT)");
            s1.executeUpdate("INSERT INTO nums VALUES (10)");
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE nums (n INT)");
            s2.executeUpdate("INSERT INTO nums VALUES (20)");
        }

        String fedUrl = "jdbc:federate[mergeStrategy=union_all]:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT n FROM nums")) {

            List<Integer> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getInt(1));
            }

            assertEquals(2, values.size(), "UNION_ALL should combine rows");
            assertTrue(values.contains(10), "Should contain 10");
            assertTrue(values.contains(20), "Should contain 20");
        }
    }

    /**
     * Property: FIRST_NON_EMPTY returns first result set with data.
     */
    @Property(tries = 10)
    void firstNonEmptyReturnsFirstWithData() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // First DB is empty, second has data
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE items (id INT)");
            // No inserts - empty table
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE items (id INT)");
            s2.executeUpdate("INSERT INTO items VALUES (99)");
        }

        String fedUrl = "jdbc:federate[mergeStrategy=first_non_empty]:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM items")) {

            assertTrue(rs.next(), "Should have at least one row");
            assertEquals(99, rs.getInt(1), "Should return data from second (non-empty) database");
            assertFalse(rs.next(), "Should have exactly one row");
        }
    }

    /**
     * Property: Empty results from all databases returns empty result set.
     */
    @Property(tries = 10)
    void emptyFromAllReturnsEmpty() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // Both empty
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE empty_table (x INT)");
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE empty_table (x INT)");
        }

        String fedUrl = "jdbc:federate:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT x FROM empty_table")) {

            assertFalse(rs.next(), "Empty tables should yield empty result");
        }
    }

    // ========== WRITE BROADCAST PROPERTIES ==========

    /**
     * Property: executeUpdate broadcasts to all and sums counts.
     */
    @Property(tries = 20)
    void executeUpdateBroadcastsToAll() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO t VALUES (1)");
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "Connection 1 should receive executeUpdate");
        assertTrue(log2.contains("executeUpdate"),
            "Connection 2 should receive executeUpdate");
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
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(fedUrl);
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

    /**
     * Property: Writes propagate to all real databases.
     */
    @Property(tries = 10)
    void writePropagationToRealDatabases() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";
        String fedUrl = "jdbc:federate:" + h2Url1 + ";" + h2Url2;

        // Create and insert via federate
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE sync_test (val INT)");
            stmt.executeUpdate("INSERT INTO sync_test VALUES (123)");
        }

        // Verify both databases have the data
        for (String url : Arrays.asList(h2Url1, h2Url2)) {
            try (Connection c = DriverManager.getConnection(url);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT val FROM sync_test")) {
                assertTrue(rs.next(), "Row should exist in " + url);
                assertEquals(123, rs.getInt(1), "Value should match in " + url);
            }
        }
    }

    // ========== PREPARED STATEMENT PROPERTIES ==========

    /**
     * Property: PreparedStatement queries merge results.
     */
    @Property(tries = 10)
    void preparedStatementQueriesMerge() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // Setup
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE ps_test (id INT)");
            s1.executeUpdate("INSERT INTO ps_test VALUES (1)");
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE ps_test (id INT)");
            s2.executeUpdate("INSERT INTO ps_test VALUES (2)");
        }

        String fedUrl = "jdbc:federate:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM ps_test WHERE id > ?")) {
            pstmt.setInt(1, 0);
            try (ResultSet rs = pstmt.executeQuery()) {
                Set<Integer> ids = new HashSet<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                assertEquals(2, ids.size(), "Should get rows from both databases");
            }
        }
    }

    /**
     * Property: PreparedStatement updates broadcast to all.
     */
    @Property(tries = 10)
    void preparedStatementUpdatesBroadcast() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(fedUrl);
             PreparedStatement pstmt = conn.prepareStatement("UPDATE t SET x = ? WHERE id = ?")) {
            pstmt.setInt(1, 100);
            pstmt.setInt(2, 1);
            pstmt.executeUpdate();
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "PreparedStatement update should reach connection 1");
        assertTrue(log2.contains("executeUpdate"),
            "PreparedStatement update should reach connection 2");
    }

    // ========== URL PARSING PROPERTIES ==========

    /**
     * Property: FederatingDriver accepts valid URLs with 2+ database URLs.
     */
    @Property(tries = 20)
    void acceptsValidUrls(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2) {

        Assume.that(!db1.equals(db2));

        FederatingDriver driver = new FederatingDriver();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;

        assertTrue(driver.acceptsURL(fedUrl),
            "Should accept valid federate URL: " + fedUrl);
    }

    /**
     * Property: FederatingDriver rejects single-URL configurations.
     */
    @Property(tries = 10)
    void rejectsSingleUrl(
            @ForAll("mockDbNames") String db) {

        FederatingDriver driver = new FederatingDriver();
        String fedUrl = "jdbc:federate:jdbc:mock:" + db;

        assertFalse(driver.acceptsURL(fedUrl),
            "Should reject single URL: " + fedUrl);
    }

    /**
     * Property: All merge strategies are accepted in URL.
     */
    @Property(tries = 10)
    void allMergeStrategiesAccepted() {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        FederatingDriver driver = new FederatingDriver();
        String[] strategies = {"concat", "union_all", "first_non_empty"};

        for (String strategy : strategies) {
            String fedUrl = "jdbc:federate[mergeStrategy=" + strategy + "]:" + url1 + ";" + url2;
            assertTrue(driver.acceptsURL(fedUrl),
                "Should accept mergeStrategy=" + strategy);
        }
    }

    /**
     * Property: parallelExecution parameter is accepted.
     */
    @Property(tries = 10)
    void parallelExecutionParameterAccepted() {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        FederatingDriver driver = new FederatingDriver();

        String fedUrlTrue = "jdbc:federate[parallelExecution=true]:" + url1 + ";" + url2;
        String fedUrlFalse = "jdbc:federate[parallelExecution=false]:" + url1 + ";" + url2;

        assertTrue(driver.acceptsURL(fedUrlTrue),
            "Should accept parallelExecution=true");
        assertTrue(driver.acceptsURL(fedUrlFalse),
            "Should accept parallelExecution=false");
    }

    // ========== COMPOSITION PROPERTIES ==========

    /**
     * Property: log:federate composition preserves federation.
     */
    @Property(tries = 10)
    void logFederateComposition() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;
        String logFedUrl = "jdbc:log:" + fedUrl;

        try (Connection conn = DriverManager.getConnection(logFedUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO t VALUES (1)");
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertTrue(log1.contains("executeUpdate"),
            "log:federate should broadcast to connection 1");
        assertTrue(log2.contains("executeUpdate"),
            "log:federate should broadcast to connection 2");
    }

    /**
     * Property: cat:federate composition preserves federation.
     */
    @Property(tries = 10)
    void catFederateComposition() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String fedUrl = "jdbc:federate:" + url1 + ";" + url2;
        String catFedUrl = "jdbc:cat:" + fedUrl;

        try (Connection conn = DriverManager.getConnection(catFedUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM t");
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        // Both should receive the query (federation queries all)
        assertTrue(log1.contains("executeQuery"),
            "cat:federate should query connection 1");
        assertTrue(log2.contains("executeQuery"),
            "cat:federate should query connection 2");
    }

    // ========== ROW COUNT PROPERTIES ==========

    /**
     * Property: Row count from federated query equals sum of individual counts.
     */
    @Property(tries = 10)
    void rowCountEqualsSumOfIndividual(
            @ForAll @IntRange(min = 1, max = 5) int rows1,
            @ForAll @IntRange(min = 1, max = 5) int rows2) throws SQLException {

        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1 + ";DB_CLOSE_DELAY=-1";
        String h2Url2 = "jdbc:h2:mem:" + db2 + ";DB_CLOSE_DELAY=-1";

        // Insert specified number of rows into each
        try (Connection c1 = DriverManager.getConnection(h2Url1);
             Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE counts (n INT)");
            for (int i = 0; i < rows1; i++) {
                s1.executeUpdate("INSERT INTO counts VALUES (" + i + ")");
            }
        }

        try (Connection c2 = DriverManager.getConnection(h2Url2);
             Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE counts (n INT)");
            for (int i = 0; i < rows2; i++) {
                s2.executeUpdate("INSERT INTO counts VALUES (" + (100 + i) + ")");
            }
        }

        String fedUrl = "jdbc:federate:" + h2Url1 + ";" + h2Url2;
        try (Connection conn = DriverManager.getConnection(fedUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT n FROM counts")) {

            int count = 0;
            while (rs.next()) {
                count++;
            }

            assertEquals(rows1 + rows2, count,
                "Federated row count should equal sum of individual counts");
        }
    }

    // ========== CONNECTION LIFECYCLE PROPERTIES ==========

    /**
     * Property: Closing connection marks it as closed.
     */
    @Property(tries = 10)
    void closeMarksConnectionAsClosed() throws SQLException {
        String db1 = createUniqueDbName();
        String db2 = createUniqueDbName();
        String h2Url1 = "jdbc:h2:mem:" + db1;
        String h2Url2 = "jdbc:h2:mem:" + db2;
        String fedUrl = "jdbc:federate:" + h2Url1 + ";" + h2Url2;

        Connection conn = DriverManager.getConnection(fedUrl);
        assertFalse(conn.isClosed(), "Connection should not be closed initially");
        conn.close();
        assertTrue(conn.isClosed(), "Connection should be closed after close()");
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
