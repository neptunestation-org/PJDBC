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
	assertEquals(0, new TeeDriver().getMinorVersion());}}
