import org.junit.Test;
import org.pjdbc.sql.JdbcUrlParser;
import static org.junit.Assert.*;

public class JdbcUrlParserTest {

    @Test
    public void parseSimpleUrl() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:filter:jdbc:mock:foo");
        assertEquals("jdbc", parser.getProtocol());
        assertEquals("filter", parser.getSubprotocol());
        assertEquals("jdbc:mock:foo", parser.getSubname());
        assertTrue(parser.getParameters().isEmpty());
    }

    @Test
    public void parseUrlWithSingleParameter() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:filter[class=com.example.MyFilter]:jdbc:mock:foo");
        assertEquals("jdbc", parser.getProtocol());
        assertEquals("filter", parser.getSubprotocol());
        assertEquals("jdbc:mock:foo", parser.getSubname());
        assertEquals("com.example.MyFilter", parser.getParameter("class"));
    }

    @Test
    public void parseUrlWithMultipleParameters() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:pool[min=5,max=20]:jdbc:postgresql://localhost/db");
        assertEquals("jdbc", parser.getProtocol());
        assertEquals("pool", parser.getSubprotocol());
        assertEquals("jdbc:postgresql://localhost/db", parser.getSubname());
        assertEquals("5", parser.getParameter("min"));
        assertEquals("20", parser.getParameter("max"));
        assertEquals(2, parser.getParameters().size());
    }

    @Test
    public void getParameterWithDefault() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:pool[min=5]:jdbc:mock:foo");
        assertEquals("5", parser.getParameter("min", "1"));
        assertEquals("10", parser.getParameter("max", "10"));
    }

    @Test
    public void parseUrlWithNoParameters() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:cat:jdbc:mock:foo");
        assertEquals("cat", parser.getSubprotocol());
        assertNull(parser.getParameter("anything"));
        assertEquals("default", parser.getParameter("anything", "default"));
    }

    @Test
    public void parseNestedProxyUrl() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:log:jdbc:filter:jdbc:mock:foo");
        assertEquals("log", parser.getSubprotocol());
        assertEquals("jdbc:filter:jdbc:mock:foo", parser.getSubname());
    }

    @Test
    public void parseUrlWithEmptyParameters() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:pool[]:jdbc:mock:foo");
        assertEquals("pool", parser.getSubprotocol());
        assertTrue(parser.getParameters().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseNullUrlThrows() {
        JdbcUrlParser.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseInvalidUrlThrows() {
        JdbcUrlParser.parse("not-a-jdbc-url");
    }

    @Test
    public void parseUrlWithSpecialCharactersInValue() {
        JdbcUrlParser parser = JdbcUrlParser.parse("jdbc:filter[query=SELECT * FROM foo]:jdbc:mock:bar");
        assertEquals("SELECT * FROM foo", parser.getParameter("query"));
    }
}
