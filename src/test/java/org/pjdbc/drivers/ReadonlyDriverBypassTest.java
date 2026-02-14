package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadonlyDriverBypassTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
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
            "jdbc:readonly:jdbc:h2:mem:readonly_bypass;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            // Setup
            try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:readonly_bypass;DB_CLOSE_DELAY=-1")) {
                setup.createStatement().execute("CREATE TABLE test (id INT)");
            }

            // Normal INSERT is blocked
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("INSERT INTO test VALUES (1)");
            });

            // Bypass with comment should also be blocked
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("/* comment */ INSERT INTO test VALUES (2)");
            }, "Bypass with comment should be blocked by ReadonlyDriver");
        }
    }
}
