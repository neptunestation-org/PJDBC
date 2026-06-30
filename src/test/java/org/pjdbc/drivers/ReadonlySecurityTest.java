package org.pjdbc.drivers;

import static org.junit.Assert.assertThrows;
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

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test (id INT PRIMARY KEY, val VARCHAR(100))");
                stmt.execute("INSERT INTO test VALUES (1, 'initial')");
            }
        }
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String dbName = "test_comment_bypass";
        setupTestTable(dbName);
        String url = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but currently might not be
                String bypassSql = "/* comment */ DELETE FROM test WHERE id = 1";
                try {
                    stmt.execute(bypassSql);
                    // If it doesn't throw, we might have a bypass
                    // Check if data was actually deleted
                    try (Connection verifyConn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
                        try (Statement verifyStmt = verifyConn.createStatement()) {
                            java.sql.ResultSet rs = verifyStmt.executeQuery("SELECT count(*) FROM test WHERE id = 1");
                            rs.next();
                            if (rs.getInt(1) == 0) {
                                fail("Security Bypass: DML executed despite ReadonlyDriver");
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Expected behavior: should be blocked
                    assertTrue("Expected blocked message but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String dbName = "test_cte_bypass";
        setupTestTable(dbName);
        String url = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE bypass mentioned in README
                String bypassSql = "WITH x AS (DELETE FROM test WHERE id = 1) SELECT 1 FROM x";
                try {
                    stmt.execute(bypassSql);
                    // Check if data was actually deleted
                    try (Connection verifyConn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
                        try (Statement verifyStmt = verifyConn.createStatement()) {
                            java.sql.ResultSet rs = verifyStmt.executeQuery("SELECT count(*) FROM test WHERE id = 1");
                            rs.next();
                            if (rs.getInt(1) == 0) {
                                fail("Security Bypass: CTE DML executed despite ReadonlyDriver");
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Expected behavior: should be blocked
                    assertTrue("Expected blocked message but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }
}
