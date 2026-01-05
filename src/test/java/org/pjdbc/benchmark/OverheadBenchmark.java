package org.pjdbc.benchmark;

import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Minimal, honest overhead benchmark for PJDBC.
 *
 * <p>This is NOT a comprehensive benchmark suite. It exists to:
 * <ol>
 *   <li>Prove that PJDBC overhead is negligible for typical queries</li>
 *   <li>Serve as a regression guard against accidental performance degradation</li>
 * </ol>
 *
 * <p>Run with: {@code mvn test -Dtest=OverheadBenchmark}
 *
 * <p><b>Honest context:</b> PJDBC adds microseconds of overhead to queries that
 * typically take milliseconds. If raw performance is critical, don't use proxy
 * layers at all.
 */
public class OverheadBenchmark {

    private static final int WARMUP_ITERATIONS = 500;
    private static final int MEASURE_ITERATIONS = 2000;

    // Thresholds: in-memory H2 amplifies overhead since query time is ~0.
    // Single driver: 100% overhead OK (doubles a 50μs query to 100μs)
    // Deep chain: 500% overhead OK (4 proxy layers on in-memory DB)
    // In real-world with 10ms network query, these become <1% and <5%
    private static final double MAX_SINGLE_DRIVER_OVERHEAD = 100.0;
    private static final double MAX_DEEP_CHAIN_OVERHEAD = 500.0;

    private static final String H2_URL = "jdbc:h2:mem:benchmark_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    private static long baselineNs;

    @BeforeClass
    public static void setup() throws Exception {
        // Load drivers
        Class.forName("org.h2.Driver");
        Class.forName("org.pjdbc.drivers.CatDriver");
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.TimeoutDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");

        // Setup test data
        try (Connection conn = DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test_data (id INT PRIMARY KEY, payload VARCHAR(100))");
            for (int i = 0; i < 100; i++) {
                stmt.execute("INSERT INTO test_data VALUES (" + i + ", 'payload" + i + "')");
            }
        }

        // JVM warmup: run all configurations once to trigger JIT
        warmupJvm();

        // Measure baseline (after warmup)
        baselineNs = measureQuery(H2_URL);

        System.out.println();
        System.out.println("PJDBC Overhead Benchmark (regression guard)");
        System.out.println("============================================");
        System.out.printf("Baseline (direct H2): %.1f μs/query%n", baselineNs / 1000.0);
        System.out.println("(In-memory DB amplifies overhead; real queries take 1-100ms)");
        System.out.println();
    }

    private static void warmupJvm() throws SQLException {
        String[] urls = {
            H2_URL,
            "jdbc:cat:" + H2_URL,
            "jdbc:retry:" + H2_URL,
            "jdbc:timeout:" + H2_URL,
            "jdbc:readonly:" + H2_URL,
            "jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:cat:" + H2_URL
        };
        for (String url : urls) {
            measureQuery(url); // Discard results, just warming up
        }
    }

    @Test
    public void catDriverOverhead() throws Exception {
        assertOverheadAcceptable("jdbc:cat:" + H2_URL, "cat", MAX_SINGLE_DRIVER_OVERHEAD);
    }

    @Test
    public void readonlyDriverOverhead() throws Exception {
        assertOverheadAcceptable("jdbc:readonly:" + H2_URL, "readonly", MAX_SINGLE_DRIVER_OVERHEAD);
    }

    @Test
    public void retryDriverOverhead() throws Exception {
        assertOverheadAcceptable("jdbc:retry:" + H2_URL, "retry", MAX_SINGLE_DRIVER_OVERHEAD);
    }

    @Test
    public void timeoutDriverOverhead() throws Exception {
        assertOverheadAcceptable("jdbc:timeout:" + H2_URL, "timeout", MAX_SINGLE_DRIVER_OVERHEAD);
    }

    @Test
    public void deepChainOverhead() throws Exception {
        String url = "jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:cat:" + H2_URL;
        assertOverheadAcceptable(url, "deep chain (4 drivers)", MAX_DEEP_CHAIN_OVERHEAD);
    }

    private void assertOverheadAcceptable(String url, String name, double maxOverhead) throws Exception {
        long driverNs = measureQuery(url);
        double overheadPercent = ((double)(driverNs - baselineNs) / baselineNs) * 100;

        System.out.printf("  %-25s %6.1f μs  (%+.1f%%)%n", name, driverNs / 1000.0, overheadPercent);

        assertTrue(
            String.format("%s overhead %.1f%% exceeds threshold %.1f%%",
                name, overheadPercent, maxOverhead),
            overheadPercent <= maxOverhead
        );
    }

    private static long measureQuery(String url) throws SQLException {
        String sql = "SELECT * FROM test_data WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                pstmt.setInt(1, i % 100);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        rs.getInt("id");
                        rs.getString("payload");
                    }
                }
            }

            // Measure
            long start = System.nanoTime();
            for (int i = 0; i < MEASURE_ITERATIONS; i++) {
                pstmt.setInt(1, i % 100);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        rs.getInt("id");
                        rs.getString("payload");
                    }
                }
            }
            long elapsed = System.nanoTime() - start;

            return elapsed / MEASURE_ITERATIONS;
        }
    }
}
