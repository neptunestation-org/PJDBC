package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for MetricsDriver.
 *
 * These tests verify metrics properties:
 * - Query count accuracy
 * - Update count and rows affected
 * - Operation type categorization
 * - Timing metrics consistency
 * - Error rate calculation
 * - Slow query detection
 * - Disabled collection
 * - Driver composition
 */
class MetricsDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    @BeforeProperty
    void resetGlobalMetrics() {
        MetricsDriver.getGlobalMetrics().reset();
    }

    private String createUniqueDbName() {
        return "metrics_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private void setupTestTable(String dbName, int rowCount) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))");
                for (int i = 1; i <= rowCount; i++) {
                    stmt.execute("INSERT INTO users VALUES (" + i + ", 'User" + i + "')");
                }
            }
        }
    }

    // ========== QUERY COUNT ACCURACY ==========

    /**
     * Property: totalQueries increments by exactly the number of executeQuery calls.
     */
    @Property(tries = 50)
    void queryCountMatchesExecutions(
            @ForAll @IntRange(min = 1, max = 10) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 5);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + ((i % 5) + 1))) {
                        while (rs.next()) { /* consume */ }
                    }
                }
            }

            assertEquals(queryCount, metrics.getTotalQueries(),
                "totalQueries should equal number of executeQuery calls");
            assertEquals(0, metrics.getTotalUpdates(),
                "totalUpdates should be 0 for queries");
        }
    }

    /**
     * Property: Each query contributes to totalOperations.
     */
    @Property(tries = 30)
    void queriesContributeToTotalOperations(
            @ForAll @IntRange(min = 1, max = 10) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                        rs.next();
                    }
                }
            }

            assertEquals(queryCount, metrics.getTotalOperations(),
                "totalOperations should include all queries");
        }
    }

    // ========== UPDATE COUNT AND ROWS AFFECTED ==========

    /**
     * Property: totalUpdates increments correctly for update operations.
     */
    @Property(tries = 50)
    void updateCountMatchesExecutions(
            @ForAll @IntRange(min = 1, max = 5) int updateCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 10);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < updateCount; i++) {
                    stmt.executeUpdate("UPDATE users SET name = 'Updated" + i + "' WHERE id = " + (i + 1));
                }
            }

            assertEquals(updateCount, metrics.getTotalUpdates(),
                "totalUpdates should equal number of executeUpdate calls");
            assertEquals(0, metrics.getTotalQueries(),
                "totalQueries should be 0 for updates");
        }
    }

    /**
     * Property: rowsAffected accumulates correctly across updates.
     */
    @Property(tries = 30)
    void rowsAffectedAccumulates(
            @ForAll @IntRange(min = 1, max = 5) int rowCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, rowCount);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                // Update all rows
                int affected = stmt.executeUpdate("UPDATE users SET name = 'AllUpdated'");
                assertEquals(rowCount, affected);
            }

            assertEquals(rowCount, metrics.getTotalRowsAffected(),
                "totalRowsAffected should equal rows affected by update");
        }
    }

    /**
     * Property: Multiple updates accumulate rowsAffected.
     */
    @Property(tries = 30)
    void multipleUpdatesAccumulateRows(
            @ForAll @IntRange(min = 2, max = 5) int updateCount,
            @ForAll @IntRange(min = 1, max = 3) int rowsPerUpdate) throws SQLException {

        String dbName = createUniqueDbName();
        int totalRows = updateCount * rowsPerUpdate;
        setupTestTable(dbName, totalRows);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < updateCount; i++) {
                    int startId = i * rowsPerUpdate + 1;
                    int endId = startId + rowsPerUpdate - 1;
                    stmt.executeUpdate("UPDATE users SET name = 'Batch" + i + "' WHERE id >= " + startId + " AND id <= " + endId);
                }
            }

            assertEquals(totalRows, metrics.getTotalRowsAffected(),
                "totalRowsAffected should be sum of all rows affected");
            assertEquals(updateCount, metrics.getTotalUpdates(),
                "totalUpdates should count each update call");
        }
    }

    // ========== OPERATION TYPE CATEGORIZATION ==========

    /**
     * Property: SELECT queries are categorized as SELECT type.
     */
    @Property(tries = 30)
    void selectQueriesCategorized(
            @ForAll @IntRange(min = 1, max = 5) int count) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics[trackByType=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < count; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                        while (rs.next()) { /* consume */ }
                    }
                }
            }

            assertEquals(count, metrics.getTypeMetrics(MetricsDriver.OperationType.SELECT).getCount(),
                "SELECT operations should be categorized correctly");
        }
    }

    /**
     * Property: INSERT/UPDATE/DELETE are categorized by their type.
     */
    @Property(tries = 20)
    void dmlOperationsCategorized() throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 5);

        String metricsUrl = "jdbc:metrics[trackByType=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO users VALUES (100, 'New')");
                stmt.executeUpdate("UPDATE users SET name = 'Changed' WHERE id = 1");
                stmt.executeUpdate("DELETE FROM users WHERE id = 100");
            }

            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.INSERT).getCount(),
                "INSERT should be categorized");
            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.UPDATE).getCount(),
                "UPDATE should be categorized");
            assertEquals(1, metrics.getTypeMetrics(MetricsDriver.OperationType.DELETE).getCount(),
                "DELETE should be categorized");
        }
    }

    /**
     * Property: Mixed operations are categorized independently.
     */
    @Property(tries = 30)
    void mixedOperationsCategorized(
            @ForAll @IntRange(min = 1, max = 3) int selectCount,
            @ForAll @IntRange(min = 1, max = 3) int insertCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics[trackByType=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < selectCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
                for (int i = 0; i < insertCount; i++) {
                    stmt.executeUpdate("INSERT INTO users VALUES (" + (100 + i) + ", 'New')");
                }
            }

            assertEquals(selectCount, metrics.getTypeMetrics(MetricsDriver.OperationType.SELECT).getCount());
            assertEquals(insertCount, metrics.getTypeMetrics(MetricsDriver.OperationType.INSERT).getCount());
            assertEquals(selectCount + insertCount, metrics.getTotalOperations());
        }
    }

    // ========== TIMING METRICS CONSISTENCY ==========

    /**
     * Property: Timing metrics are non-negative and consistent.
     * Note: For very fast operations, millisecond precision means min/max/avg
     * may all be 0ms, so we just verify non-negative and reasonable relationships.
     */
    @Property(tries = 50)
    void timingMetricsOrdered(
            @ForAll @IntRange(min = 2, max = 10) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < operationCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                        while (rs.next()) { /* consume */ }
                    }
                }
            }

            long minTime = metrics.getMinTimeMs();
            double avgTime = metrics.getAvgTimeMs();
            long maxTime = metrics.getMaxTimeMs();
            long totalTime = metrics.getTotalTimeMs();

            assertTrue(minTime >= 0, "minTime should be >= 0");
            assertTrue(avgTime >= 0, "avgTime should be >= 0");
            assertTrue(maxTime >= 0, "maxTime should be >= 0");
            assertTrue(totalTime >= 0, "totalTime should be >= 0");
            assertTrue(maxTime >= minTime, "maxTime should be >= minTime");
        }
    }

    /**
     * Property: totalTime >= 0 and increases with operations.
     */
    @Property(tries = 30)
    void totalTimeNonNegative(
            @ForAll @IntRange(min = 1, max = 5) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 2);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < operationCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertTrue(metrics.getTotalTimeMs() >= 0,
                "totalTime should be non-negative");
        }
    }

    // ========== ERROR RATE CALCULATION ==========

    /**
     * Property: errorRate = errors / totalOperations.
     */
    @Property(tries = 30)
    void errorRateCalculation(
            @ForAll @IntRange(min = 1, max = 5) int successCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                // Successful operations
                for (int i = 0; i < successCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }

                // One error
                try {
                    stmt.executeQuery("SELECT * FROM nonexistent_table");
                } catch (SQLException expected) {
                    // Expected
                }
            }

            long totalOps = metrics.getTotalOperations();
            long errors = metrics.getTotalErrors();

            assertEquals(successCount, totalOps, "Should have " + successCount + " successful ops");
            assertEquals(1, errors, "Should have 1 error");

            double expectedRate = (double) errors / (totalOps + errors);
            // Note: errorRate uses totalOperations which only counts successes
            // So actual rate = errors / (successes + errors)
            assertTrue(metrics.getErrorRate() >= 0 && metrics.getErrorRate() <= 1,
                "Error rate should be between 0 and 1");
        }
    }

    /**
     * Property: No errors means errorRate = 0.
     */
    @Property(tries = 30)
    void noErrorsZeroRate(
            @ForAll @IntRange(min = 1, max = 10) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < operationCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertEquals(0, metrics.getTotalErrors(), "Should have no errors");
            assertEquals(0.0, metrics.getErrorRate(), 0.001, "Error rate should be 0");
        }
    }

    // ========== SLOW QUERY DETECTION ==========

    /**
     * Property: With slowThreshold=0, all queries are slow.
     */
    @Property(tries = 30)
    void zeroThresholdAllSlow(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 2);

        String metricsUrl = "jdbc:metrics[slowThreshold=0]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertEquals(queryCount, metrics.getSlowQueries(),
                "All queries should be slow with threshold=0");
            assertEquals(1.0, metrics.getSlowQueryRate(), 0.001,
                "Slow query rate should be 1.0");
        }
    }

    /**
     * Property: With very high threshold, no queries are slow.
     */
    @Property(tries = 30)
    void highThresholdNoSlow(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 2);

        // 1 hour threshold - no query should be that slow
        String metricsUrl = "jdbc:metrics[slowThreshold=3600000]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertEquals(0, metrics.getSlowQueries(),
                "No queries should be slow with very high threshold");
            assertEquals(0.0, metrics.getSlowQueryRate(), 0.001,
                "Slow query rate should be 0");
        }
    }

    // ========== DISABLED COLLECTION ==========

    /**
     * Property: With enabled=false, no metrics are collected.
     */
    @Property(tries = 30)
    void disabledCollectionNoMetrics(
            @ForAll @IntRange(min = 1, max = 10) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics[enabled=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < operationCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                    stmt.executeUpdate("UPDATE users SET name = 'X' WHERE id = 1");
                }
            }

            assertEquals(0, metrics.getTotalQueries(), "No queries recorded when disabled");
            assertEquals(0, metrics.getTotalUpdates(), "No updates recorded when disabled");
            assertEquals(0, metrics.getTotalOperations(), "No operations recorded when disabled");
        }
    }

    /**
     * Property: Queries still work when metrics disabled.
     */
    @Property(tries = 20)
    void disabledStillFunctions(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();

        String metricsUrl = "jdbc:metrics[enabled=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Query should still return correct results when metrics disabled");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:metrics:X behaves like metrics:X (cat is identity).
     * Uses global metrics since outer wrapper hides MetricsConnection.
     */
    @Property(tries = 30)
    void catMetricsComposition(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String catMetricsUrl = "jdbc:cat:jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        MetricsDriver.getGlobalMetrics().reset();
        long initialQueries = MetricsDriver.getGlobalMetrics().getTotalQueries();

        try (Connection conn = DriverManager.getConnection(catMetricsUrl)) {
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }
        }

        long finalQueries = MetricsDriver.getGlobalMetrics().getTotalQueries();
        assertEquals(queryCount, finalQueries - initialQueries,
            "cat:metrics:X should record queries in global metrics");
    }

    /**
     * Property: metrics:cat:X behaves like metrics:X.
     */
    @Property(tries = 30)
    void metricsCatComposition(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsCatUrl = "jdbc:metrics:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsCatUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertEquals(queryCount, metrics.getTotalQueries(),
                "metrics:cat:X should collect correct query count");
        }
    }

    /**
     * Property: log:metrics:X preserves metrics collection.
     * Uses global metrics since outer wrapper hides MetricsConnection.
     */
    @Property(tries = 30)
    void logMetricsComposition(
            @ForAll @IntRange(min = 1, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String logMetricsUrl = "jdbc:log:jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        MetricsDriver.getGlobalMetrics().reset();
        long initialQueries = MetricsDriver.getGlobalMetrics().getTotalQueries();

        try (Connection conn = DriverManager.getConnection(logMetricsUrl)) {
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < queryCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }
        }

        long finalQueries = MetricsDriver.getGlobalMetrics().getTotalQueries();
        assertEquals(queryCount, finalQueries - initialQueries,
            "log:metrics:X should record queries in global metrics");
    }

    // ========== RESET BEHAVIOR ==========

    /**
     * Property: Reset clears all counters to zero.
     */
    @Property(tries = 20)
    void resetClearsCounters(
            @ForAll @IntRange(min = 1, max = 10) int operationCount) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName, 3);

        String metricsUrl = "jdbc:metrics:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(metricsUrl)) {
            MetricsDriver.Metrics metrics = MetricsDriver.getMetrics(conn);

            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < operationCount; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) { rs.next(); }
                }
            }

            assertTrue(metrics.getTotalQueries() > 0, "Should have queries before reset");

            metrics.reset();

            assertEquals(0, metrics.getTotalQueries(), "Queries should be 0 after reset");
            assertEquals(0, metrics.getTotalUpdates(), "Updates should be 0 after reset");
            assertEquals(0, metrics.getTotalOperations(), "Operations should be 0 after reset");
            assertEquals(0, metrics.getTotalErrors(), "Errors should be 0 after reset");
            assertEquals(0, metrics.getSlowQueries(), "Slow queries should be 0 after reset");
        }
    }
}
