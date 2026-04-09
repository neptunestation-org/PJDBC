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
        String baseH2Url = "jdbc:h2:mem:test_comment_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:readonly:" + baseH2Url;

        try (Connection setupConn = DriverManager.getConnection(baseH2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT)");
            }

            try (Connection conn = DriverManager.getConnection(url)) {
                try (Statement stmt = conn.createStatement()) {
                    // Leading comment might bypass the regex which uses ^\s*
                    stmt.execute("/* bypass */ INSERT INTO test_table VALUES (1)");

                    // If we reach here, check if it actually inserted
                    try (Statement verifyStmt = setupConn.createStatement()) {
                        try (java.sql.ResultSet rs = verifyStmt.executeQuery("SELECT COUNT(*) FROM test_table")) {
                            assertTrue(rs.next());
                            if (rs.getInt(1) > 0) {
                                fail("ReadonlyDriver was bypassed using leading comment!");
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // Expected if not bypassed
                System.out.println("Caught expected exception: " + e.getMessage());
                assertTrue("Should be blocked by ReadonlyDriver", e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
