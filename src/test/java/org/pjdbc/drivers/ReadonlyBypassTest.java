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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Leading block comment
                try {
                    stmt.execute("/* leading comment */ INSERT INTO test_table VALUES (1)");
                    fail("Should have blocked INSERT with leading block comment");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver to block it, but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver"));
                }

                // Leading line comment
                try {
                    stmt.execute("-- leading comment\nINSERT INTO test_table VALUES (1)");
                    fail("Should have blocked INSERT with leading line comment");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver to block it, but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver"));
                }
            }
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE with INSERT
                try {
                    stmt.execute("WITH t AS (SELECT 1) INSERT INTO test_table SELECT * FROM t");
                    fail("Should have blocked INSERT within CTE");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver to block it, but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver"));
                }

                // CTE with UPDATE
                try {
                    stmt.execute("WITH t AS (SELECT 1) UPDATE test_table SET id = 1");
                    fail("Should have blocked UPDATE within CTE");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver to block it, but got: " + e.getMessage(),
                        e.getMessage().contains("ReadonlyDriver"));
                }
            }
        }
    }

    @Test
    public void testCTEFalsePositive() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_false_positive";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be allowed, but currently it is blocked by the blunt NESTED_DML_PATTERN
                // "UPDATE" is inside a string literal
                stmt.execute("WITH t AS (SELECT 'No UPDATE needed' AS msg) SELECT * FROM t");
            }
        }
    }
}
