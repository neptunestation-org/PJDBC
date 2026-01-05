package org.pjdbc.drivers;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;
import org.pjdbc.sql.AbstractJdbcTransformer;
import org.pjdbc.sql.JdbcTransformer;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.sql.PjdbcListeners;

/**
 * FilterDriver transforms SQL statements using a configurable JdbcTransformer.
 *
 * <h2>Configuration</h2>
 * <p>Specify the transformer class via URL parameter:</p>
 * <pre>
 * jdbc:filter[class=com.example.MyTransformer]:jdbc:postgresql://localhost/db
 * </pre>
 *
 * <p>The transformer class must implement {@link JdbcTransformer} and have a
 * no-argument constructor. If no class is specified, a pass-through transformer
 * is used (SQL is not modified).</p>
 *
 * <h2>Per-Connection Transformers</h2>
 * <p>Each connection gets its own transformer instance. This ensures thread-safety
 * and predictable behavior in connection-pooled environments.</p>
 *
 * @see JdbcTransformer
 * @see AbstractJdbcTransformer
 */
@DriverCapability(
    prefix = "filter",
    description = "Transforms SQL statements using a configurable JdbcTransformer",
    capabilities = {"transformation", "filtering"}
)
@DriverParameter(name = "class", type = ParameterType.STRING,
    description = "Fully qualified class name of JdbcTransformer implementation")
public class FilterDriver extends AbstractProxyDriver {
    private static final Logger LOG = Logger.getLogger(FilterDriver.class.getName());

    static {try {DriverManager.registerDriver(new FilterDriver());} catch (Exception e) {throw new RuntimeException(e);}}

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
     * Priority: 1) URL class parameter, 2) ThreadLocal (deprecated), 3) default pass-through
     */
    private JdbcTransformer resolveTransformer(String url) throws SQLException {
        // First, try URL parameter
        JdbcUrlParser parser = JdbcUrlParser.parse(url);
        String className = parser.getParameter("class");
        if (className != null && !className.isEmpty()) {
            return instantiateTransformer(className);
        }

        // Second, check ThreadLocal (deprecated backward compatibility)
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
