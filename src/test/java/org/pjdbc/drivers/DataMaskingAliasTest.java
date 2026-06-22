package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingAliasTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testAliasBypass() throws SQLException {
        // Mask 'ssn' column
        String url = "jdbc:mask[columns=ssn]:jdbc:h2:mem:mask_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(20))");
                stmt.execute("INSERT INTO users VALUES (1, '123-456-7890')");

                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_id FROM users")) {
                    if (rs.next()) {
                        String value = rs.getString("public_id");
                        // If it's NOT masked, then the bypass worked
                        if ("123-456-7890".equals(value)) {
                            fail("Bypass succeeded: Aliased sensitive column was not masked when retrieved by label");
                        }
                    }
                }
            }
        }
    }
}
