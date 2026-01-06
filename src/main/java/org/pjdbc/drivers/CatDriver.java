package org.pjdbc.drivers;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.sql.AbstractProxyDriver;

/**
 * Pass-through driver that forwards all calls unchanged to the target driver.
 *
 * <p>CatDriver is the simplest PJDBC driver - it does nothing but delegate to
 * the wrapped driver. It serves as:
 * <ul>
 *   <li>A template for creating new drivers</li>
 *   <li>A base for composition when you only need other drivers' behaviors</li>
 *   <li>A testing aid to verify proxy infrastructure works correctly</li>
 * </ul>
 *
 * <p>URL format: {@code jdbc:cat:jdbc:target:...}
 *
 * <p>Example:
 * <pre>
 * jdbc:cat:jdbc:postgresql://localhost/mydb
 * </pre>
 */
@DriverCapability(
    prefix = "cat",
    description = "Pass-through driver that forwards all calls unchanged",
    capabilities = {"passthrough"}
)
public class CatDriver extends AbstractProxyDriver {

    static {
        try {
            DriverManager.registerDriver(new CatDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean acceptsSubProtocol(String subprotocol) {
        return "cat".equals(subprotocol);
    }
}
