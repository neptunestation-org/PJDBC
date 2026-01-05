package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;

import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ReadonlyDriver.
 *
 * These tests verify read-only enforcement properties:
 * - SELECT queries are always allowed
 * - DML operations are blocked by default
 * - DDL operations are blocked by default
 * - DCL operations are always blocked
 * - allowDML/allowDDL parameters permit operations
 * - Driver composition
 */
class ReadonlyDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "readonly_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private void setupTestTable(String dbName) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("DELETE FROM users");
                stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
                stmt.execute("INSERT INTO users VALUES (2, 'Bob')");
            }
        }
    }

    // ========== SELECT QUERIES ALWAYS ALLOWED ==========

    /**
     * Property: SELECT queries should always be allowed through ReadonlyDriver.
     */
    @Property(tries = 30)
    void selectQueriesAreAllowed(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Should not throw - SELECT is always allowed
            assertNotNull(rs, "SELECT should return a ResultSet");
        }
    }

    /**
     * Property: SELECT queries work even with strictest readonly settings.
     */
    @Property(tries = 20)
    void selectAllowedEvenWithAllBlocked(
            @ForAll("selectStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        // Explicit allowDML=false, allowDDL=false (defaults anyway)
        String readonlyUrl = "jdbc:readonly[allowDML=false,allowDDL=false]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertNotNull(rs, "SELECT should still work with all writes blocked");
        }
    }

    // ========== DML BLOCKED BY DEFAULT ==========

    /**
     * Property: DML operations (INSERT, UPDATE, DELETE, etc.) throw SQLException by default.
     */
    @Property(tries = 30)
    void dmlBlockedByDefault(
            @ForAll("dmlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.executeUpdate(sql),
                "DML should be blocked: " + sql);

            assertTrue(ex.getMessage().contains("DML blocked"),
                "Error message should indicate DML blocked: " + ex.getMessage());
        }
    }

    /**
     * Property: DML is blocked at prepareStatement time too.
     */
    @Property(tries = 20)
    void dmlBlockedAtPrepareTime(
            @ForAll("dmlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl)) {
            SQLException ex = assertThrows(SQLException.class,
                () -> conn.prepareStatement(sql),
                "DML should be blocked at prepare time: " + sql);

            assertTrue(ex.getMessage().contains("DML blocked"),
                "Error message should indicate DML blocked");
        }
    }

    // ========== DDL BLOCKED BY DEFAULT ==========

    /**
     * Property: DDL operations (CREATE, ALTER, DROP, RENAME) throw SQLException by default.
     */
    @Property(tries = 20)
    void ddlBlockedByDefault(
            @ForAll("ddlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.execute(sql),
                "DDL should be blocked: " + sql);

            assertTrue(ex.getMessage().contains("DDL blocked"),
                "Error message should indicate DDL blocked: " + ex.getMessage());
        }
    }

    // ========== DCL ALWAYS BLOCKED ==========

    /**
     * Property: DCL operations (GRANT, REVOKE) are always blocked, even with allowDML and allowDDL.
     */
    @Property(tries = 10)
    void dclAlwaysBlocked(
            @ForAll("dclStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        // Even with everything allowed, DCL should still be blocked
        String readonlyUrl = "jdbc:readonly[allowDML=true,allowDDL=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.execute(sql),
                "DCL should always be blocked: " + sql);

            assertTrue(ex.getMessage().contains("DCL blocked"),
                "Error message should indicate DCL blocked: " + ex.getMessage());
        }
    }

    // ========== allowDML PERMITS DML ==========

    /**
     * Property: With allowDML=true, DML operations are permitted.
     */
    @Property(tries = 20)
    void allowDmlPermitsDml(
            @ForAll @IntRange(min = 10, max = 20) int newId) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly[allowDML=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            // INSERT should work
            int inserted = stmt.executeUpdate("INSERT INTO users VALUES (" + newId + ", 'New')");
            assertEquals(1, inserted, "INSERT should succeed with allowDML=true");

            // UPDATE should work
            int updated = stmt.executeUpdate("UPDATE users SET name = 'Updated' WHERE id = " + newId);
            assertEquals(1, updated, "UPDATE should succeed with allowDML=true");

            // DELETE should work
            int deleted = stmt.executeUpdate("DELETE FROM users WHERE id = " + newId);
            assertEquals(1, deleted, "DELETE should succeed with allowDML=true");
        }
    }

    /**
     * Property: allowDML=true still blocks DDL.
     */
    @Property(tries = 10)
    void allowDmlStillBlocksDdl(
            @ForAll("ddlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly[allowDML=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.execute(sql),
                "DDL should still be blocked with only allowDML=true");

            assertTrue(ex.getMessage().contains("DDL blocked"),
                "Should indicate DDL blocked");
        }
    }

    // ========== allowDDL PERMITS DDL ==========

    /**
     * Property: With allowDDL=true, DDL operations are permitted.
     */
    @Property(tries = 10)
    void allowDdlPermitsDdl(
            @ForAll @IntRange(min = 1, max = 100) int tableNum) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String tableName = "test_table_" + tableNum;

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            // CREATE should work
            stmt.execute("CREATE TABLE " + tableName + " (id INT)");

            // ALTER should work
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN name VARCHAR(50)");

            // DROP should work
            stmt.execute("DROP TABLE " + tableName);
        }
    }

    /**
     * Property: allowDDL=true still blocks DML.
     */
    @Property(tries = 10)
    void allowDdlStillBlocksDml(
            @ForAll("dmlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {

            SQLException ex = assertThrows(SQLException.class,
                () -> stmt.executeUpdate(sql),
                "DML should still be blocked with only allowDDL=true");

            assertTrue(ex.getMessage().contains("DML blocked"),
                "Should indicate DML blocked");
        }
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * Property: cat:readonly:X behaves like readonly:X (cat is identity).
     */
    @Property(tries = 20)
    void catReadonlyComposition(
            @ForAll("dmlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyUrl = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String catReadonlyUrl = "jdbc:cat:jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        // Both should block DML
        try (Connection conn = DriverManager.getConnection(readonlyUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeUpdate(sql));
        }

        try (Connection conn = DriverManager.getConnection(catReadonlyUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeUpdate(sql),
                "cat:readonly should still block DML");
        }
    }

    /**
     * Property: readonly:cat:X behaves like readonly:X.
     */
    @Property(tries = 20)
    void readonlyCatComposition(
            @ForAll("dmlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String readonlyCatUrl = "jdbc:readonly:jdbc:cat:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(readonlyCatUrl);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> stmt.executeUpdate(sql),
                "readonly:cat should block DML");
        }
    }

    /**
     * Property: log:readonly:X preserves readonly enforcement.
     */
    @Property(tries = 20)
    void catReadonlyComposition(
            @ForAll("selectStatements") String selectSql,
            @ForAll("dmlStatements") String dmlSql) throws SQLException {

        String dbName = createUniqueDbName();
        setupTestTable(dbName);

        String catReadonlyUrl = "jdbc:cat:jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(catReadonlyUrl);
             Statement stmt = conn.createStatement()) {

            // SELECT should work
            try (ResultSet rs = stmt.executeQuery(selectSql)) {
                assertNotNull(rs);
            }

            // DML should be blocked
            assertThrows(SQLException.class, () -> stmt.executeUpdate(dmlSql),
                "cat:readonly should still block DML");
        }
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> selectStatements() {
        return Arbitraries.of(
            "SELECT * FROM users",
            "SELECT id FROM users",
            "SELECT name FROM users WHERE id = 1",
            "SELECT COUNT(*) FROM users",
            "SELECT id, name FROM users ORDER BY id"
        );
    }

    @Provide
    Arbitrary<String> dmlStatements() {
        return Arbitraries.of(
            "INSERT INTO users VALUES (99, 'Test')",
            "UPDATE users SET name = 'Updated' WHERE id = 1",
            "DELETE FROM users WHERE id = 1",
            "MERGE INTO users KEY(id) VALUES (1, 'Merged')",
            "TRUNCATE TABLE users"
        );
    }

    @Provide
    Arbitrary<String> ddlStatements() {
        return Arbitraries.of(
            "CREATE TABLE temp_test (id INT)",
            "ALTER TABLE users ADD COLUMN temp VARCHAR(10)",
            "DROP TABLE IF EXISTS nonexistent",
            "CREATE INDEX idx_test ON users(name)"
        );
    }

    @Provide
    Arbitrary<String> dclStatements() {
        return Arbitraries.of(
            "GRANT SELECT ON users TO PUBLIC",
            "REVOKE SELECT ON users FROM PUBLIC"
        );
    }
}
