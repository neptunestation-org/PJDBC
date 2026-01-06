package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractJdbcTransformer;
import org.pjdbc.sql.CompositeTransformer;
import org.pjdbc.sql.JdbcTransformer;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.sql.PjdbcListeners;
import org.pjdbc.sql.RenameTransformer;
import org.pjdbc.sql.SchemaTransformer;
import org.pjdbc.sql.WhereTransformer;

/**
 * FilterDriver transforms SQL statements using configurable transformers.
 *
 * <h2>Built-in Transformers</h2>
 * <p>URL-configurable transformers for common use cases:</p>
 *
 * <h3>Schema Prefix</h3>
 * <p>Add schema prefix to unqualified table names:</p>
 * <pre>
 * jdbc:filter[schema=tenant_123]:jdbc:postgresql://localhost/db
 * -- SELECT * FROM users → SELECT * FROM tenant_123.users
 * </pre>
 *
 * <h3>WHERE Clause</h3>
 * <p>Append condition to all SELECT/UPDATE/DELETE statements:</p>
 * <pre>
 * jdbc:filter[where=deleted=false]:jdbc:mysql://localhost/db
 * -- SELECT * FROM users → SELECT * FROM users WHERE deleted=false
 * -- SELECT * FROM users WHERE active=true → SELECT * FROM users WHERE active=true AND deleted=false
 * </pre>
 *
 * <h3>Rename Identifiers</h3>
 * <p>Rename table or column names (case-insensitive):</p>
 * <pre>
 * jdbc:filter[rename.old_table=new_table]:jdbc:h2:mem:test
 * jdbc:filter[rename.users=customers,rename.created=created_at]:jdbc:...
 * </pre>
 *
 * <h3>Combining Transformers</h3>
 * <p>Multiple transformers are applied in order:</p>
 * <pre>
 * jdbc:filter[schema=acme,where=active=true,rename.old=new]:jdbc:...
 * </pre>
 *
 * <h2>Custom Transformer Class</h2>
 * <p>For complex transformations, specify a custom transformer class:</p>
 * <pre>
 * jdbc:filter[class=com.example.MyTransformer]:jdbc:postgresql://localhost/db
 * </pre>
 *
 * <p>The transformer class must implement {@link JdbcTransformer} and have a
 * no-argument constructor.</p>
 *
 * <h2>Per-Connection Transformers</h2>
 * <p>Each connection gets its own transformer instance. This ensures thread-safety
 * and predictable behavior in connection-pooled environments.</p>
 *
 * @see JdbcTransformer
 * @see AbstractJdbcTransformer
 * @see SchemaTransformer
 * @see WhereTransformer
 * @see RenameTransformer
 */
@DriverCapability(
    prefix = "filter",
    description = "Transforms SQL statements using built-in or custom transformers",
    capabilities = {"transformation", "filtering"}
)
@DriverParameter(name = "class", type = ParameterType.STRING,
    description = "Fully qualified class name of custom JdbcTransformer implementation")
@DriverParameter(name = "schema", type = ParameterType.STRING,
    description = "Schema prefix to add to unqualified table names")
@DriverParameter(name = "where", type = ParameterType.STRING,
    description = "Condition to append to WHERE clauses (e.g., deleted=false)")
@DriverParameter(name = "rename.*", type = ParameterType.STRING,
    description = "Rename identifiers: rename.OLD_NAME=NEW_NAME")
public class FilterDriver extends AbstractProxyDriver {
    private static final Logger LOG = Logger.getLogger(FilterDriver.class.getName());

    static {
        try {
            DriverManager.registerDriver(new FilterDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ThreadLocal for backward compatibility with setTransformer() API.
     * @deprecated Use URL parameter class= instead. This field will be removed in a future version.
     */
    @Deprecated
    protected ThreadLocal<JdbcTransformer> transformer =
        ThreadLocal.withInitial(() -> null);

    protected boolean acceptsSubProtocol(String subprotocol) {
        return "filter".equals(subprotocol);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        Connection delegate = DriverManager.getConnection(subname(url), info);
        return proxyConnection(delegate, url, info, this);
    }

    @Override
    protected Connection proxyConnection(Connection delegate, String url, Properties info, Driver driver) throws SQLException {
        JdbcTransformer xformer = resolveTransformer(url);
        return new FilterConnection(delegate, this, url, info, xformer);
    }

    /**
     * Resolve the transformer for a connection.
     * Priority: 1) Custom class, 2) Built-in transformers, 3) ThreadLocal (deprecated), 4) pass-through
     */
    private JdbcTransformer resolveTransformer(String url) throws SQLException {
        JdbcUrlParser parser = JdbcUrlParser.parse(url);

        // First, try custom class parameter
        String className = parser.getParameter("class");
        if (className != null && !className.isEmpty()) {
            return instantiateTransformer(className);
        }

        // Second, try built-in transformers
        JdbcTransformer builtIn = createBuiltInTransformers(parser);
        if (builtIn != null) {
            return builtIn;
        }

        // Third, check ThreadLocal (deprecated backward compatibility)
        JdbcTransformer threadLocalTransformer = transformer.get();
        if (threadLocalTransformer != null) {
            LOG.warning(() -> "FilterDriver: Using deprecated setTransformer() API. " +
                "Migrate to URL parameter: jdbc:filter[class=...]:...");
            return threadLocalTransformer;
        }

        // Default: pass-through transformer
        return new AbstractJdbcTransformer() {};
    }

    /**
     * Create built-in transformers from URL parameters.
     *
     * @return a transformer (possibly composite), or null if no built-in params found
     */
    private JdbcTransformer createBuiltInTransformers(JdbcUrlParser parser) throws SQLException {
        CompositeTransformer composite = new CompositeTransformer();

        // Schema prefix transformer
        String schema = parser.getParameter("schema");
        if (schema != null && !schema.isEmpty()) {
            composite.add(new SchemaTransformer(schema));
            LOG.fine(() -> "FilterDriver: Added SchemaTransformer with prefix: " + schema);
        }

        // WHERE clause transformer
        String where = parser.getParameter("where");
        if (where != null && !where.isEmpty()) {
            composite.add(new WhereTransformer(where));
            LOG.fine(() -> "FilterDriver: Added WhereTransformer with condition: " + where);
        }

        // Rename transformers (rename.* parameters)
        RenameTransformer renamer = null;
        for (Map.Entry<String, String> entry : parser.getParameters().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("rename.") && key.length() > 7) {
                String oldName = key.substring(7); // Remove "rename." prefix
                String newName = entry.getValue();
                if (oldName != null && !oldName.isEmpty() && newName != null && !newName.isEmpty()) {
                    if (renamer == null) {
                        renamer = new RenameTransformer();
                    }
                    renamer.addRename(oldName, newName);
                    final String old = oldName;
                    LOG.fine(() -> "FilterDriver: Added rename rule: " + old + " → " + newName);
                }
            }
        }
        if (renamer != null) {
            composite.add(renamer);
        }

        // Return composite if it has any transformers, null otherwise
        return composite.isEmpty() ? null : (composite.size() == 1 ? extractSingle(composite) : composite);
    }

    /**
     * Extract single transformer from composite if it only has one.
     * Avoids unnecessary wrapping.
     */
    private JdbcTransformer extractSingle(CompositeTransformer composite) {
        // We can't easily extract, so just return the composite
        // The overhead is minimal
        return composite;
    }

    /**
     * Instantiate a JdbcTransformer from a class name.
     */
    private JdbcTransformer instantiateTransformer(String className) throws SQLException {
        try {
            Class<?> clazz = Class.forName(className);
            if (!JdbcTransformer.class.isAssignableFrom(clazz)) {
                throw new SQLException("Class " + className + " does not implement JdbcTransformer");
            }
            return (JdbcTransformer) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new SQLException("Transformer class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to instantiate transformer: " + className, e);
        }
    }

    /**
     * Transform SQL and log if the SQL was modified.
     */
    private static String transformAndLog(JdbcTransformer xformer, String sql) throws SQLException {
        String transformed = xformer.transformSql(sql);
        if (!sql.equals(transformed)) {
            LOG.fine(() -> String.format("FilterDriver: SQL transformed%n  Original: %s%n  Transformed: %s",
                sql, transformed));
            PjdbcListeners.fireSqlTransformed(sql, transformed);
        }
        return transformed;
    }

    @Override
    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {
        FilterConnection filterConn = (FilterConnection) conn;
        final JdbcTransformer xformer = filterConn.getTransformer();
        final AbstractProxyDriver driver = this;
        return new AbstractStatement(delegate, conn) {
            @Override
            protected ResultSet wrap(ResultSet r) throws SQLException {
                return driver.proxyResultSet(this, r);
            }

            public void addBatch(String sql) throws SQLException {
                super.addBatch(transformAndLog(xformer, sql));
            }

            public boolean execute(String sql) throws SQLException {
                return super.execute(transformAndLog(xformer, sql));
            }

            public boolean execute(String sql, int[] columnIndexes) throws SQLException {
                return super.execute(transformAndLog(xformer, sql), columnIndexes);
            }

            public boolean execute(String sql, String[] columnNames) throws SQLException {
                return super.execute(transformAndLog(xformer, sql), columnNames);
            }

            public ResultSet executeQuery(String sql) throws SQLException {
                return super.executeQuery(transformAndLog(xformer, sql));
            }

            public int executeUpdate(String sql) throws SQLException {
                return super.executeUpdate(transformAndLog(xformer, sql));
            }

            public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
                return super.executeUpdate(transformAndLog(xformer, sql), autoGeneratedKeys);
            }

            public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
                return super.executeUpdate(transformAndLog(xformer, sql), columnIndexes);
            }

            public int executeUpdate(String sql, String[] columnNames) throws SQLException {
                return super.executeUpdate(transformAndLog(xformer, sql), columnNames);
            }
        };
    }

    /**
     * Connection wrapper that holds the transformer for this connection.
     */
    private static class FilterConnection extends AbstractConnection {
        private final JdbcTransformer transformer;

        FilterConnection(Connection conn, Driver driver, String url, Properties info, JdbcTransformer transformer) throws SQLException {
            super(conn, driver, url, info);
            this.transformer = transformer;
        }

        JdbcTransformer getTransformer() {
            return transformer;
        }

        @Override
        public Statement createStatement() throws SQLException {
            FilterDriver filterDriver = (FilterDriver) getDriver();
            return filterDriver.proxyStatement(getDelegate().createStatement(), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            FilterDriver filterDriver = (FilterDriver) getDriver();
            return filterDriver.proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency), this);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            FilterDriver filterDriver = (FilterDriver) getDriver();
            return filterDriver.proxyStatement(getDelegate().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);
        }
    }

    /**
     * Get the current transformer for this thread.
     * @return the JdbcTransformer used for SQL transformation, or null if not set
     * @deprecated Use URL parameter class= instead. Per-thread transformers are confusing
     *             in pooled connection scenarios.
     */
    @Deprecated
    public JdbcTransformer getTransformer() {
        return transformer.get();
    }

    /**
     * Set the transformer for SQL transformation for this thread.
     * @param transformer the JdbcTransformer to use
     * @deprecated Use URL parameter class= instead:
     *             jdbc:filter[class=com.example.MyTransformer]:jdbc:...
     *             Per-thread transformers cause confusion in pooled connection scenarios.
     */
    @Deprecated
    public void setTransformer(JdbcTransformer transformer) {
        this.transformer.set(transformer);
    }
}
