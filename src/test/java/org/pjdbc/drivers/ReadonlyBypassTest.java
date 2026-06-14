package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("/* bypass */ INSERT INTO bypass_test VALUES (1)");
                    fail("Should have blocked INSERT with leading comment");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver error message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked"));
                }
            }
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("WITH cte AS (INSERT INTO test_cte VALUES (1) RETURNING id) SELECT * FROM cte");
                    fail("Should have blocked CTE with INSERT");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver error message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("DML blocked"));
                }
            }
        }
    }
}
