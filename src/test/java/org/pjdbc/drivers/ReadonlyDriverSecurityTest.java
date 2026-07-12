package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReadonlyDriverSecurityTest {

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:test_comment_bypass;DB_CLOSE_DELAY=-1";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE users (id INT PRIMARY KEY)");

        try {
            stmt.execute("/* comment */ DELETE FROM users");
            fail("Should have blocked DELETE with leading comment");
        } catch (SQLException e) {
            assertTrue("Expected ReadonlyDriver message but got: " + e.getMessage(),
                e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:test_cte_bypass;DB_CLOSE_DELAY=-1";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE users (id INT PRIMARY KEY)");

        try {
            stmt.execute("WITH deleted AS (DELETE FROM users RETURNING *) SELECT * FROM deleted");
            fail("Should have blocked DELETE in CTE");
        } catch (SQLException e) {
            assertTrue("Expected ReadonlyDriver message but got: " + e.getMessage(),
                e.getMessage().contains("ReadonlyDriver: Write operation not permitted"));
        }
    }
}
