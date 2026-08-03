package org.pjdbc.drivers;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractProxyDriver;

/**
 * Maps application usernames to database credentials.
 *
 * <p>UserMapDriver enables multi-tenant or role-based database access by translating
 * application-level usernames to actual database credentials. This is useful for:
 * <ul>
 *   <li>Hiding real database credentials from application code</li>
 *   <li>Implementing role-based access with different database users</li>
 *   <li>Multi-tenant scenarios where each tenant maps to different credentials</li>
 * </ul>
 *
 * <p>User mappings are loaded from a properties file on the classpath:
 * {@code org.pjdbc.UserMapDriver.UserMapFile}
 *
 * <p>Properties file format:
 * <pre>
 * app_user1=db_user/db_password
 * app_user2=other_user/other_password
 * </pre>
 *
 * <p>URL format: {@code jdbc:mapuser:jdbc:target:...}
 *
 * <p>Example:
 * <pre>
 * // Connect with app credentials, which get mapped to DB credentials
 * Properties info = new Properties();
 * info.setProperty("user", "app_user1");
 * info.setProperty("password", "ignored");
 * Connection conn = DriverManager.getConnection("jdbc:mapuser:jdbc:postgresql://localhost/db", info);
 * </pre>
 *
 * <p><strong>Security:</strong> Error messages are intentionally generic to prevent
 * user enumeration attacks. Missing users and invalid mappings produce the same error.
 */
@DriverCapability(
    prefix = "mapuser",
    description = "Maps application usernames to database credentials",
    capabilities = {"security", "transformation"}
)
@DriverSideEffects(filesystem = true)
public class UserMapDriver extends AbstractProxyDriver {

    private static final Properties p = new Properties();

    static {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = UserMapDriver.class.getClassLoader();
            }
            InputStream is = cl.getResourceAsStream("org.pjdbc.UserMapDriver.UserMapFile");
            if (is != null) {
                try {
                    p.load(is);
                } finally {
                    is.close();
                }
            }
            DriverManager.registerDriver(new UserMapDriver());
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "mapuser".equals(subprotocol);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;

        // Prevent sensitive data/stack trace leakage via NullPointerException when info is null.
        if (info == null) {
            throw new SQLException("PJDBC: Authentication failed");
        }

        String user = info.getProperty("user");
        // Prevent user enumeration by checking for null or empty user property.
        if (user == null || user.trim().isEmpty()) {
            throw new SQLException("PJDBC: Authentication failed");
        }

        String mapping = p.getProperty(user);
        // Prevent user enumeration by checking for a missing or empty mapping.
        if (mapping == null || mapping.trim().isEmpty()) {
            throw new SQLException("PJDBC: Authentication failed");
        }

        String[] credentials = mapping.split("/", 2);
        // Prevent DoS from malformed mappings and avoid leaking user existence.
        if (credentials.length < 2) {
            throw new SQLException("PJDBC: Authentication failed");
        }

        Properties delegateInfo = new Properties();
        delegateInfo.putAll(info);
        // Trim whitespace from credentials to prevent connection issues.
        delegateInfo.setProperty("user", credentials[0].trim());
        delegateInfo.setProperty("password", credentials[1].trim());
        return DriverManager.getConnection(subname(url), delegateInfo);
    }
}
