package com.omnicorps.global.pjdbc; // Generated package name

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;

public interface SQLHandler {
    public ResultSet execute (String sql, Connection connection)
	throws SQLException;
}