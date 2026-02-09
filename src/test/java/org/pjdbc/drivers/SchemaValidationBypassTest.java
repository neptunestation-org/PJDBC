package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaValidationDriver Bypass Tests")
class SchemaValidationBypassTest {

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.h2.Driver");
    }

    @Test
    @DisplayName("bypass table using double quotes")
    void bypassTableUsingDoubleQuotes() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM \"blocked_table\"")
                );

                // BEFORE FIX: This contains H2 error message "Table \"blocked_table\" not found"
                // AFTER FIX: This should contain SchemaValidationDriver message "not in allowed tables list"
                assertTrue(ex.getMessage().contains("not in allowed tables list"),
                    "Should have been blocked by SchemaValidationDriver, but was: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("bypass table using square brackets")
    void bypassTableUsingSquareBrackets() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM [blocked_table]")
                );

                assertTrue(ex.getMessage().contains("not in allowed tables list"),
                    "Should have been blocked by SchemaValidationDriver, but was: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("bypass column using double quotes and spaces")
    void bypassColumnUsingDoubleQuotesAndSpaces() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret column]:jdbc:h2:mem:bypass3;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE users (id INT, \"secret column\" VARCHAR(100))");

            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT \"secret column\" FROM users")
            );

            assertTrue(ex.getMessage().contains("is blocked"),
                "Column 'secret column' should have been blocked by SchemaValidationDriver, but was: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("bypass column using dots and quotes")
    void bypassColumnUsingDotsAndQuotes() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret]:jdbc:h2:mem:bypass4;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"my schema\"");
            stmt.execute("CREATE TABLE \"my schema\".\"my table\" (id INT, \"secret\" VARCHAR(100))");

            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT \"my schema\".\"my table\".\"secret\" FROM \"my schema\".\"my table\"")
            );

            assertTrue(ex.getMessage().contains("is blocked"),
                "Column 'secret' should have been blocked by SchemaValidationDriver, but was: " + ex.getMessage());
        }
    }
}
