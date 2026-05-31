package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlySecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_comment")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                    fail("Should have blocked INSERT with leading comment");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("DML blocked"));
                }
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                try {
                    // Even if H2 doesn't support this, ReadonlyDriver SHOULD block it
                    // if it sees the INSERT keyword in a DML context.
                    // If ReadonlyDriver doesn't block it, then it might reach the database.
                    stmt.execute("WITH cte AS (SELECT 1) INSERT INTO test_table SELECT * FROM cte");
                    fail("Should have blocked INSERT in CTE");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("DML blocked"));
                }
            }
        }
    }
}
