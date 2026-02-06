package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaValidationBypassTest {

    private String dbName;
    private Connection setupConn;
    private Connection proxiedConn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.h2.Driver");

        dbName = "bypass_" + UUID.randomUUID().toString().replace("-", "");

        // Setup base database
        setupConn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        try (Statement stmt = setupConn.createStatement()) {
            stmt.execute("CREATE TABLE allowed (id INT)");
            stmt.execute("CREATE TABLE blocked (id INT)");
            stmt.execute("CREATE TABLE \"quoted_blocked\" (id INT)");
        }

        // Connect via proxy
        proxiedConn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=allowed]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1"
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (proxiedConn != null) proxiedConn.close();
        if (setupConn != null) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("SHUTDOWN");
            }
            setupConn.close();
        }
    }

    @Test
    void testQuotedIdentifierBlocked() throws SQLException {
        try (Statement stmt = proxiedConn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM \"quoted_blocked\"")
            );
            assertTrue(ex.getMessage().contains("quoted_blocked"), "Error message should contain table name: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("not in allowed tables list"), "Should be blocked by validation");
        }
    }

    @Test
    void testCommaSeparatedTablesBlocked() throws SQLException {
        try (Statement stmt = proxiedConn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM allowed, blocked")
            );
            assertTrue(ex.getMessage().contains("blocked"), "Error message should contain table name: " + ex.getMessage());
        }
    }

    @Test
    void testSubqueryBlocked() throws SQLException {
        try (Statement stmt = proxiedConn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM (SELECT * FROM blocked)")
            );
            assertTrue(ex.getMessage().contains("blocked"), "Error message should contain table name: " + ex.getMessage());
        }
    }

    @Test
    void testQuotedColumnBlocked() throws SQLException {
        Connection colConn = DriverManager.getConnection(
            "jdbc:schema[blockedColumns=ssn]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1"
        );
        try (Statement stmt = colConn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT \"ssn\" FROM allowed")
            );
            assertTrue(ex.getMessage().contains("ssn"), "Error message should contain column name: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("is blocked"), "Should be blocked by validation");
        } finally {
            colConn.close();
        }
    }
}
