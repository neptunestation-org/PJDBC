package org.pjdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests verifying PJDBC drivers work correctly with Oracle.
 * Uses Oracle XE (Express Edition) which is free for development/testing.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OracleIntegrationTest {

    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
        .withReuse(true);

    @BeforeAll
    static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CatDriver");
        Class.forName("org.pjdbc.drivers.LogDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.PoolDriver");
        Class.forName("org.pjdbc.drivers.SinkDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.CachingDriver");
        Class.forName("org.pjdbc.drivers.MetricsDriver");
        Class.forName("org.pjdbc.drivers.TracingDriver");
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
        Class.forName("org.pjdbc.drivers.HikariPoolDriver");
    }

    private String getJdbcUrlWithCredentials() {
        // Oracle URL with credentials
        String baseUrl = oracle.getJdbcUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "user=" + oracle.getUsername() + "&password=" + oracle.getPassword();
    }

    private void setupTestTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Oracle syntax for drop if exists (using exception handling in PL/SQL or checking)
            try {
                stmt.execute("DROP TABLE users");
            } catch (SQLException e) {
                // Table doesn't exist, ignore
            }
            stmt.execute("CREATE TABLE users (id NUMBER(10) PRIMARY KEY, name VARCHAR2(100), email VARCHAR2(100), ssn VARCHAR2(20))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', '123-45-6789')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', '987-65-4321')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', '555-55-5555')");
        }
    }

    // === CatDriver Tests ===

    @Test
    @Order(1)
    void catDriverPassthrough() throws SQLException {
        String url = "jdbc:cat:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            setupTestTable(conn);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }

    // === LogDriver Tests ===

    @Test
    @Order(2)
    void logDriverLogsQueries() throws SQLException {
        String url = "jdbc:log:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString(1));
            }
        }
    }

    // === PoolDriver Tests ===

    @Test
    @Order(3)
    void poolDriverMaintainsConnections() throws SQLException {
        String url = "jdbc:pool[min=1,max=3]:" + getJdbcUrlWithCredentials();
        Connection conn1 = DriverManager.getConnection(url);
        Connection conn2 = DriverManager.getConnection(url);

        assertNotNull(conn1);
        assertNotNull(conn2);
        assertFalse(conn1.isClosed());
        assertFalse(conn2.isClosed());

        try (Statement stmt = conn1.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        conn1.close();
        conn2.close();
    }

    // === HikariPoolDriver Tests ===

    @Test
    @Order(4)
    void hikariPoolDriverWorks() throws SQLException {
        String url = "jdbc:hikaricp:" + getJdbcUrlWithCredentials() + "&maximumPoolSize=2";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 42 AS answer FROM DUAL")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("ANSWER"));
            }
        }
    }

    // === ReadonlyDriver Tests ===

    @Test
    @Order(5)
    void readonlyDriverBlocksWrites() throws SQLException {
        String url = "jdbc:readonly:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Reads should work
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
            }

            // Writes should be blocked
            try (Statement stmt = conn.createStatement()) {
                assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("INSERT INTO users VALUES (99, 'Blocked', 'blocked@test.com', '000-00-0000')"));
            }
        }
    }

    // === RetryDriver Tests ===

    @Test
    @Order(6)
    void retryDriverSucceedsOnValidQuery() throws SQLException {
        String url = "jdbc:retry[maxRetries=2]:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString(1));
            }
        }
    }

    // === CachingDriver Tests ===

    @Test
    @Order(7)
    void cachingDriverCachesResults() throws SQLException {
        String url = "jdbc:cache[ttl=60]:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // First query - cache miss
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }

            // Second identical query - should hit cache
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }

    // === MetricsDriver Tests ===

    @Test
    @Order(8)
    void metricsDriverCollectsStats() throws SQLException {
        String url = "jdbc:metrics:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1 FROM DUAL").close();
                stmt.executeQuery("SELECT 2 FROM DUAL").close();
                stmt.executeUpdate("UPDATE users SET name = 'Alice' WHERE id = 1");
            }

            var metrics = org.pjdbc.drivers.MetricsDriver.getMetrics(conn);
            assertNotNull(metrics);
            assertTrue(metrics.getTotalQueries() >= 2);
            assertTrue(metrics.getTotalUpdates() >= 1);
        }
    }

    // === TracingDriver Tests ===

    @Test
    @Order(9)
    void tracingDriverCreatesSpans() throws SQLException {
        String url = "jdbc:trace:" + getJdbcUrlWithCredentials();
        org.pjdbc.drivers.TracingDriver.getDefaultTracer().clear();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Oracle uses FETCH FIRST or ROWNUM for limiting rows
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE ROWNUM = 1")) {
                assertTrue(rs.next());
            }
        }

        var spans = org.pjdbc.drivers.TracingDriver.getDefaultTracer().getSpans();
        assertFalse(spans.isEmpty());
    }

    // === DataMaskingDriver Tests ===

    @Test
    @Order(10)
    void dataMaskingDriverMasksSensitiveData() throws SQLException {
        String url = "jdbc:mask[columns=ssn,strategy=PARTIAL,showLast=4]:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                String maskedSsn = rs.getString(1);
                // Should end with last 4 chars visible
                assertTrue(maskedSsn.endsWith("6789"));
                // Should have masking characters
                assertTrue(maskedSsn.contains("*"));
            }
        }
    }

    // === Driver Chaining Tests ===

    @Test
    @Order(11)
    void driverChainingWorks() throws SQLException {
        // Chain: log -> cache -> pool -> oracle
        String url = "jdbc:log:jdbc:cache:jdbc:pool:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Oracle uses FETCH FIRST for limiting rows (12c+)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY id FETCH FIRST 1 ROWS ONLY")) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString(1));
            }
        }
    }

    // === Prepared Statement Tests ===

    @Test
    @Order(12)
    void preparedStatementsWorkThroughProxy() throws SQLException {
        String url = "jdbc:cat:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name FROM users WHERE id = ?")) {
                pstmt.setInt(1, 2);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Bob", rs.getString(1));
                }
            }
        }
    }

    // === Transaction Tests ===

    @Test
    @Order(13)
    void transactionsWorkThroughProxy() throws SQLException {
        String url = "jdbc:log:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET name = 'Alice Updated' WHERE id = 1");
                conn.rollback();
            }

            // Verify rollback worked
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString(1));
            }
        }
    }

    // === Oracle-Specific Tests ===

    @Test
    @Order(14)
    void oracleSpecificFeaturesWork() throws SQLException {
        String url = "jdbc:cat:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Test Oracle-specific syntax (MERGE statement)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("MERGE INTO users dest " +
                    "USING (SELECT 1 AS id, 'Alice Merged' AS name, 'alice3@example.com' AS email, '222-22-2222' AS ssn FROM DUAL) src " +
                    "ON (dest.id = src.id) " +
                    "WHEN MATCHED THEN UPDATE SET dest.name = src.name, dest.email = src.email, dest.ssn = src.ssn " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, email, ssn) VALUES (src.id, src.name, src.email, src.ssn)");
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Alice Merged", rs.getString(1));
            }
        }
    }
}
