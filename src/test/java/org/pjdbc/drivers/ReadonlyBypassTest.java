package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
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
    public void testLeadingCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("/* bypass */ INSERT INTO test VALUES (1)");
                    fail("Should have blocked INSERT with leading comment");
                } catch (SQLException e) {
                    assertTrue("Expected readonly block message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }

    @Test
    public void testInlineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_inline;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("INSERT/**/INTO test VALUES (1)");
                    fail("Should have blocked INSERT with inline comment");
                } catch (SQLException e) {
                    assertTrue("Expected readonly block message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_cte;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("WITH cte AS (INSERT INTO test VALUES (1) RETURNING id) SELECT * FROM cte");
                    fail("Should have blocked INSERT within CTE");
                } catch (SQLException e) {
                    assertTrue("Expected readonly block message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }

    @Test
    public void testSelectWithBlockedKeywordsInString() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_string;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be allowed
                stmt.execute("SELECT 'This is an INSERT statement' AS col");
            }
        }
    }

    @Test
    public void testCteNoSpaceBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:readonly_cte_nospace;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("WITH cte AS(INSERT INTO test VALUES (1) RETURNING id) SELECT * FROM cte");
                    fail("Should have blocked INSERT within CTE with no space after AS");
                } catch (SQLException e) {
                    assertTrue("Expected readonly block message, got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
                }
            }
        }
    }
}
