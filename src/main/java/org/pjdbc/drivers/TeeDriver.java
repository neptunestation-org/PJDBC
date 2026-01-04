package org.pjdbc.drivers;

import java.sql.*;
import java.util.*;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverSideEffects;
import org.pjdbc.sql.*;

@DriverCapability(
    prefix = "tee",
    description = "Replicates operations across multiple database connections",
    capabilities = {"replication"}
)
@DriverSideEffects(stateful = true)
public class TeeDriver extends AbstractProxyDriver {
    static {try {DriverManager.registerDriver(new TeeDriver());} catch (Exception e) {throw new RuntimeException(e);}}

    protected boolean acceptsSubName (String subname) {
        if ((""+subname).split(";").length!=2) return false;
        try{for (String url : subname.split(";")) if (DriverManager.getDriver(url.trim())==null) return false;}
        catch (Exception e) {return false;}
        return true;}

    protected boolean acceptsSubProtocol (String subprotocol) {
        return "tee".equals(subprotocol);}

    public Connection connect (String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        ArrayList<String> urls = new ArrayList<String>(Arrays.asList(subname(url).split(";")));
        ArrayList<Connection> delegates = new ArrayList<Connection>();
        for (String s : urls) delegates.add(DriverManager.getConnection(s, info));
        return proxyConnection(urls.get(0), info, delegates.toArray(new Connection[0]));}}
