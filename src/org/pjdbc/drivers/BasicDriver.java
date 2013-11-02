package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.pjdbc.util.AbstractProxyDriver;

public class BasicDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new BasicDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "basic".equals(subprotocol);}}

