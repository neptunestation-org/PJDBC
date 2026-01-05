import java.sql.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class DriverCompositionTest {
    @Test
    public void progressiveComposition () {
	try {
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:sink:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:sink:jdbc:cat:jdbc:sink:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:filter:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:filter:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:filter:jdbc:cat:jdbc:sink:jdbc:mock:foo"));
	    assertNotNull(DriverManager.getConnection("jdbc:cat:jdbc:filter:jdbc:cat:jdbc:tee:jdbc:sink:jdbc:mock:foo;jdbc:mock:bar"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void teePlusSink () {
	try {
	    Connection c = DriverManager.getConnection("jdbc:tee:jdbc:mock:foo;jdbc:sink:jdbc:mock:bar");
	    MockDriver foo = (MockDriver)DriverManager.getDriver("jdbc:mock:foo");
	    MockDriver bar = (MockDriver)DriverManager.getDriver("jdbc:mock:bar");
	    assertSame(foo, bar);
	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from person;");
	    stmt.executeQuery("insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);");
	    assertNotNull(MockDriver.getLog("jdbc:mock:foo"));
	    assertNotNull(MockDriver.getLog("jdbc:mock:bar"));
	    assertEquals("executeQuery[select * from person;]\n"+
			 "executeQuery[insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);]",
			 MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("", MockDriver.getLog("jdbc:mock:bar"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void teePlusSinkButReverseOrder () {
	try {
	    Connection c = DriverManager.getConnection("jdbc:tee:jdbc:sink:jdbc:mock:foo;jdbc:mock:bar");
	    MockDriver foo = (MockDriver)DriverManager.getDriver("jdbc:mock:foo");
	    MockDriver bar = (MockDriver)DriverManager.getDriver("jdbc:mock:bar");
	    assertSame(foo, bar);
	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from person;");
	    stmt.executeQuery("insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);");
	    assertNotNull(MockDriver.getLog("jdbc:mock:foo"));
	    assertNotNull(MockDriver.getLog("jdbc:mock:bar"));
	    assertEquals("", MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("executeQuery[select * from person;]\n"+
			 "executeQuery[insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);]",
			 MockDriver.getLog("jdbc:mock:bar"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void URLsCanHaveWhitespace () {
	try {
	    int i = 0;
	    try{DriverManager.getConnection("jdbc: cat:jdbc:\nmock:foo");} catch (Exception e) {i++;}
	    try{DriverManager.getConnection("jdbc: cat:jdbc:\nsink:jdbc:mock:foo");} catch (Exception e) {i++;}
	    try{DriverManager.getConnection("jdbc: cat:jdbc:\nfilter:jdbc:cat:jdbc:sink:jdbc:mock:foo");} catch (Exception e) {i++;}
	    try{DriverManager.getConnection("jdbc: cat:jdbc:\nfilter:jdbc\n:cat :  jdbc:\ntee:jdbc:sink :jdbc:mock:foo ;\njdbc:mock:bar");} catch (Exception e) {i++;}
	    assertEquals(4, i);}
	catch (Exception e) {fail(e.getMessage());}}}
