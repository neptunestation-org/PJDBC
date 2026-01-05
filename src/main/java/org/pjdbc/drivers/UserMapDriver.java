package org.pjdbc.drivers;

import java.sql.*;
import java.util.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.*;

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
	    if (cl == null) cl = UserMapDriver.class.getClassLoader();
	    java.io.InputStream is = cl.getResourceAsStream("org.pjdbc.UserMapDriver.UserMapFile");
	    if (is != null) {p.load(is); is.close();}
	    DriverManager.registerDriver(new UserMapDriver());
	} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubProtocol (String subprotocol) {
	return "mapuser".equals(subprotocol);}

    public Connection connect (String url, Properties info) throws SQLException {
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
	return DriverManager.getConnection(subname(url), delegateInfo);}}
