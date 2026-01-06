package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.AbstractProxyDriver;

/**
 * Replicates write operations across multiple database connections.
 *
 * <p>TeeDriver sends all operations to two database connections simultaneously,
 * useful for scenarios like:
 * <ul>
 *   <li>Write replication to a secondary database</li>
 *   <li>Dual-write during migration periods</li>
 *   <li>Keeping audit databases in sync</li>
 * </ul>
 *
 * <p><strong>Warning:</strong> TeeDriver does not provide transactional guarantees
 * across the two connections. If one write succeeds and the other fails, the
 * databases will be inconsistent.
 *
 * <p>URL format: {@code jdbc:tee:jdbc:target1:...;jdbc:target2:...}
 *
 * <p>Example:
 * <pre>
 * jdbc:tee:jdbc:postgresql://primary/db;jdbc:postgresql://replica/db
 * </pre>
 */
@DriverCapability(
    prefix = "tee",
    description = "Replicates operations across multiple database connections",
    capabilities = {"replication"}
)
@DriverSideEffects(stateful = true)
public class TeeDriver extends AbstractProxyDriver {

    static {
        try {
            DriverManager.registerDriver(new TeeDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubName(String subname) {
        if (("" + subname).split(";").length != 2) {
            return false;
        }
        try {
            for (String url : subname.split(";")) {
                if (DriverManager.getDriver(url.trim()) == null) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "tee".equals(subprotocol);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        ArrayList<String> urls = new ArrayList<>(Arrays.asList(subname(url).split(";")));
        ArrayList<Connection> delegates = new ArrayList<>();
        for (String s : urls) {
            delegates.add(DriverManager.getConnection(s, info));
        }
        return proxyConnection(urls.get(0), info, delegates.toArray(new Connection[0]));
    }
}
