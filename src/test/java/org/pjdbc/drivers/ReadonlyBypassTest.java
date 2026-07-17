package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Security bypass tests for ReadonlyDriver to ensure comment-based and
 * CTE-based write bypasses are correctly detected and blocked.
 */
public class ReadonlyBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    private Connection getReadonlyConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:readonly:jdbc:h2:mem:test_readonly_bypass");
    }

    @Test
    public void testLeadingBlockCommentDmlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("/* allow write */ INSERT INTO test_table VALUES (1)");
            fail("Expected SQLException for leading block comment DML bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testLeadingLineCommentDmlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("-- leading comment \n INSERT INTO test_table VALUES (1)");
            fail("Expected SQLException for leading line comment DML bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testMultiLineBlockCommentDmlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("/* \n multi \n line \n comment */ UPDATE test_table SET val = 1");
            fail("Expected SQLException for multi-line block comment DML bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: UPDATE"));
        }
    }

    @Test
    public void testLeadingBlockCommentDdlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("/* allow schema modification */ CREATE TABLE test_table (id INT)");
            fail("Expected SQLException for leading block comment DDL bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DDL blocked: CREATE"));
        }
    }

    @Test
    public void testCtePrefixDmlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("WITH cte AS (SELECT 1) INSERT INTO test_table VALUES (1)");
            fail("Expected SQLException for CTE-prefixed DML bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testCteNestedDmlBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("WITH cte AS (INSERT INTO test_table VALUES (1)) SELECT * FROM cte");
            fail("Expected SQLException for CTE-nested DML bypass");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testCteNestedDmlWithCommentsBypassBlocked() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("WITH cte AS /* comment */ ( /* comment */ INSERT INTO test_table VALUES (1) ) SELECT * FROM cte");
            fail("Expected SQLException for CTE-nested DML bypass with comments");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked: INSERT"));
        }
    }

    @Test
    public void testNoFalsePositivesInLiterals() throws SQLException {
        try (Connection conn = getReadonlyConnection();
             Statement stmt = conn.createStatement()) {
            // These should be permitted as they are SELECT queries containing keywords in literals or column names
            stmt.execute("SELECT 'INSERT INTO table' AS col FROM (VALUES (1))");
            stmt.execute("SELECT 'CREATE TABLE' AS col FROM (VALUES (1))");
        }
    }
}
