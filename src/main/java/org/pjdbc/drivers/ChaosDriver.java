package org.pjdbc.drivers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Random;

import org.pjdbc.sql.AbstractCallableStatement;
import org.pjdbc.sql.AbstractConnection;
import org.pjdbc.sql.AbstractPreparedStatement;
import org.pjdbc.sql.AbstractProxyDriver;
import org.pjdbc.sql.AbstractStatement;

public class ChaosDriver extends AbstractProxyDriver {

    private final Random random = new Random();



    static {

        try {

            DriverManager.registerDriver(new ChaosDriver());

        } catch (SQLException e) {

            throw new RuntimeException(e);

        }

    }



    @Override

    protected boolean acceptsSubProtocol(String subprotocol) {

        return "chaos".equals(subprotocol);

    }



        @Override



        protected boolean acceptsSubName(String subname) {



            return true;



        }



    



        private void introduceChaos(Connection conn) throws SQLException {



            String url = ((AbstractConnection) conn).getURL();



            double failureRate = Double.parseDouble(getUrlParameter(url, "failureRate", "0.0"));



            int latency = Integer.parseInt(getUrlParameter(url, "latency", "0"));



    



            if (random.nextDouble() < failureRate) {



                throw new SQLException("ChaosDriver: Intentionally failing the query.");



            }



    



            if (latency > 0) {



                try {



                    Thread.sleep(latency);



                } catch (InterruptedException e) {



                    Thread.currentThread().interrupt();



                    throw new SQLException("ChaosDriver: Interrupted while introducing latency.", e);



                }



            }



        }



    @Override

    protected Statement proxyStatement(Statement delegate, Connection conn) throws SQLException {

        return new AbstractStatement(delegate, conn) {

            @Override

            public boolean execute(String sql) throws SQLException {

                introduceChaos(getConnection());

                return super.execute(sql);

            }



            @Override

            public ResultSet executeQuery(String sql) throws SQLException {

                introduceChaos(getConnection());

                return super.executeQuery(sql);

            }



            @Override

            public int executeUpdate(String sql) throws SQLException {

                introduceChaos(getConnection());

                return super.executeUpdate(sql);

            }

        };

    }



    @Override

    protected PreparedStatement proxyPreparedStatement(PreparedStatement delegate, Connection conn) throws SQLException {

        return new AbstractPreparedStatement(delegate, conn) {

            @Override

            public boolean execute() throws SQLException {

                introduceChaos(getConnection());

                return super.execute();

            }



            @Override

            public ResultSet executeQuery() throws SQLException {

                introduceChaos(getConnection());

                return super.executeQuery();

            }



            @Override

            public int executeUpdate() throws SQLException {

                introduceChaos(getConnection());

                return super.executeUpdate();

            }

        };

    }



    @Override

    protected CallableStatement proxyCallableStatement(CallableStatement delegate, Connection conn) throws SQLException {

        return new AbstractCallableStatement(delegate, conn) {

            @Override

            public boolean execute() throws SQLException {

                introduceChaos(getConnection());

                return super.execute();

            }



            @Override

            public ResultSet executeQuery() throws SQLException {

                introduceChaos(getConnection());

                return super.executeQuery();

            }



            @Override

            public int executeUpdate() throws SQLException {

                introduceChaos(getConnection());

                return super.executeUpdate();

            }

        };

    }

}