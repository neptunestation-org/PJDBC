package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaValidationDriver Bypass Tests")
class SchemaValidationDriverBypassTest {

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
    @DisplayName("blocks table access even with comments (bypass attempt)")
    void blocksTableAccessWithComments() throws SQLException {
        // Setup without schema driver first to create tables
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setup.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT)");
                stmt.execute("CREATE TABLE secrets (id INT, val VARCHAR(100))");
            }
        }

        conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=users]:jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            // Standard block works
            assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM secrets")
            );

            // Bypass attempt with block comment
            // If the regex is \s+, "FROM/**/secrets" won't match "FROM\s+secrets"
            // We expect this to FAIL (not throw SQLException) if there is a bypass vulnerability
            stmt.executeQuery("SELECT * FROM/**/secrets");
        } catch (SQLException e) {
            if (e.getMessage().contains("secrets")) {
                return; // Successfully blocked
            }
            throw e;
        }
        fail("Should have blocked access to 'secrets' table even with comments");
    }

    @Test
    @DisplayName("blocks table access with line comments (bypass attempt)")
    void blocksTableAccessWithLineComments() throws SQLException {
         // Setup without schema driver first to create tables
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setup.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT)");
                stmt.execute("CREATE TABLE secrets (id INT, val VARCHAR(100))");
            }
        }

        conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=users]:jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            // Bypass attempt with line comment and newline
            stmt.executeQuery("SELECT * FROM --\nsecrets");
        } catch (SQLException e) {
            if (e.getMessage().contains("secrets")) {
                return; // Successfully blocked
            }
            throw e;
        }
        fail("Should have blocked access to 'secrets' table even with line comments");
    }
}
