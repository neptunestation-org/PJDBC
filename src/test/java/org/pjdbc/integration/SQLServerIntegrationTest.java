package org.pjdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.Properties;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests verifying PJDBC drivers work correctly with SQL Server.
 * Uses Microsoft's official SQL Server Docker image under Developer Edition license.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SQLServerIntegrationTest {

    @Container
    static MSSQLServerContainer<?> sqlserver = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
        .acceptLicense()
        .withReuse(true);

    @BeforeAll
    static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.CatDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.SinkDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private String getJdbcUrlWithCredentials() {
        // SQL Server uses semicolon-separated parameters
        // Include credentials and trust certificate for test containers
        return sqlserver.getJdbcUrl() +
            ";user=" + sqlserver.getUsername() +
            ";password=" + sqlserver.getPassword() +
            ";trustServerCertificate=true";
    }

    private void setupTestTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // SQL Server syntax for drop if exists
            stmt.execute("IF OBJECT_ID('users', 'U') IS NOT NULL DROP TABLE users");
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), ssn VARCHAR(20))");
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
        // Chain: readonly -> retry -> sqlserver
        String url = "jdbc:readonly:jdbc:retry:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // SQL Server uses TOP instead of LIMIT
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT TOP 1 name FROM users ORDER BY id")) {
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
        String url = "jdbc:cat:" + getJdbcUrlWithCredentials();
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

    // === SQL Server-Specific Tests ===

    @Test
    @Order(14)
    void sqlServerSpecificFeaturesWork() throws SQLException {
        String url = "jdbc:cat:" + getJdbcUrlWithCredentials();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Test SQL Server-specific syntax (MERGE statement)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("MERGE INTO users AS target " +
                    "USING (SELECT 1 AS id, 'Alice Merged' AS name, 'alice3@example.com' AS email, '222-22-2222' AS ssn) AS source " +
                    "ON target.id = source.id " +
                    "WHEN MATCHED THEN UPDATE SET name = source.name, email = source.email, ssn = source.ssn " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, email, ssn) VALUES (source.id, source.name, source.email, source.ssn);");
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Alice Merged", rs.getString(1));
            }
        }
    }
}
