package org.pjdbc.drivers;

import static org.junit.Assert.fail;
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
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
                // If it reaches here, it might be because the driver didn't block CREATE due to comments
                // But wait, the standard testCreateBlocked already fails.
                // Let's try with a comment.
            } catch (SQLException e) {
                // Expected if not bypassed
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ CREATE TABLE test2 (id INT)");
                fail("Bypassed ReadonlyDriver using comments!");
            } catch (SQLException e) {
                if (!e.getMessage().contains("ReadonlyDriver")) {
                     // If it's a database error (e.g. table already exists), then it might have bypassed the proxy
                     System.out.println("Caught non-proxy exception: " + e.getMessage());
                }
            }
        }
    }
}
