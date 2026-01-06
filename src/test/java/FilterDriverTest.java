import java.sql.*;
import java.util.regex.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import org.pjdbc.sql.*;
import static org.junit.Assert.*;

public class FilterDriverTest {
    @Test
    public void acceptsURL () {
	assertFalse(new FilterDriver().acceptsURL("jdbc:filtering"));
	assertFalse(new FilterDriver().acceptsURL("jdbc:filter:"));
	assertFalse(new FilterDriver().acceptsURL("jdbc:filter:foo"));
	assertTrue(new FilterDriver().acceptsURL("jdbc:filter:jdbc:mock:foo"));}

    @Test
    public void assertionFilter () {
	try {
	    ((FilterDriver)DriverManager.getDriver("jdbc:filter:jdbc:mock:foo"))
		.setTransformer(new AbstractJdbcTransformer() {
			public String transformSql (String sql) {
			    Matcher m1 = Pattern.compile("CREATE\\s+ASSERTION\\s+(\\w+)\\s+CHECK\\s+(.*)").matcher((""+sql).trim().toUpperCase());
			    if (m1.matches()) {
				Matcher m2 = Pattern.compile("\\((.*)=(.*)\\)").matcher(m1.group(2));
				if (m2.matches()) {
				    return m1.group(1) + "," + m2.group(1) + "," + m2.group(2);}}
			    return sql;}});
	    DriverManager
		.getConnection("jdbc:filter:jdbc:mock:foo")
		.createStatement()
		.executeQuery("create assertion foo check (0=(select count(*) from person))");
	    String log = MockDriver.getLog("jdbc:mock:foo");
	    assertEquals("executeQuery[FOO,0,(SELECT COUNT(*) FROM PERSON)]", log);}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectDirectlyAndInvokeMethods () {
	try {
	    Connection c = (new FilterDriver().connect("jdbc:filter:jdbc:mock:foo", null));
	    Statement stmt = c.createStatement();
	    String query = "select * from person;";
	    stmt.executeQuery(query);
	    assertNotNull(MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("executeQuery[select * from person;]", MockDriver.getLog("jdbc:mock:foo"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectDirectly () {
	try {
	    assertFalse(new FilterDriver().acceptsURL("foo"));
	    assertNull(new FilterDriver().connect("foo", null));
	    assertNull(new FilterDriver().connect("jdbc:filtering", null));
	    assertNull(new FilterDriver().connect("jdbc:filter:", null));
	    assertNotNull(new FilterDriver().connect("jdbc:filter:jdbc:mock:foo", null));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectIndirectly () {
	try {assertNotNull(DriverManager.getConnection("jdbc:filter:jdbc:mock:foo"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void compliance () {
	assertFalse(new FilterDriver().jdbcCompliant());}

    @Test
    public void upcaseFilter () {
	try {
	    ((FilterDriver)DriverManager.getDriver("jdbc:filter:jdbc:mock:foo")).setTransformer(new AbstractJdbcTransformer() {
		    public String transformSql (String sql) {return sql==null ? null : sql.toUpperCase();}});
	    DriverManager.getConnection("jdbc:filter:jdbc:mock:foo").createStatement().executeQuery("select * from person;");
	    String log = MockDriver.getLog("jdbc:mock:foo");
	    assertEquals("executeQuery[SELECT * FROM PERSON;]", log);}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void versionInfo () {
	assertEquals(1, new FilterDriver().getMajorVersion());
	assertEquals(0, new FilterDriver().getMinorVersion());}

    @Test
    public void urlBasedTransformer () {
	try {
	    // Use URL parameter to specify transformer class
	    Connection conn = DriverManager.getConnection(
		"jdbc:filter[class=org.pjdbc.test.UpperCaseTransformer]:jdbc:mock:foo");
	    conn.createStatement().executeQuery("select * from person;");
	    String log = MockDriver.getLog("jdbc:mock:foo");
	    assertEquals("executeQuery[SELECT * FROM PERSON;]", log);}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void urlBasedTransformerInvalidClass () {
	try {
	    // Invalid class should throw SQLException
	    DriverManager.getConnection(
		"jdbc:filter[class=com.nonexistent.Transformer]:jdbc:mock:foo");
	    fail("Should throw SQLException for non-existent class");}
	catch (SQLException e) {
	    assertTrue(e.getMessage().contains("not found"));}}

    @Test
    public void urlBasedTransformerNotImplementingInterface () {
	try {
	    // Class not implementing JdbcTransformer should throw SQLException
	    DriverManager.getConnection(
		"jdbc:filter[class=java.lang.String]:jdbc:mock:foo");
	    fail("Should throw SQLException for class not implementing JdbcTransformer");}
	catch (SQLException e) {
	    assertTrue(e.getMessage().contains("does not implement JdbcTransformer"));}}

    @Test
    public void perConnectionTransformerIsolation () {
	try {
	    // Two connections with different transformers
	    Connection conn1 = DriverManager.getConnection(
		"jdbc:filter[class=org.pjdbc.test.UpperCaseTransformer]:jdbc:mock:foo");
	    Connection conn2 = DriverManager.getConnection(
		"jdbc:filter:jdbc:mock:bar"); // default pass-through

	    conn1.createStatement().executeQuery("select * from users;");
	    conn2.createStatement().executeQuery("select * from orders;");

	    assertEquals("executeQuery[SELECT * FROM USERS;]", MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("executeQuery[select * from orders;]", MockDriver.getLog("jdbc:mock:bar"));}
	catch (Exception e) {fail(e.getMessage());}}

    // ========== Built-in Transformer Tests ==========

    @Test
    public void schemaTransformer() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[schema=tenant_123]:jdbc:mock:schema_test");
            conn.createStatement().executeQuery("SELECT * FROM users");
            String log = MockDriver.getLog("jdbc:mock:schema_test");
            assertEquals("executeQuery[SELECT * FROM tenant_123.users]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void schemaTransformerAlreadyQualified() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[schema=tenant_123]:jdbc:mock:schema_qualified");
            conn.createStatement().executeQuery("SELECT * FROM public.users");
            String log = MockDriver.getLog("jdbc:mock:schema_qualified");
            // Already qualified names should not be modified
            assertEquals("executeQuery[SELECT * FROM public.users]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void schemaTransformerJoin() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[schema=acme]:jdbc:mock:schema_join");
            conn.createStatement().executeQuery("SELECT * FROM users JOIN orders ON users.id = orders.user_id");
            String log = MockDriver.getLog("jdbc:mock:schema_join");
            assertEquals("executeQuery[SELECT * FROM acme.users JOIN acme.orders ON users.id = orders.user_id]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void whereTransformerAddsNewWhere() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[where=deleted=false]:jdbc:mock:where_new");
            conn.createStatement().executeQuery("SELECT * FROM users");
            String log = MockDriver.getLog("jdbc:mock:where_new");
            assertEquals("executeQuery[SELECT * FROM users WHERE deleted=false]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void whereTransformerAppendsToExisting() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[where=deleted=false]:jdbc:mock:where_append");
            conn.createStatement().executeQuery("SELECT * FROM users WHERE active=true");
            String log = MockDriver.getLog("jdbc:mock:where_append");
            assertEquals("executeQuery[SELECT * FROM users WHERE active=true AND deleted=false]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void whereTransformerWithOrderBy() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[where=tenant_id=42]:jdbc:mock:where_order");
            conn.createStatement().executeQuery("SELECT * FROM users ORDER BY name");
            String log = MockDriver.getLog("jdbc:mock:where_order");
            assertEquals("executeQuery[SELECT * FROM users WHERE tenant_id=42 ORDER BY name]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void whereTransformerIgnoresInsert() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[where=deleted=false]:jdbc:mock:where_insert");
            conn.createStatement().executeUpdate("INSERT INTO users VALUES (1, 'test')");
            String log = MockDriver.getLog("jdbc:mock:where_insert");
            // INSERT should not be modified
            assertEquals("executeUpdate[INSERT INTO users VALUES (1, 'test')]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void renameTransformerBasic() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[rename.users=customers]:jdbc:mock:rename_basic");
            conn.createStatement().executeQuery("SELECT * FROM users");
            String log = MockDriver.getLog("jdbc:mock:rename_basic");
            assertEquals("executeQuery[SELECT * FROM customers]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void renameTransformerCaseInsensitive() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[rename.USERS=customers]:jdbc:mock:rename_case");
            conn.createStatement().executeQuery("SELECT * FROM users WHERE Users.id = 1");
            String log = MockDriver.getLog("jdbc:mock:rename_case");
            assertEquals("executeQuery[SELECT * FROM customers WHERE customers.id = 1]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void renameTransformerMultiple() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[rename.users=customers,rename.orders=purchases]:jdbc:mock:rename_multi");
            conn.createStatement().executeQuery("SELECT * FROM users JOIN orders");
            String log = MockDriver.getLog("jdbc:mock:rename_multi");
            assertEquals("executeQuery[SELECT * FROM customers JOIN purchases]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void compositeTransformers() {
        try {
            // Combine schema, where, and rename transformers
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[schema=acme,where=active=true,rename.users=people]:jdbc:mock:composite");
            conn.createStatement().executeQuery("SELECT * FROM users");
            String log = MockDriver.getLog("jdbc:mock:composite");
            // Order: schema first, then where, then rename
            assertEquals("executeQuery[SELECT * FROM acme.people WHERE active=true]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void builtInTransformerWithUpdate() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[schema=tenant,where=deleted=false]:jdbc:mock:update_test");
            conn.createStatement().executeUpdate("UPDATE users SET name='test'");
            String log = MockDriver.getLog("jdbc:mock:update_test");
            assertEquals("executeUpdate[UPDATE tenant.users SET name='test' WHERE deleted=false]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void builtInTransformerWithDelete() {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:filter[where=org_id=123]:jdbc:mock:delete_test");
            conn.createStatement().executeUpdate("DELETE FROM users");
            String log = MockDriver.getLog("jdbc:mock:delete_test");
            assertEquals("executeUpdate[DELETE FROM users WHERE org_id=123]", log);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
