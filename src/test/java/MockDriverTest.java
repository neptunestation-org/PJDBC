import java.sql.*;
import java.util.regex.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class MockDriverTest {

    @Before
    public void setUp() {
        MockDriver.reset();
    }

    @After
    public void tearDown() {
        MockDriver.reset();
    }

    // ========== Basic driver tests ==========

    @Test
    public void loadable() {
        try {
            DriverManager.getDriver("jdbc:mock:foo");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testBasicConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        conn.close();
    }

    @Test
    public void testLogging() throws SQLException {
        String url = "jdbc:mock:logtest";
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();
        stmt.executeQuery("SELECT 1");

        String log = MockDriver.getLog(url);
        assertTrue(log.contains("executeQuery"));
        assertTrue(log.contains("SELECT 1"));
    }

    // ========== MockResultSet tests ==========

    @Test
    public void testMockResultSetEmpty() throws SQLException {
        MockResultSet rs = MockResultSet.empty();
        assertFalse(rs.next());
        rs.close();
        assertTrue(rs.isClosed());
    }

    @Test
    public void testMockResultSetWithData() throws SQLException {
        MockResultSet rs = MockResultSet.create()
            .columns("id", "name", "active")
            .row(1, "Alice", true)
            .row(2, "Bob", false)
            .build();

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));
        assertTrue(rs.getBoolean("active"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertEquals("Bob", rs.getString("name"));
        assertFalse(rs.getBoolean("active"));

        assertFalse(rs.next());
    }

    @Test
    public void testMockResultSetMetaData() throws SQLException {
        MockResultSet rs = MockResultSet.create()
            .columns("id", "name", "price")
            .row(1, "Widget", 19.99)
            .build();

        ResultSetMetaData meta = rs.getMetaData();
        assertEquals(3, meta.getColumnCount());
        assertEquals("id", meta.getColumnName(1));
        assertEquals("name", meta.getColumnName(2));
        assertEquals("price", meta.getColumnName(3));
    }

    @Test
    public void testMockResultSetNavigation() throws SQLException {
        MockResultSet rs = MockResultSet.create()
            .columns("id")
            .row(1)
            .row(2)
            .row(3)
            .build();

        assertTrue(rs.isBeforeFirst());
        assertTrue(rs.first());
        assertEquals(1, rs.getInt(1));
        assertTrue(rs.isFirst());

        assertTrue(rs.last());
        assertEquals(3, rs.getInt(1));
        assertTrue(rs.isLast());

        assertTrue(rs.previous());
        assertEquals(2, rs.getInt(1));

        assertTrue(rs.absolute(1));
        assertEquals(1, rs.getInt(1));
    }

    @Test
    public void testMockResultSetNullHandling() throws SQLException {
        MockResultSet rs = MockResultSet.create()
            .columns("id", "name")
            .row(1, null)
            .build();

        rs.next();
        assertEquals(1, rs.getInt("id"));
        assertFalse(rs.wasNull());

        assertNull(rs.getString("name"));
        assertTrue(rs.wasNull());
    }

    @Test
    public void testMockResultSetColumnTypes() throws SQLException {
        java.sql.Date date = java.sql.Date.valueOf("2025-01-05");
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2025-01-05 10:30:00");

        MockResultSet rs = MockResultSet.create()
            .columns("int_col", "long_col", "double_col", "date_col", "ts_col")
            .row(42, 1234567890L, 3.14159, date, ts)
            .build();

        rs.next();
        assertEquals(42, rs.getInt(1));
        assertEquals(1234567890L, rs.getLong(2));
        assertEquals(3.14159, rs.getDouble(3), 0.0001);
        assertEquals(date, rs.getDate(4));
        assertEquals(ts, rs.getTimestamp(5));
    }

    @Test(expected = IllegalStateException.class)
    public void testMockResultSetRowBeforeColumns() {
        MockResultSet.create().row(1, 2, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMockResultSetRowColumnMismatch() {
        MockResultSet.create()
            .columns("a", "b")
            .row(1); // wrong number of values
    }

    // ========== Result Configuration API tests ==========

    @Test
    public void testWhenThenReturn() throws SQLException {
        MockDriver.when("SELECT * FROM users")
            .thenReturn(MockResultSet.create()
                .columns("id", "name")
                .row(1, "Alice")
                .row(2, "Bob")
                .build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM users");

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertEquals("Bob", rs.getString("name"));

        assertFalse(rs.next());
    }

    @Test
    public void testWhenThenReturnBuilder() throws SQLException {
        // Test the builder overload
        MockDriver.when("SELECT name FROM users")
            .thenReturn(MockResultSet.create()
                .columns("name")
                .row("Charlie"));

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1");

        assertTrue(rs.next());
        assertEquals("Charlie", rs.getString("name"));
    }

    @Test
    public void testWhenThenUpdate() throws SQLException {
        MockDriver.when("INSERT INTO users").thenUpdate(3);

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();
        int count = stmt.executeUpdate("INSERT INTO users (name) VALUES ('Test')");

        assertEquals(3, count);
    }

    @Test
    public void testWhenThenThrow() throws SQLException {
        MockDriver.when("DROP TABLE")
            .thenThrow(new SQLException("Not allowed", "42000", 1));

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        try {
            stmt.executeUpdate("DROP TABLE users");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertEquals("Not allowed", e.getMessage());
            assertEquals("42000", e.getSQLState());
        }
    }

    @Test
    public void testWhenMatchesRegex() throws SQLException {
        MockDriver.whenMatches("SELECT.*FROM users WHERE id = \\d+")
            .thenReturn(MockResultSet.create()
                .columns("id", "name")
                .row(1, "MatchedUser")
                .build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        // Should match
        ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 42");
        assertTrue(rs.next());
        assertEquals("MatchedUser", rs.getString("name"));

        // Reset expectations for different test
        MockDriver.reset();
        MockDriver.whenMatches("SELECT.*FROM users WHERE id = \\d+")
            .thenReturn(MockResultSet.create()
                .columns("id", "name")
                .row(1, "MatchedUser")
                .build());

        // Should NOT match (no id condition)
        Statement stmt2 = conn.createStatement();
        ResultSet rs2 = stmt2.executeQuery("SELECT * FROM users");
        assertFalse(rs2.next()); // Falls back to empty result set
    }

    @Test
    public void testWhenThenAnswer() throws SQLException {
        MockDriver.whenMatches("SELECT.*FROM users WHERE id = (\\d+)")
            .thenAnswer(sql -> {
                Matcher m = Pattern.compile("id = (\\d+)").matcher(sql);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    return MockResultSet.create()
                        .columns("id", "name")
                        .row(id, "User" + id)
                        .build();
                }
                return MockResultSet.empty();
            });

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        ResultSet rs1 = stmt.executeQuery("SELECT * FROM users WHERE id = 42");
        assertTrue(rs1.next());
        assertEquals(42, rs1.getInt("id"));
        assertEquals("User42", rs1.getString("name"));

        ResultSet rs2 = stmt.executeQuery("SELECT * FROM users WHERE id = 99");
        assertTrue(rs2.next());
        assertEquals(99, rs2.getInt("id"));
        assertEquals("User99", rs2.getString("name"));
    }

    @Test
    public void testWhenThenAnswerUpdate() throws SQLException {
        MockDriver.whenMatches("INSERT INTO users.*")
            .thenAnswerUpdate(sql -> {
                // Count VALUES clauses
                int count = 0;
                int idx = 0;
                while ((idx = sql.indexOf("VALUES", idx)) != -1) {
                    count++;
                    idx++;
                }
                return count;
            });

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        int count = stmt.executeUpdate("INSERT INTO users VALUES (1), VALUES (2), VALUES (3)");
        assertEquals(3, count);
    }

    @Test
    public void testMultipleExpectations() throws SQLException {
        MockDriver.when("SELECT * FROM users")
            .thenReturn(MockResultSet.create()
                .columns("id")
                .row(1)
                .build());

        MockDriver.when("SELECT * FROM products")
            .thenReturn(MockResultSet.create()
                .columns("id")
                .row(100)
                .build());

        MockDriver.when("INSERT INTO orders").thenUpdate(1);

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        ResultSet rs1 = stmt.executeQuery("SELECT * FROM users");
        assertTrue(rs1.next());
        assertEquals(1, rs1.getInt("id"));

        ResultSet rs2 = stmt.executeQuery("SELECT * FROM products");
        assertTrue(rs2.next());
        assertEquals(100, rs2.getInt("id"));

        assertEquals(1, stmt.executeUpdate("INSERT INTO orders (user_id) VALUES (1)"));
    }

    @Test
    public void testPreparedStatement() throws SQLException {
        MockDriver.when("SELECT * FROM users WHERE id = ?")
            .thenReturn(MockResultSet.create()
                .columns("id", "name")
                .row(1, "PreparedUser")
                .build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        ResultSet rs = pstmt.executeQuery();

        assertTrue(rs.next());
        assertEquals("PreparedUser", rs.getString("name"));
    }

    @Test
    public void testReset() throws SQLException {
        MockDriver.when("SELECT 1").thenReturn(
            MockResultSet.create().columns("x").row(1).build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        ResultSet rs1 = stmt.executeQuery("SELECT 1");
        assertTrue(rs1.next());
        assertEquals(1, rs1.getInt(1));

        MockDriver.reset();

        // After reset, should get empty result
        Statement stmt2 = conn.createStatement();
        ResultSet rs2 = stmt2.executeQuery("SELECT 1");
        assertFalse(rs2.next());
    }

    @Test
    public void testExecuteWithResultSet() throws SQLException {
        MockDriver.when("SELECT 1")
            .thenReturn(MockResultSet.create()
                .columns("x")
                .row(1)
                .build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        boolean hasResultSet = stmt.execute("SELECT 1");
        assertTrue(hasResultSet);

        ResultSet rs = stmt.getResultSet();
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
    }

    @Test
    public void testExecuteWithUpdateCount() throws SQLException {
        MockDriver.when("UPDATE users").thenUpdate(5);

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        boolean hasResultSet = stmt.execute("UPDATE users SET active = true");
        assertFalse(hasResultSet);

        assertEquals(5, stmt.getUpdateCount());
    }

    @Test
    public void testCaseInsensitiveMatching() throws SQLException {
        MockDriver.when("SELECT * FROM USERS")
            .thenReturn(MockResultSet.create()
                .columns("id")
                .row(1)
                .build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        // Should match despite different case
        ResultSet rs = stmt.executeQuery("select * from users where id = 1");
        assertTrue(rs.next());
    }

    @Test
    public void testExceptionOnQuery() throws SQLException {
        MockDriver.when("SELECT * FROM secret")
            .thenThrow(new SQLException("Access denied"));

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        try {
            stmt.executeQuery("SELECT * FROM secret");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertEquals("Access denied", e.getMessage());
        }
    }

    @Test
    public void testNoMatchFallsBackToDefault() throws SQLException {
        MockDriver.when("SELECT 1").thenReturn(
            MockResultSet.create().columns("x").row(1).build());

        Connection conn = DriverManager.getConnection("jdbc:mock:test");
        Statement stmt = conn.createStatement();

        // This SQL doesn't match any expectation
        ResultSet rs = stmt.executeQuery("SELECT * FROM unmatched");
        assertFalse(rs.next()); // Empty result set
    }
}
