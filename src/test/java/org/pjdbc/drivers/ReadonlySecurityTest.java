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
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT)");
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Exception should be from ReadonlyDriver, but was: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver") || e.getMessage().contains("blocked"));
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML might be valid in some DBs like PostgreSQL, H2 might not support it this way.
                // Let's use a simple SELECT to see if it even gets past ReadonlyDriver if it has a fake DML-like CTE
                stmt.execute("WITH some_cte AS (SELECT 1) INSERT INTO some_table SELECT * FROM some_cte");
                fail("Expected SQLException for CTE with INSERT");
            } catch (SQLException e) {
                assertTrue("Exception should be from ReadonlyDriver, but was: " + e.getMessage(),
                    e.getMessage().contains("ReadonlyDriver") || e.getMessage().contains("blocked"));
            }
        }
    }

    @Test
    public void testFalsePositivePrevention() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_fp";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This contains "WITH" and "INSERT" but is a valid SELECT
                stmt.execute("SELECT 'This is a query WITH an INSERT string' FROM (SELECT 1)");
            }
        }
    }
}
