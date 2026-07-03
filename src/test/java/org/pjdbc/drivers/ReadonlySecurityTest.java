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

    private void setupTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT)");
            }
        }
    }

    private void assertBlocked(String url, String sql, String expectedMessage) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                fail("Should have been blocked: " + sql);
            }
        } catch (SQLException e) {
            assertTrue("Expected message to contain '" + expectedMessage + "', but was: " + e.getMessage(),
                e.getMessage().contains(expectedMessage));
        }
    }

    @Test
    public void testCommentBypass() throws SQLException {
        setupTable("test_comment_bypass");
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";

        assertBlocked(url, "/* comment */ INSERT INTO test_table VALUES (1)", "DML blocked");
        assertBlocked(url, "-- comment\nINSERT INTO test_table VALUES (1)", "DML blocked");
        assertBlocked(url, "/* \n multi-line \n comment \n */ UPDATE test_table SET id = 2", "DML blocked");
    }

    @Test
    public void testCteBypass() throws SQLException {
        setupTable("test_cte_bypass");
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";

        // This is a common bypass for simple regex-based DML filters
        assertBlocked(url, "WITH temp AS (SELECT 1) INSERT INTO test_table SELECT * FROM temp", "DML blocked");
        assertBlocked(url, "WITH temp AS (SELECT 1) UPDATE test_table SET id = 1", "DML blocked");
        assertBlocked(url, "WITH temp AS (SELECT 1) DELETE FROM test_table", "DML blocked");
    }

    @Test
    public void testFalsePositives() throws SQLException {
        // SELECT statements containing keywords should NOT be blocked
        String url = "jdbc:readonly:jdbc:h2:mem:test_false_positives";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 'INSERT' FROM (SELECT 1)");
                stmt.executeQuery("SELECT 1 /* this is an UPDATE */");
                stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'DELETE'");
            }
        }
    }
}
