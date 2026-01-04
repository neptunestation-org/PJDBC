package org.pjdbc.drivers;

import java.sql.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.sql.*;

@DriverCapability(
    prefix = "cat",
    description = "Pass-through driver that forwards all calls unchanged",
    capabilities = {"passthrough"}
)
public class CatDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new CatDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "cat".equals(subprotocol);}}

