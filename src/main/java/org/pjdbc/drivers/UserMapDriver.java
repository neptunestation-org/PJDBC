package org.pjdbc.drivers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            URL url = cl.getResource("org.pjdbc.UserMapDriver.UserMapFile");
            if (url != null) {
                warnOnInsecurePermissions(url);
                try (InputStream is = url.openStream()) {
                    p.load(is);
                }
            }
            DriverManager.registerDriver(new UserMapDriver());
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void warnOnInsecurePermissions(URL url) {
        try {
            if (!"file".equals(url.getProtocol())) {
                return;
            }
            Path path = Paths.get(url.toURI());
            if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                return; // Not a POSIX-compliant file system, cannot check permissions.
            }
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            if (perms.contains(PosixFilePermission.GROUP_READ) ||
                perms.contains(PosixFilePermission.OTHERS_READ) ||
                perms.contains(PosixFilePermission.GROUP_WRITE) ||
                perms.contains(PosixFilePermission.OTHERS_WRITE) ||
                perms.contains(PosixFilePermission.GROUP_EXECUTE) ||
                perms.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                Logger.getLogger(UserMapDriver.class.getName()).log(
                    Level.SEVERE,
                    "CRITICAL SECURITY WARNING: Credential file is world-readable or group-readable: " + path +
                    ". This is a security risk. Please restrict permissions to the owner (e.g., 'chmod 600').");
            }
        } catch (Exception e) {
            // Log and ignore any errors during permission check, as it's a non-critical enhancement.
            Logger.getLogger(UserMapDriver.class.getName()).log(
                Level.WARNING, "Failed to check permissions on credential file: " + url, e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "mapuser".equals(subprotocol);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;

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
