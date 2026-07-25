package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.h2.Driver");
    }

    @Test
    public void testLeadingCommentsBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_leading_comments;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_comments (id INT)");
                fail("Expected DDL to be blocked despite leading comments");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DDL blocked"));
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* This is a block comment */ -- this is a line comment \n CREATE TABLE test_comments (id INT)");
                fail("Expected DDL to be blocked despite leading comments");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DDL blocked"));
            }
        }
    }

    @Test
    public void testCTEBypassBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Setup table first with native connection
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INT)");
                }
            }

            // Attempt CTE bypass on readonly connection
            String sql = "WITH cte AS (INSERT INTO test_table VALUES (1)) SELECT * FROM cte";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery(sql);
                fail("Expected SQLException for INSERT embedded inside a CTE");
            } catch (SQLException e) {
                assertTrue(e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testLegitimateSelectContainingKeywordsAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_legit;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Select statement with the word "create" inside a string literal should be allowed
                try (ResultSet rs = stmt.executeQuery("SELECT 'This is a CREATE statement' AS msg")) {
                    assertTrue(rs.next());
                    assertEquals("This is a CREATE statement", rs.getString("msg"));
                }
            }
        }
    }

    @Test
    public void testLegitimateSelectContainingKeywordsInCommentsAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_legit_comments;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Select statement with blocked keywords in comments should be allowed
                String sql = "/* We are going to INSERT or UPDATE here soon */ SELECT 42 AS val";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt("val"));
                }
            }
        }
    }
}
