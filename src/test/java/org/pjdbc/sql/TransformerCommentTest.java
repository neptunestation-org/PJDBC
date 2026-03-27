package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentTest {

    @Test
    public void testSchemaTransformerWithComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("my_schema");
        String sql = "SELECT * FROM/*comment*/users";
        String expected = "SELECT * FROM/*comment*/my_schema.users";
        assertEquals(expected, transformer.transformSql(sql));
    }

    @Test
    public void testWhereTransformerWithComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("deleted=false");
        String sql = "/* leading comment */ SELECT * FROM users";
        String expected = "/* leading comment */ SELECT * FROM users WHERE deleted=false";
        assertEquals(expected, transformer.transformSql(sql));
    }

    @Test
    public void testWhereTransformerInsertionPointWithComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("deleted=false");
        String sql = "SELECT * FROM users/*comment*/ORDER/*comment*/BY name";
        String expected = "SELECT * FROM users WHERE deleted=false/*comment*/ORDER/*comment*/BY name";
        assertEquals(expected, transformer.transformSql(sql));
    }
}
