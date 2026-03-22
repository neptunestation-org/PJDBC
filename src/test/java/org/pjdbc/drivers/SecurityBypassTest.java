package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Test;

public class SecurityBypassTest {
    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        // Allow DDL so we can setup the table, but block DML
        String url = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:test_readonly_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");

                // Blocked DML with comments
                assertBlocked(stmt, "/* comment */ INSERT INTO test VALUES (1)");
                assertBlocked(stmt, "-- line comment\nINSERT INTO test VALUES (1)");
                assertBlocked(stmt, "  \n  /* comment */  DELETE FROM test");
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        // Setup tables using direct H2 connection to bypass schema validation during setup
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT)");
                stmt.execute("CREATE TABLE secrets (data VARCHAR)");
            }
        }

        // Whitelist 'users', block 'secrets'
        String url = "jdbc:schema[allowedTables=users,mode=whitelist]:jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Should be allowed
                stmt.execute("SELECT * FROM /* comment */ users");

                // Should be blocked even with comments as separators
                assertBlocked(stmt, "SELECT * FROM /* comment */ secrets");
                assertBlocked(stmt, "SELECT * FROM -- comment\nsecrets");
                assertBlocked(stmt, "SELECT * FROM\n/* comment */\nsecrets");

                // Blocked in other clauses
                assertBlocked(stmt, "INSERT INTO /* comment */ secrets VALUES ('data')");
                assertBlocked(stmt, "UPDATE /* comment */ secrets SET data='new'");
                assertBlocked(stmt, "DELETE FROM /* comment */ secrets");
            }
        }
    }

    private void assertBlocked(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
            fail("Should have blocked: " + sql);
        } catch (SQLException e) {
            // Expected
        }
    }
}
