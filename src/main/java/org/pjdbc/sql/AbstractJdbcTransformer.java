package org.pjdbc.sql;

import java.sql.*;

/**
 * Base implementation of JdbcTransformer that performs no transformation.
 * Subclass and override specific methods to add transformation behavior.
 *
 * This class provides identity transformations (returns input unchanged)
 * for all transformation methods, making it easy to implement transformers
 * that only need to transform certain aspects of JDBC operations.
 */
public abstract class AbstractJdbcTransformer implements JdbcTransformer {

    /**
     * Default implementation returns SQL unchanged.
     */
    @Override
    public String transformSql(String sql) throws SQLException {
        return sql;
    }

    /**
     * Default implementation returns parameter value unchanged.
     */
    @Override
    public Object transformParameter(int parameterIndex, Object value, int sqlType) throws SQLException {
        return value;
    }

    /**
     * Default implementation returns result value unchanged.
     */
    @Override
    public Object transformResultValue(int columnIndex, String columnName, Object value, int sqlType) throws SQLException {
        return value;
    }
}
