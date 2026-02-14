package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaValidationBypassTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.h2.Driver");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void bypassWithComment() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1")) {
                setup.createStatement().execute("CREATE TABLE secrets (id INT, val VARCHAR(100))");
            }
            assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT * FROM secrets");
            });
            // Should now also be blocked
            assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT * FROM/**/secrets");
            }, "Bypass with comment should be blocked");
        }
    }

    @Test
    void bypassWithQuotes() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1")) {
                setup.createStatement().execute("CREATE TABLE secrets (id INT, val VARCHAR(100))");
            }
            // Should now be blocked by SchemaValidationDriver, not H2
            assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT * FROM \"secrets\"");
            }, "Bypass with quotes should be blocked");
        }
    }

    @Test
    void bypassWithMultipleTables() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:bypass3;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass3;DB_CLOSE_DELAY=-1")) {
                setup.createStatement().execute("CREATE TABLE allowed_table (id INT)");
                setup.createStatement().execute("CREATE TABLE secrets (id INT, val VARCHAR(100))");
            }

            // Should now be blocked because 'secrets' is present
            assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT * FROM allowed_table, secrets");
            }, "Bypass with multiple tables should be blocked");
        }
    }

    @Test
    void bypassWithMultipleUpdateColumns() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[blockedColumns=password,mode=blacklist]:jdbc:h2:mem:bypass4;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass4;DB_CLOSE_DELAY=-1")) {
                setup.createStatement().execute("CREATE TABLE users (id INT, name VARCHAR(100), password VARCHAR(100))");
            }

            // Should now be blocked because 'password' is being updated
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("UPDATE users SET name = 'anonymous', password = 'pwned'");
            }, "Bypass with multiple update columns should be blocked");
        }
    }
}
