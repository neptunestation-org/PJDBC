import java.sql.*;
import org.junit.Test;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class TeeDriverTest {

    @Test
    public void acceptsURL () {
	assertFalse(new TeeDriver().acceptsURL("jdbc:tee"));
	assertFalse(new TeeDriver().acceptsURL("jdbc:tee:"));
	assertFalse(new TeeDriver().acceptsURL("jdbc:tee:foo"));
	assertTrue(new TeeDriver().acceptsURL("jdbc:tee:jdbc:mock:foo;jdbc:mock:foo"));}

    @Test
    public void connectDirectlyAndInvokeMethods () {
	try {
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:foo;jdbc:mock:bar", null));
	    MockDriver foo = (MockDriver)DriverManager.getDriver("jdbc:mock:foo");
	    MockDriver bar = (MockDriver)DriverManager.getDriver("jdbc:mock:bar");
	    assertSame(foo, bar);
	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from person");
	    stmt.executeQuery("insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42)");
	    assertNotNull(MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("executeQuery[select * from person]\n"+
			 "executeQuery[insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42)]",
			 MockDriver.getLog("jdbc:mock:foo"));
	    assertNotNull(MockDriver.getLog("jdbc:mock:bar"));
	    assertEquals("executeQuery[select * from person]\n"+
			 "executeQuery[insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42)]",
			 MockDriver.getLog("jdbc:mock:bar"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void executeUpdateReplicatesToAllConnections () {
	try {
	    MockDriver.clearLogs();
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:update1;jdbc:mock:update2", null));
	    Statement stmt = c.createStatement();
	    stmt.executeUpdate("INSERT INTO person VALUES (1, 'Alice')");
	    stmt.executeUpdate("UPDATE person SET name = 'Bob' WHERE id = 1");
	    stmt.executeUpdate("DELETE FROM person WHERE id = 1");

	    String expectedLog = "executeUpdate[INSERT INTO person VALUES (1, 'Alice')]\n"+
				 "executeUpdate[UPDATE person SET name = 'Bob' WHERE id = 1]\n"+
				 "executeUpdate[DELETE FROM person WHERE id = 1]";

	    assertEquals(expectedLog, MockDriver.getLog("jdbc:mock:update1"));
	    assertEquals(expectedLog, MockDriver.getLog("jdbc:mock:update2"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void executeReplicatesToAllConnections () {
	try {
	    MockDriver.clearLogs();
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:exec1;jdbc:mock:exec2", null));
	    Statement stmt = c.createStatement();
	    stmt.execute("CREATE TABLE test (id INT)");
	    stmt.execute("DROP TABLE test");

	    String expectedLog = "execute[CREATE TABLE test (id INT)]\n"+
				 "execute[DROP TABLE test]";

	    assertEquals(expectedLog, MockDriver.getLog("jdbc:mock:exec1"));
	    assertEquals(expectedLog, MockDriver.getLog("jdbc:mock:exec2"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectDirectly () {
	try {
	    assertFalse(new TeeDriver().acceptsURL("foo"));
	    assertNull(new TeeDriver().connect("foo", null));
	    assertNull(new TeeDriver().connect("jdbc:tee", null));
	    assertNull(new TeeDriver().connect("jdbc:tee:", null));
	    assertNotNull(new TeeDriver().connect("jdbc:tee:jdbc:mock:foo;jdbc:mock:foo", null));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectIndirectlyWithBadURLFails () {
	try{DriverManager.getConnection("jdbc:tee:jdbc:mock:foo");} catch (SQLException e) {return;}
	fail("jdbc:tee:jdbc:mock:foo should have no registered driver!");}

    @Test
    public void connectIndirectlyWithGoodURLSucceeds () {
	try {DriverManager.getConnection("jdbc:tee:jdbc:mock:foo;jdbc:mock:foo");}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void compliance () {
	assertFalse(new TeeDriver().jdbcCompliant());}

    @Test
    public void versionInfo () {
	assertEquals(1, new TeeDriver().getMajorVersion());
	assertEquals(0, new TeeDriver().getMinorVersion());}

    @Test
    public void preparedStatementExecuteUpdateReplicatesToAllConnections () {
	try {
	    MockDriver.clearLogs();
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:pstmt1;jdbc:mock:pstmt2", null));
	    PreparedStatement pstmt = c.prepareStatement("INSERT INTO person VALUES (?, ?)");
	    pstmt.setInt(1, 1);
	    pstmt.setString(2, "Alice");
	    pstmt.executeUpdate();

	    String log1 = MockDriver.getLog("jdbc:mock:pstmt1");
	    String log2 = MockDriver.getLog("jdbc:mock:pstmt2");

	    // MockDriver logs operations on PreparedStatement, not Connection.prepareStatement()
	    assertTrue("Target 1 should have setInt", log1.contains("setInt[1, 1]"));
	    assertTrue("Target 2 should have setInt", log2.contains("setInt[1, 1]"));
	    assertTrue("Target 1 should have setString", log1.contains("setString[2, Alice]"));
	    assertTrue("Target 2 should have setString", log2.contains("setString[2, Alice]"));
	    assertTrue("Target 1 should have executeUpdate", log1.contains("executeUpdate[]"));
	    assertTrue("Target 2 should have executeUpdate", log2.contains("executeUpdate[]"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void preparedStatementExecuteReplicatesToAllConnections () {
	try {
	    MockDriver.clearLogs();
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:pexec1;jdbc:mock:pexec2", null));
	    PreparedStatement pstmt = c.prepareStatement("CREATE TABLE test (id INT)");
	    pstmt.execute();

	    String log1 = MockDriver.getLog("jdbc:mock:pexec1");
	    String log2 = MockDriver.getLog("jdbc:mock:pexec2");

	    assertTrue("Target 1 should have execute", log1.contains("execute[]"));
	    assertTrue("Target 2 should have execute", log2.contains("execute[]"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void preparedStatementExecuteQueryReplicatesToAllConnections () {
	try {
	    MockDriver.clearLogs();
	    Connection c = (new TeeDriver().connect("jdbc:tee:jdbc:mock:pquery1;jdbc:mock:pquery2", null));
	    PreparedStatement pstmt = c.prepareStatement("SELECT * FROM person WHERE id = ?");
	    pstmt.setInt(1, 42);
	    pstmt.executeQuery();

	    String log1 = MockDriver.getLog("jdbc:mock:pquery1");
	    String log2 = MockDriver.getLog("jdbc:mock:pquery2");

	    assertTrue("Target 1 should have setInt", log1.contains("setInt[1, 42]"));
	    assertTrue("Target 2 should have setInt", log2.contains("setInt[1, 42]"));
	    assertTrue("Target 1 should have executeQuery", log1.contains("executeQuery[]"));
	    assertTrue("Target 2 should have executeQuery", log2.contains("executeQuery[]"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void preparedStatementWithH2Databases () {
	try {
	    String h2Url1 = "jdbc:h2:mem:tee_pstmt_h2_1_" + System.nanoTime();
	    String h2Url2 = "jdbc:h2:mem:tee_pstmt_h2_2_" + System.nanoTime();
	    String teeUrl = "jdbc:tee:" + h2Url1 + ";" + h2Url2;

	    try (Connection conn = DriverManager.getConnection(teeUrl)) {
		// Create table in both databases
		try (Statement stmt = conn.createStatement()) {
		    stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
		}

		// Insert using PreparedStatement - should go to both
		try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test VALUES (?, ?)")) {
		    pstmt.setInt(1, 1);
		    pstmt.setString(2, "Alice");
		    int count = pstmt.executeUpdate();
		    assertEquals(1, count);
		}

		// Verify data exists in first H2 database
		try (Connection c1 = DriverManager.getConnection(h2Url1);
		     Statement s1 = c1.createStatement();
		     ResultSet rs1 = s1.executeQuery("SELECT name FROM test WHERE id = 1")) {
		    assertTrue("First DB should have the row", rs1.next());
		    assertEquals("Alice", rs1.getString(1));
		}

		// Verify data exists in second H2 database
		try (Connection c2 = DriverManager.getConnection(h2Url2);
		     Statement s2 = c2.createStatement();
		     ResultSet rs2 = s2.executeQuery("SELECT name FROM test WHERE id = 1")) {
		    assertTrue("Second DB should have the row", rs2.next());
		    assertEquals("Alice", rs2.getString(1));
		}
	    }}
	catch (Exception e) {fail(e.getMessage());}}}
