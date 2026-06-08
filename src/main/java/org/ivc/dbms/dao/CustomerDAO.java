package org.ivc.dbms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.ivc.dbms.model.Customer;
import org.ivc.dbms.util.DBConnection;

public class CustomerDAO {

    public static Customer login(
            String customerId,
            String password) {

        String sql = """
                SELECT customer_id,
                       first_name,
                       last_name,
                       status,
                       cart_id,
                       manager
                FROM Customers
                WHERE TRIM(customer_id) = TRIM(?)
                    AND TRIM(password) = TRIM(?)
                """;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setString(1, customerId);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Customer(
                        rs.getString("customer_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("status"),
                        rs.getString("cart_id"),
                        "Y".equals(rs.getString("manager"))
                );
            }
            

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}