package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        String sql = "/* comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);

        // If it didn't match MODIFIABLE_STATEMENT, it would return original SQL without WHERE
        assertTrue("Should have added WHERE clause, but got: " + transformed,
            transformed.contains("WHERE tenant_id=1"));
        assertTrue("Should preserve leading comment", transformed.startsWith("/* comment */"));
    }
}
