package org.pjdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;

import org.junit.jupiter.api.*;

/**
 * Integration tests verifying PJDBC drivers work correctly with SQLite.
 * Uses in-memory SQLite database (no Docker required).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SQLiteIntegrationTest {

    private static final String SQLITE_URL = "jdbc:sqlite::memory:";

    // SQLite in-memory DB is per-connection, so we need a shared connection for tests
    // that depend on previous test state, or use file-based for persistence
    private static final String SQLITE_FILE_URL = "jdbc:sqlite:target/test.db";

    @BeforeAll
    static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        Class.forName("org.pjdbc.drivers.CatDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.SinkDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.RetryDriver");
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @BeforeAll
    static void setupDatabase() throws SQLException {
        // Create file-based database with test data
        try (Connection conn = DriverManager.getConnection(SQLITE_FILE_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT, ssn TEXT)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', '123-45-6789')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', '987-65-4321')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', '555-55-5555')");
        }
    }

    @AfterAll
    static void cleanup() {
        // Clean up test database file
        new java.io.File("target/test.db").delete();
    }

    // === CatDriver Tests ===

    @Test
    @Order(1)
    void catDriverPassthrough() throws SQLException {
        String url = "jdbc:cat:" + SQLITE_FILE_URL;
        try (Connection conn = DriverManager.getConnection(url)) {
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
        String url = "jdbc:readonly:" + SQLITE_FILE_URL;
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
        String url = "jdbc:retry[maxRetries=2]:" + SQLITE_FILE_URL;
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
        String url = "jdbc:mask[columns=ssn,strategy=PARTIAL,showLast=4]:" + SQLITE_FILE_URL;
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
        // Chain: readonly -> retry -> sqlite
        String url = "jdbc:readonly:jdbc:retry:" + SQLITE_FILE_URL;
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY id LIMIT 1")) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString(1));
            }
        }
    }

    // === Prepared Statement Tests ===

    @Test
    @Order(12)
    void preparedStatementsWorkThroughProxy() throws SQLException {
        String url = "jdbc:cat:" + SQLITE_FILE_URL;
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
        String url = "jdbc:cat:" + SQLITE_FILE_URL;
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

    // === SQLite-Specific Tests ===

    @Test
    @Order(14)
    void sqliteSpecificFeaturesWork() throws SQLException {
        String url = "jdbc:cat:" + SQLITE_FILE_URL;
        try (Connection conn = DriverManager.getConnection(url)) {
            // Test SQLite-specific syntax (INSERT OR REPLACE)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT OR REPLACE INTO users VALUES (1, 'Alice Replaced', 'alice2@example.com', '111-11-1111')");
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Alice Replaced", rs.getString(1));
            }

            // Restore original value for other tests
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE users SET name = 'Alice', email = 'alice@example.com', ssn = '123-45-6789' WHERE id = 1");
            }
        }
    }

    // === In-Memory Database Tests ===

    @Test
    @Order(15)
    void inMemoryDatabaseWorks() throws SQLException {
        // Test with in-memory database (each connection gets fresh DB)
        String url = "jdbc:cat:" + SQLITE_URL;
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, value TEXT)");
                stmt.execute("INSERT INTO test VALUES (1, 'hello')");
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT value FROM test WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("hello", rs.getString(1));
            }
        }
    }
}
