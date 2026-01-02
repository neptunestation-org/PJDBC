package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;
import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for driver composition laws.
 *
 * These tests verify algebraic properties of driver composition:
 * - Identity: cat:X behaves like X
 * - Idempotence: cat:cat:X behaves like cat:X
 * - Passthrough chains preserve SQL
 * - Sink absorbs operations
 */
class DriverCompositionProperties {

    @BeforeProperty
    void clearMockLogs() {
        MockDriver.clearLogs();
    }

    // ========== IDENTITY LAWS ==========

    /**
     * Identity Law: jdbc:cat:X should behave identically to X
     *
     * The CatDriver is a pure pass-through that should not modify
     * any behavior of the wrapped connection.
     */
    @Property(tries = 50)
    void catDriverIsIdentity(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        // Execute against direct mock connection
        try (Connection directConn = DriverManager.getConnection(mockUrl)) {
            directConn.createStatement().executeQuery(sql);
        }
        String directLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // Execute against cat-wrapped connection
        String catUrl = "jdbc:cat:" + mockUrl;
        try (Connection catConn = DriverManager.getConnection(catUrl)) {
            catConn.createStatement().executeQuery(sql);
        }
        String catLog = MockDriver.getLog(mockUrl);

        assertEquals(directLog, catLog,
            "CatDriver should not modify behavior: " + catUrl);
    }

    /**
     * Idempotence: cat:cat:X should behave like cat:X
     *
     * Multiple cat wrappers should have no cumulative effect.
     */
    @Property(tries = 50)
    void catDriverIsIdempotent(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql,
            @ForAll @IntRange(min = 1, max = 5) int catDepth) throws SQLException {

        MockDriver.clearLogs();

        // Single cat wrapper
        String singleCatUrl = "jdbc:cat:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(singleCatUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String singleLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // Multiple cat wrappers
        StringBuilder multiCatUrl = new StringBuilder();
        for (int i = 0; i < catDepth; i++) {
            multiCatUrl.append("jdbc:cat:");
        }
        multiCatUrl.append(mockUrl);

        try (Connection conn = DriverManager.getConnection(multiCatUrl.toString())) {
            conn.createStatement().executeQuery(sql);
        }
        String multiLog = MockDriver.getLog(mockUrl);

        assertEquals(singleLog, multiLog,
            "Multiple CatDrivers should be idempotent: " + multiCatUrl);
    }

    // ========== PASSTHROUGH COMPOSITION ==========

    /**
     * Log driver preserves data integrity.
     *
     * Log driver should pass through SQL unchanged while logging.
     */
    @Property(tries = 50)
    void logDriverPreservesData(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        // Execute directly
        try (Connection directConn = DriverManager.getConnection(mockUrl)) {
            directConn.createStatement().executeQuery(sql);
        }
        String directLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // Execute through log driver
        String logUrl = "jdbc:log:" + mockUrl;
        try (Connection logConn = DriverManager.getConnection(logUrl)) {
            logConn.createStatement().executeQuery(sql);
        }
        String loggedLog = MockDriver.getLog(mockUrl);

        assertEquals(directLog, loggedLog,
            "LogDriver should preserve SQL: " + sql);
    }

    /**
     * Passthrough driver chains preserve data.
     *
     * Any chain of cat and log drivers should not modify the SQL
     * that reaches the terminal MockDriver.
     */
    @Property(tries = 30)
    void passthroughChainsPreserveData(
            @ForAll("passthroughChains") String chainUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        // Extract the terminal mock URL
        String mockUrl = extractMockUrl(chainUrl);

        MockDriver.clearLogs();

        // Execute through chain
        try (Connection conn = DriverManager.getConnection(chainUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String chainLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // Execute directly
        try (Connection directConn = DriverManager.getConnection(mockUrl)) {
            directConn.createStatement().executeQuery(sql);
        }
        String directLog = MockDriver.getLog(mockUrl);

        assertEquals(directLog, chainLog,
            "Passthrough chain should preserve SQL. Chain: " + chainUrl);
    }

    // ========== SINK DRIVER PROPERTIES ==========

    /**
     * Sink driver absorbs all operations.
     *
     * Operations through sink should not reach the terminal driver.
     */
    @Property(tries = 50)
    void sinkDriverAbsorbsOperations(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        String sinkUrl = "jdbc:sink:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(sinkUrl)) {
            Statement stmt = conn.createStatement();
            stmt.executeQuery(sql);
            stmt.executeUpdate("INSERT INTO test VALUES (1)");
        }

        String log = MockDriver.getLog(mockUrl);
        assertEquals("", log,
            "SinkDriver should absorb all operations");
    }

    /**
     * Sink is idempotent with cat.
     *
     * cat:sink:X and sink:X should both absorb operations.
     */
    @Property(tries = 30)
    void sinkCatCompositionAbsorbs(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        MockDriver.clearLogs();

        // sink:X
        String sinkUrl = "jdbc:sink:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(sinkUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String sinkLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // cat:sink:X
        String catSinkUrl = "jdbc:cat:jdbc:sink:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(catSinkUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String catSinkLog = MockDriver.getLog(mockUrl);

        assertEquals(sinkLog, catSinkLog,
            "cat:sink:X should behave like sink:X");
        assertEquals("", sinkLog,
            "Both should absorb operations");
    }

    // ========== CONNECTION LIFECYCLE ==========

    /**
     * Connections through any valid chain can be established and closed.
     */
    @Property(tries = 100)
    void connectionLifecycleWorks(
            @ForAll("composableChains") String chainUrl) throws SQLException {

        Connection conn = DriverManager.getConnection(chainUrl);
        assertNotNull(conn, "Should get valid connection for: " + chainUrl);
        assertFalse(conn.isClosed(), "New connection should not be closed");

        conn.close();
    }

    /**
     * Statements can be created through any valid chain.
     */
    @Property(tries = 50)
    void statementCreationWorks(
            @ForAll("composableChains") String chainUrl) throws SQLException {

        try (Connection conn = DriverManager.getConnection(chainUrl)) {
            Statement stmt = conn.createStatement();
            assertNotNull(stmt, "Should get valid statement for: " + chainUrl);
        }
    }

    // ========== HELPER METHODS ==========

    private String extractMockUrl(String chainUrl) {
        int mockIndex = chainUrl.lastIndexOf("jdbc:mock:");
        if (mockIndex >= 0) {
            return chainUrl.substring(mockIndex);
        }
        throw new IllegalArgumentException("No mock URL found in: " + chainUrl);
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> mockUrls() {
        return PjdbcArbitraries.mockUrls();
    }

    @Provide
    Arbitrary<String> sqlStatements() {
        return PjdbcArbitraries.sqlStatements();
    }

    @Provide
    Arbitrary<String> passthroughChains() {
        return PjdbcArbitraries.passthroughChains(1, 4);
    }

    @Provide
    Arbitrary<String> composableChains() {
        return PjdbcArbitraries.composableChains(0, 3);
    }
}
