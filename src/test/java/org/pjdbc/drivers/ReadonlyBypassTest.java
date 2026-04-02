package org.pjdbc.drivers;

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
        String baseH2Url = "jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        // Setup table using direct connection
        try (Connection conn = DriverManager.getConnection(baseH2Url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        String readonlyUrl = "jdbc:readonly:" + baseH2Url;
        try (Connection conn = DriverManager.getConnection(readonlyUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but will pass due to leading comment
                try {
                    stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                    // If we are here, it bypassed!
                } catch (SQLException e) {
                    // If it's blocked, then there's no bypass (unexpected for current code)
                    return;
                }
                fail("Should have blocked INSERT with leading comment");
            }
        }
    }
}
