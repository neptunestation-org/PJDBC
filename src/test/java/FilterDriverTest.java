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
	catch (Exception e) {fail(e.getMessage());}}}
