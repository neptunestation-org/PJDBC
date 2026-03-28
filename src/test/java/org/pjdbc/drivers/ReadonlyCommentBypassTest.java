package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyCommentBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String dbName = "test_comment_bypass_" + System.currentTimeMillis();
        String url = "jdbc:readonly:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String directUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        // Setup table
        try (Connection setupConn = DriverManager.getConnection(directUrl)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT)");
                stmt.execute("INSERT INTO test_table VALUES (1)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Leading comment might bypass the regex ^\s*
                String sql = "/* comment */ DELETE FROM test_table WHERE id = 1";
                stmt.executeUpdate(sql);

                // Verify if it was deleted
                try (Connection verifyConn = DriverManager.getConnection(directUrl)) {
                    try (Statement verifyStmt = verifyConn.createStatement()) {
                        var rs = verifyStmt.executeQuery("SELECT COUNT(*) FROM test_table");
                        rs.next();
                        if (rs.getInt(1) == 0) {
                            fail("Bypass successful: DELETE executed via leading comment in ReadonlyDriver");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }
}
