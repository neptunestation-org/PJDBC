package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.*;
import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for TeeDriver composition.
 *
 * TeeDriver splits operations to two targets, enabling:
 * - Broadcast properties (both targets receive operations)
 * - Sink/live composition patterns
 */
class TeeDriverProperties {

    @BeforeProperty
    void clearMockLogs() {
        MockDriver.clearLogs();
    }

    /**
     * TeeDriver broadcasts to both targets.
     *
     * Both branches should receive the same operations.
     */
    @Property(tries = 30)
    void teeBroadcastsToBothTargets(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2)); // Ensure different databases

        MockDriver.clearLogs();

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;

        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log1 = MockDriver.getLog(url1);
        String log2 = MockDriver.getLog(url2);

        assertEquals(log1, log2,
            "Tee should broadcast same operations to both targets");
        assertTrue(log1.contains("executeQuery"),
            "Both targets should have received executeQuery");
    }

    /**
     * Tee with sink on one branch isolates the other.
     *
     * Pattern: jdbc:tee:jdbc:mock:live;jdbc:sink:jdbc:mock:dead
     * Only the live branch should record operations.
     */
    @Property(tries = 30)
    void teeWithSinkIsolatesTarget(
            @ForAll("mockDbNames") String liveDb,
            @ForAll("mockDbNames") String deadDb,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!liveDb.equals(deadDb));

        MockDriver.clearLogs();

        String liveUrl = "jdbc:mock:" + liveDb;
        String deadUrl = "jdbc:mock:" + deadDb;
        String teeUrl = "jdbc:tee:" + liveUrl + ";jdbc:sink:" + deadUrl;

        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String liveLog = MockDriver.getLog(liveUrl);
        String deadLog = MockDriver.getLog(deadUrl);

        assertFalse(liveLog.isEmpty(),
            "Live branch should receive operations");
        assertEquals("", deadLog,
            "Sink branch should absorb operations");
    }

    /**
     * Tee order doesn't affect broadcast semantics.
     *
     * jdbc:tee:A;B and jdbc:tee:B;A should both broadcast to A and B.
     */
    @Property(tries = 20)
    void teeOrderDoesNotAffectBroadcast(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2));

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        // First order: db1;db2
        MockDriver.clearLogs();
        String teeUrl1 = "jdbc:tee:" + url1 + ";" + url2;
        try (Connection conn = DriverManager.getConnection(teeUrl1)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_order1 = MockDriver.getLog(url1);
        String log2_order1 = MockDriver.getLog(url2);

        // Second order: db2;db1
        MockDriver.clearLogs();
        String teeUrl2 = "jdbc:tee:" + url2 + ";" + url1;
        try (Connection conn = DriverManager.getConnection(teeUrl2)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_order2 = MockDriver.getLog(url1);
        String log2_order2 = MockDriver.getLog(url2);

        assertEquals(log1_order1, log1_order2,
            "db1 should receive same operations regardless of tee order");
        assertEquals(log2_order1, log2_order2,
            "db2 should receive same operations regardless of tee order");
    }

    /**
     * Cat wrapper around tee preserves broadcast behavior.
     *
     * jdbc:cat:jdbc:tee:A;B should behave like jdbc:tee:A;B
     */
    @Property(tries = 20)
    void catPreservesTee(
            @ForAll("mockDbNames") String db1,
            @ForAll("mockDbNames") String db2,
            @ForAll("sqlStatements") String sql) throws SQLException {

        Assume.that(!db1.equals(db2));

        String url1 = "jdbc:mock:" + db1;
        String url2 = "jdbc:mock:" + db2;

        // Direct tee
        MockDriver.clearLogs();
        String teeUrl = "jdbc:tee:" + url1 + ";" + url2;
        try (Connection conn = DriverManager.getConnection(teeUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_tee = MockDriver.getLog(url1);
        String log2_tee = MockDriver.getLog(url2);

        // Cat wrapped tee
        MockDriver.clearLogs();
        String catTeeUrl = "jdbc:cat:" + teeUrl;
        try (Connection conn = DriverManager.getConnection(catTeeUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String log1_cat = MockDriver.getLog(url1);
        String log2_cat = MockDriver.getLog(url2);

        assertEquals(log1_tee, log1_cat, "cat:tee should preserve branch 1");
        assertEquals(log2_tee, log2_cat, "cat:tee should preserve branch 2");
    }

    @Provide
    Arbitrary<String> mockDbNames() {
        return PjdbcArbitraries.mockDbNames();
    }

    @Provide
    Arbitrary<String> sqlStatements() {
        return PjdbcArbitraries.sqlStatements();
    }
}
