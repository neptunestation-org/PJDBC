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
    void bypassWithCommentsInFrom() throws SQLException {
        // Whitelist both tables so we can create them, then we'll use a connection with restricted whitelist
        String urlBase = "jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1";
        try (Connection setupConn = DriverManager.getConnection(urlBase)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE allowed_table (id INT)");
                stmt.execute("CREATE TABLE secret_table (id INT)");
            }
        }

        // Now connect via SchemaValidationDriver with restricted whitelist
        conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=allowed_table]:" + urlBase
        );

        try (Statement stmt = conn.createStatement()) {
            // This should be blocked
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM secret_table")
            );
            assertTrue(ex.getMessage().contains("secret_table"));

            // This should ALSO be blocked, but if it's vulnerable, it will succeed (bypass)
            try {
                stmt.executeQuery("SELECT * FROM/**/secret_table");
                // If it doesn't throw, the bypass worked!
                fail("Bypass successful: SELECT * FROM/**/secret_table was not blocked");
            } catch (SQLException e) {
                if (e.getMessage().contains("secret_table")) {
                    // It was correctly blocked
                    return;
                }
                // Some other error (e.g. SQL syntax error) - still means it didn't block it as expected
                throw e;
            }
        }
    }
}
