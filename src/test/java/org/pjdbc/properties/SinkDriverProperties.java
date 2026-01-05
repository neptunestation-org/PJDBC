package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.pjdbc.drivers.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for SinkDriver.
 *
 * SinkDriver is a "black hole" driver that absorbs all SQL operations:
 * - executeQuery returns null
 * - executeUpdate returns 0
 * - execute returns true
 * - Operations don't reach the underlying driver
 */
class SinkDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "sink_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== EXECUTEQUERY RETURNS NULL ==========

    /**
     * Property: executeQuery always returns null for any SQL.
     */
    @Property(tries = 20)
    void executeQueryReturnsNull(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            assertNull(rs, "executeQuery should return null for: " + sql);
        }
    }

    /**
     * Property: executeQuery returns null for SELECT statements.
     */
    @Property(tries = 10)
    void executeQueryReturnsNullForSelect(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT " + value);
            assertNull(rs, "executeQuery should return null even for valid SELECT");
        }
    }

    // ========== EXECUTEUPDATE RETURNS 0 ==========

    /**
     * Property: executeUpdate always returns 0 for any SQL.
     */
    @Property(tries = 20)
    void executeUpdateReturnsZero(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            int result = stmt.executeUpdate(sql);
            assertEquals(0, result, "executeUpdate should return 0 for: " + sql);
        }
    }

    /**
     * Property: executeUpdate returns 0 regardless of SQL type.
     */
    @Property(tries = 10)
    void executeUpdateReturnsZeroForAllTypes(
            @ForAll("allSqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            int result = stmt.executeUpdate(sql);
            assertEquals(0, result, "executeUpdate should always return 0");
        }
    }

    // ========== EXECUTE RETURNS TRUE ==========

    /**
     * Property: execute always returns true for any SQL.
     */
    @Property(tries = 20)
    void executeReturnsTrue(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            boolean result = stmt.execute(sql);
            assertTrue(result, "execute should return true for: " + sql);
        }
    }

    /**
     * Property: execute returns true for all SQL types.
     */
    @Property(tries = 10)
    void executeReturnsTrueForAllTypes(
            @ForAll("allSqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String sinkUrl = "jdbc:sink:jdbc:mock:" + dbName;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            boolean result = stmt.execute(sql);
            assertTrue(result, "execute should always return true");
        }
    }

    // ========== OPERATIONS DON'T REACH UNDERLYING DRIVER ==========

    /**
     * Property: executeQuery doesn't reach underlying driver.
     * Note: close[] still reaches underlying, but SQL operations don't.
     */
    @Property(tries = 10)
    void executeQueryDoesNotReachUnderlying(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        // close[] reaches underlying, but SQL operations don't
        assertFalse(mockLog.contains("executeQuery"),
            "executeQuery should not reach underlying driver");
        assertFalse(mockLog.contains(sql),
            "SQL should not reach underlying driver");
    }

    /**
     * Property: executeUpdate doesn't reach underlying driver.
     */
    @Property(tries = 10)
    void executeUpdateDoesNotReachUnderlying(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeUpdate"),
            "executeUpdate should not reach underlying driver");
        assertFalse(mockLog.contains(sql),
            "SQL should not reach underlying driver");
    }

    /**
     * Property: execute doesn't reach underlying driver.
     */
    @Property(tries = 10)
    void executeDoesNotReachUnderlying(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("execute["),
            "execute should not reach underlying driver");
        assertFalse(mockLog.contains(sql),
            "SQL should not reach underlying driver");
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:sink:X preserves sink absorption (cat is identity).
     */
    @Property(tries = 10)
    void catSinkComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String catSinkUrl = "jdbc:cat:jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(catSinkUrl);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            assertNull(rs, "cat:sink should still return null");
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeQuery"),
            "cat:sink should still absorb SQL operations");
        assertFalse(mockLog.contains(sql),
            "cat:sink should not pass SQL to underlying");
    }

    /**
     * Property: sink:cat:X absorbs operations (sink wraps cat).
     */
    @Property(tries = 10)
    void sinkCatComposition(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkCatUrl = "jdbc:sink:jdbc:cat:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkCatUrl);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            assertNull(rs, "sink:cat should return null");

            int updateResult = stmt.executeUpdate("INSERT INTO t VALUES (1)");
            assertEquals(0, updateResult, "sink:cat should return 0");
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeQuery"),
            "sink:cat should absorb SQL operations");
        assertFalse(mockLog.contains("executeUpdate"),
            "sink:cat should absorb update operations");
    }

    // ========== MULTIPLE OPERATIONS ABSORBED ==========

    /**
     * Property: Multiple executeQuery calls all absorbed.
     */
    @Property(tries = 10)
    void multipleExecuteQueryAbsorbed(
            @ForAll @IntRange(min = 2, max = 5) int queryCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < queryCount; i++) {
                ResultSet rs = stmt.executeQuery("SELECT " + i);
                assertNull(rs, "Query " + i + " should return null");
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeQuery"),
            "All " + queryCount + " queries should be absorbed");
        assertFalse(mockLog.contains("SELECT"),
            "SQL should not reach underlying driver");
    }

    /**
     * Property: Multiple executeUpdate calls all absorbed.
     */
    @Property(tries = 10)
    void multipleExecuteUpdateAbsorbed(
            @ForAll @IntRange(min = 2, max = 5) int updateCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < updateCount; i++) {
                int result = stmt.executeUpdate("INSERT INTO t VALUES (" + i + ")");
                assertEquals(0, result, "Update " + i + " should return 0");
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeUpdate"),
            "All " + updateCount + " updates should be absorbed");
        assertFalse(mockLog.contains("INSERT"),
            "SQL should not reach underlying driver");
    }

    /**
     * Property: Mixed operations all absorbed.
     */
    @Property(tries = 10)
    void mixedOperationsAbsorbed(
            @ForAll @IntRange(min = 1, max = 3) int operationsPerType) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;
        String sinkUrl = "jdbc:sink:" + mockUrl;

        try (Connection conn = DriverManager.getConnection(sinkUrl);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < operationsPerType; i++) {
                // executeQuery
                ResultSet rs = stmt.executeQuery("SELECT " + i);
                assertNull(rs);

                // executeUpdate
                int updateResult = stmt.executeUpdate("INSERT INTO t VALUES (" + i + ")");
                assertEquals(0, updateResult);

                // execute
                boolean execResult = stmt.execute("DELETE FROM t WHERE id = " + i);
                assertTrue(execResult);
            }
        }

        String mockLog = MockDriver.getLog(mockUrl);
        assertFalse(mockLog.contains("executeQuery"),
            "All queries should be absorbed");
        assertFalse(mockLog.contains("executeUpdate"),
            "All updates should be absorbed");
        assertFalse(mockLog.contains("execute["),
            "All executes should be absorbed");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> sqlStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "SELECT id, name FROM test",
            "SELECT COUNT(*) FROM t"
        );
    }

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1",
            "INSERT INTO users (name) VALUES ('test')"
        );
    }

    @Provide
    Arbitrary<String> allSqlStatements() {
        return Arbitraries.of(
            "SELECT 1",
            "SELECT * FROM users",
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1",
            "CREATE TABLE test (id INT)",
            "DROP TABLE test"
        );
    }
}
