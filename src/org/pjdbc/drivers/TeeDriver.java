package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.pjdbc.util.AbstractProxyDriver;

public class TeeDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new TeeDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "tee".equals(subprotocol);}}

