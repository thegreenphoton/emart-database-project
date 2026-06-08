
package org.ivc.dbms.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:oracle:thin:@ehcss8v21jh8p7xk_tp?TNS_ADMIN=/Users/jaydenudall/Downloads/cs174a/Wallet_EHCSS8V21JH8P7XK";
    private static final String USER = "ADMIN";
    private static final String PASSWORD = "Scorpions001!";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}

