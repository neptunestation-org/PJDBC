package org.pjdbc.test;

import org.pjdbc.sql.AbstractJdbcTransformer;

/**
 * Test transformer that converts SQL to uppercase.
 * Used for testing URL-based transformer configuration.
 */
public class UpperCaseTransformer extends AbstractJdbcTransformer {
    @Override
    public String transformSql(String sql) {
        return sql == null ? null : sql.toUpperCase();
    }
}
