
package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverDependency;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverParameter.ParameterType;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractProxyDriver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


/**
 * A PJDBC driver that provides connection pooling using HikariCP.
 *
 * The driver URL format is `jdbc:hikaricp:<real_jdbc_url>`.
 *
 * HikariCP configuration properties can be passed in the URL query string.
 * For example:
 * `jdbc:hikaricp:jdbc:h2:mem:test?maximumPoolSize=10`
 */
@DriverCapability(
    prefix = "hikaricp",
    description = "Connection pooling using HikariCP",
    capabilities = {"pooling"}
)
@DriverParameter(name = "maximumPoolSize", type = ParameterType.INTEGER,
    description = "Maximum pool size", defaultValue = "10", min = 1)
@DriverParameter(name = "minimumIdle", type = ParameterType.INTEGER,
    description = "Minimum idle connections", defaultValue = "10", min = 0)
@DriverParameter(name = "connectionTimeout", type = ParameterType.INTEGER,
    description = "Connection timeout in milliseconds", defaultValue = "30000", min = 250)
@DriverParameter(name = "idleTimeout", type = ParameterType.INTEGER,
    description = "Idle timeout in milliseconds", defaultValue = "600000", min = 10000)
@DriverParameter(name = "maxLifetime", type = ParameterType.INTEGER,
    description = "Max connection lifetime in milliseconds", defaultValue = "1800000", min = 30000)
@DriverDependency(groupId = "com.zaxxer", artifactId = "HikariCP", version = "5.1.0", optional = false)
@DriverSideEffects(stateful = true)
public class HikariPoolDriver extends AbstractProxyDriver {
    private static final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    static {
        try {
            DriverManager.registerDriver(new HikariPoolDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "hikaricp".equals(subprotocol);
    }

    @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            String jdbcUrl = subname(url);
            Properties properties = new Properties(info);
            properties.putAll(getUrlParameters(url));
            
                    HikariDataSource ds = dataSources.computeIfAbsent(jdbcUrl, key -> {
                        HikariConfig config = new HikariConfig(properties);
                        config.setJdbcUrl(key);
                        try {
                            config.setDriverClassName(DriverManager.getDriver(key).getClass().getName());
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        return new HikariDataSource(config);
                    });
            
                    return proxyConnection(ds.getConnection(), jdbcUrl, properties, this);
                }
    @Override
    protected boolean acceptsSubName(String subname) {
        return true;
    }
}
