package org.pjdbc.sql;

import java.sql.*;
import java.util.*;
import java.util.logging.*;

public abstract class AbstractDriver implements Driver {

    /**
     * Parse the URL and return the JdbcUrlParser, or null if invalid.
     */
    protected JdbcUrlParser parseUrl(String url) {
        try {
            return JdbcUrlParser.parse(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected String protocol (String url) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getProtocol() : null;
    }

    protected String subprotocol (String url) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getSubprotocol() : null;
    }

    protected String subname (String url) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getSubname() : null;
    }

    /**
     * Get a URL parameter value.
     * @param url the JDBC URL
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    protected String getUrlParameter(String url, String key) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getParameter(key) : null;
    }

    /**
     * Get a URL parameter value with a default.
     * @param url the JDBC URL
     * @param key the parameter key
     * @param defaultValue the default value if not found
     * @return the parameter value, or the default if not found
     */
    protected String getUrlParameter(String url, String key, String defaultValue) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getParameter(key, defaultValue) : defaultValue;
    }

    /**
     * Get all URL parameters as a map.
     * @param url the JDBC URL
     * @return unmodifiable map of parameters, empty if none
     */
    protected Map<String, String> getUrlParameters(String url) {
        JdbcUrlParser parser = parseUrl(url);
        return parser != null ? parser.getParameters() : Collections.emptyMap();
    }

    protected boolean acceptsProtocol (String protocol) {return "jdbc".equals(protocol);}

    protected abstract boolean acceptsSubProtocol (String subprotocol);

    protected abstract boolean acceptsSubName (String subname);

    @Override
    public boolean acceptsURL (String url) {
        JdbcUrlParser parser = parseUrl(url);
        if (parser == null) return false;
        if (!acceptsProtocol(parser.getProtocol())) return false;
        if (!acceptsSubProtocol(parser.getSubprotocol())) return false;
        if (!acceptsSubName(parser.getSubname())) return false;
        return true;
    }

    @Override
    public int getMajorVersion () {return 1;}

    @Override
    public int getMinorVersion () {return 0;}

    @Override
    public Logger getParentLogger () throws SQLFeatureNotSupportedException {throw new SQLFeatureNotSupportedException();}

    @Override
    public DriverPropertyInfo[] getPropertyInfo (String url, Properties info) throws SQLException {return new DriverPropertyInfo[]{};}

    @Override
    public boolean jdbcCompliant () {return false;}
}
