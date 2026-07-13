package org.pjdbc.drivers;

import static org.junit.Assert.assertThrows;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Test;

public class ReadonlyBypassTest {
    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD be blocked.
        assertThrows(SQLException.class, () -> {
            stmt.execute("/* comment */ DELETE FROM users");
        });
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD be blocked.
        assertThrows(SQLException.class, () -> {
            stmt.execute("-- comment \n DELETE FROM users");
        });
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD be blocked (DML inside CTE).
        assertThrows(SQLException.class, () -> {
            stmt.execute("WITH moved_rows AS (DELETE FROM users WHERE id = 1 RETURNING *) SELECT * FROM moved_rows");
        });
    }

    @Test
    public void testCTEWithMainDMLBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD be blocked (Main statement is DML after CTE).
        assertThrows(SQLException.class, () -> {
            stmt.execute("WITH t AS (SELECT 1) DELETE FROM users");
        });
    }

    @Test
    public void testValidSelect() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD NOT be blocked.
        stmt.execute("SELECT * FROM users");
    }

    @Test
    public void testValidSelectWithComment() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();

        // This SHOULD NOT be blocked.
        stmt.execute("/* comment */ SELECT * FROM users");
    }
}
