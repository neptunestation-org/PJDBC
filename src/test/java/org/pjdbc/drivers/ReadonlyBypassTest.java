package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReadonlyBypassTest {
    @Test
    public void testCommentBypass() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:readonly:jdbc:mock:test");
        Statement stmt = conn.createStatement();

        // This should be blocked but might bypass if regex only checks ^\s*
        String sql = "/* comment */ DELETE FROM users";
        assertThrows(SQLException.class, () -> stmt.execute(sql), "Should have blocked DELETE with leading comment");
    }

    @Test
    public void testCTEBypass() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:readonly:jdbc:mock:test");
        Statement stmt = conn.createStatement();

        // CTE with DML bypass mentioned in README
        String sql = "WITH deleted AS (DELETE FROM users RETURNING *) SELECT * FROM deleted";
        assertThrows(SQLException.class, () -> stmt.execute(sql), "Should have blocked DELETE inside CTE");
    }
}
