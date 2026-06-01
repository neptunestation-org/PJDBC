package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void testCteDmlBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This query might fail to execute on H2 if table doesn't exist,
                // but ReadonlyDriver should block it BEFORE it reaches H2.
                stmt.execute("WITH x AS (DELETE FROM some_table) SELECT * FROM x");
                fail("Expected SQLException for CTE with DELETE");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked' in message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* comment */ INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'DML blocked' in message, but got: " + e.getMessage(),
                       e.getMessage().contains("DML blocked"));
        }
    }
}
