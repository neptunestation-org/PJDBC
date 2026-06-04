package org.pjdbc.drivers;

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
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass;DB_CLOSE_DELAY=-1";
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_comment_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
            }
        }
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                throw e;
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_comment_bypass;DB_CLOSE_DELAY=-1";
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_line_comment_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
            }
        }
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- comment\nINSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                throw e;
            }
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1";
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
            }
        }
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This bypasses many simple regex filters
                stmt.execute("WITH x AS (DELETE FROM test_table) SELECT * FROM x");
                fail("Expected SQLException for DML inside CTE");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                throw e;
            }
        }
    }
}
