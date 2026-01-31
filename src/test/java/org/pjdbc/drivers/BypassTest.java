package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import org.junit.jupiter.api.Test;

public class BypassTest {
    @Test
    void testBypass() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.h2.Driver");

        // Setup without schema driver
        Connection setup = DriverManager.getConnection("jdbc:h2:mem:bypass;DB_CLOSE_DELAY=-1");
        Statement setupStmt = setup.createStatement();
        setupStmt.execute("CREATE TABLE \"blocked_table\" (id INT, ssn VARCHAR(20))");
        setupStmt.execute("CREATE TABLE \"allowed_table\" (id INT, ssn VARCHAR(20))");
        setup.close();

        // Now connect with schema driver
        Connection conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=allowed_table,blockedColumns=ssn]:jdbc:h2:mem:bypass;DB_CLOSE_DELAY=-1"
        );

        Statement stmt = conn.createStatement();

        // This should be blocked
        assertThrows(SQLException.class, () -> {
            stmt.executeQuery("SELECT * FROM blocked_table");
        }, "SELECT * FROM blocked_table should have been blocked");

        // Table bypass - SHOULD NOW BE BLOCKED
        assertThrows(SQLException.class, () -> {
            stmt.executeQuery("SELECT * FROM \"blocked_table\"");
        }, "SELECT * FROM \"blocked_table\" should have been blocked");

        // Column bypass - SHOULD NOW BE BLOCKED (wait, it was already blocked by accident, but let's be sure)
        assertThrows(SQLException.class, () -> {
            stmt.executeQuery("SELECT \"ssn\" FROM \"allowed_table\"");
        }, "SELECT \"ssn\" FROM \"allowed_table\" should have been blocked");

        // Test other quoting styles
        assertThrows(SQLException.class, () -> {
            stmt.executeQuery("SELECT * FROM `blocked_table` ");
        });

        assertThrows(SQLException.class, () -> {
            stmt.executeQuery("SELECT * FROM [blocked_table]");
        });

        conn.close();
    }
}
