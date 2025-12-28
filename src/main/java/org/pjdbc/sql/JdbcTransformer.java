package org.pjdbc.sql;

import java.sql.*;

/**
 * Unified interface for JDBC transformation.
 * Handles input (SQL, parameters) and output (ResultSet values) transformation.
 *
 * Implementations can transform:
 * - SQL strings before execution
 * - Parameter values before binding to PreparedStatements
 * - ResultSet values when retrieved
 */
public interface JdbcTransformer {

    /**
     * Transform SQL before execution.
     * @param sql Original SQL string
     * @return Transformed SQL string
     * @throws SQLException if transformation fails
     */
    String transformSql(String sql) throws SQLException;

    /**
     * Transform a parameter before binding to a PreparedStatement.
     * @param parameterIndex 1-based parameter index
     * @param value Original parameter value
     * @param sqlType SQL type from java.sql.Types
     * @return Transformed parameter value
     * @throws SQLException if transformation fails
     */
    Object transformParameter(int parameterIndex, Object value, int sqlType) throws SQLException;

    /**
     * Transform a value retrieved from a ResultSet.
     * @param columnIndex 1-based column index
     * @param columnName Column name (may be null if not available)
     * @param value Original value from ResultSet
     * @param sqlType SQL type from java.sql.Types
     * @return Transformed value
     * @throws SQLException if transformation fails
     */
    Object transformResultValue(int columnIndex, String columnName, Object value, int sqlType) throws SQLException;
}
