
package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

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
