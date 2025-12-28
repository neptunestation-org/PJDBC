package org.pjdbc.sql;

import java.util.*;
import java.util.regex.*;

/**
 * Parser for PJDBC URLs with optional parameter support.
 *
 * URL format: jdbc:subprotocol[param1=value1,param2=value2]:subname
 *
 * Examples:
 * - jdbc:filter:jdbc:mock:foo
 * - jdbc:filter[class=com.example.MyFilter]:jdbc:mock:foo
 * - jdbc:pool[min=5,max=20]:jdbc:postgresql://localhost/db
 */
public class JdbcUrlParser {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(jdbc):([a-zA-Z][a-zA-Z0-9]*)(\\[([^\\]]*)\\])?:(.+)$"
    );

    private final String protocol;
    private final String subprotocol;
    private final String subname;
    private final Map<String, String> parameters;

    private JdbcUrlParser(String protocol, String subprotocol, String subname, Map<String, String> parameters) {
        this.protocol = protocol;
        this.subprotocol = subprotocol;
        this.subname = subname;
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    /**
     * Parse a JDBC URL into its components.
     *
     * @param url the JDBC URL to parse
     * @return a JdbcUrlParser instance containing the parsed components
     * @throws IllegalArgumentException if the URL is null or invalid
     */
    public static JdbcUrlParser parse(String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }

        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid JDBC URL: " + url);
        }

        String protocol = matcher.group(1);
        String subprotocol = matcher.group(2);
        String paramString = matcher.group(4); // may be null
        String subname = matcher.group(5);

        Map<String, String> parameters = parseParameters(paramString);

        return new JdbcUrlParser(protocol, subprotocol, subname, parameters);
    }

    private static Map<String, String> parseParameters(String paramString) {
        Map<String, String> params = new LinkedHashMap<>();

        if (paramString == null || paramString.isEmpty()) {
            return params;
        }

        // Split on comma, but be careful with values that might contain commas
        // For now, simple split - can be enhanced later for escaped commas
        String[] pairs = paramString.split(",");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex).trim();
                String value = pair.substring(eqIndex + 1).trim();
                params.put(key, value);
            }
        }

        return params;
    }

    /**
     * Get the protocol (always "jdbc").
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Get the subprotocol (e.g., "filter", "pool", "log").
     */
    public String getSubprotocol() {
        return subprotocol;
    }

    /**
     * Get the subname (everything after the subprotocol and parameters).
     */
    public String getSubname() {
        return subname;
    }

    /**
     * Get all parameters as an unmodifiable map.
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Get a parameter value by key.
     *
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    public String getParameter(String key) {
        return parameters.get(key);
    }

    /**
     * Get a parameter value by key with a default value.
     *
     * @param key the parameter key
     * @param defaultValue the default value if the parameter is not found
     * @return the parameter value, or the default value if not found
     */
    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append(":").append(subprotocol);
        if (!parameters.isEmpty()) {
            sb.append("[");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) sb.append(",");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("]");
        }
        sb.append(":").append(subname);
        return sb.toString();
    }
}
