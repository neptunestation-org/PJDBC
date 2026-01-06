package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transformer that chains multiple JdbcTransformers together.
 *
 * <p>Transformers are applied in order: the output of one transformer
 * becomes the input to the next. This allows composing simple transformers
 * into complex transformation pipelines.</p>
 *
 * <h2>Example</h2>
 * <pre>
 * JdbcTransformer composite = new CompositeTransformer()
 *     .add(new SchemaTransformer("tenant_123"))
 *     .add(new WhereTransformer("deleted=false"))
 *     .add(new RenameTransformer().addRename("old_table", "new_table"));
 * </pre>
 *
 * @see FilterDriver
 */
public class CompositeTransformer extends AbstractJdbcTransformer {

    private final List<JdbcTransformer> transformers = new ArrayList<>();

    /**
     * Create an empty CompositeTransformer.
     */
    public CompositeTransformer() {
    }

    /**
     * Add a transformer to the chain.
     *
     * @param transformer the transformer to add
     * @return this transformer for chaining
     */
    public CompositeTransformer add(JdbcTransformer transformer) {
        if (transformer != null) {
            transformers.add(transformer);
        }
        return this;
    }

    /**
     * Check if this composite has any transformers.
     *
     * @return true if at least one transformer has been added
     */
    public boolean isEmpty() {
        return transformers.isEmpty();
    }

    /**
     * Get the number of transformers in this composite.
     *
     * @return the number of transformers
     */
    public int size() {
        return transformers.size();
    }

    @Override
    public String transformSql(String sql) throws SQLException {
        String result = sql;
        for (JdbcTransformer transformer : transformers) {
            result = transformer.transformSql(result);
        }
        return result;
    }

    @Override
    public Object transformParameter(int parameterIndex, Object value, int sqlType) throws SQLException {
        Object result = value;
        for (JdbcTransformer transformer : transformers) {
            result = transformer.transformParameter(parameterIndex, result, sqlType);
        }
        return result;
    }

    @Override
    public Object transformResultValue(int columnIndex, String columnName, Object value, int sqlType) throws SQLException {
        Object result = value;
        for (JdbcTransformer transformer : transformers) {
            result = transformer.transformResultValue(columnIndex, columnName, result, sqlType);
        }
        return result;
    }
}
