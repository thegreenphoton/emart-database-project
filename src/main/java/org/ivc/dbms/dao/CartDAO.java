package org.ivc.dbms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.ivc.dbms.util.DBConnection;

public class CartDAO {
    public static boolean viewCart(Scanner scanner, String cartId) {
        
        String sql = """
            SELECT ci.stock_no, mp.manufacturer_name, mp.category, ci.cart_qty
            FROM cart_items ci INNER JOIN mart_products mp ON ci.stock_no = mp.stock_no
            WHERE cart_id = ?
        """;

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, cartId);

            ResultSet rs = stmt.executeQuery();

            boolean found = false;

            while (rs.next()) {
                found = true;
                System.out.println("\nStock Number: " + rs.getString("stock_no"));
                System.out.println(rs.getString("manufacturer_name").trim() + " " + rs.getString("category").trim());
                System.out.println("Quantity: " + rs.getString("cart_qty"));
            }

            if (!found) {
                System.out.println("No products found.");
                return false;
            } else {
                int subtotal = calculateCartSubtotal(cartId);
                System.out.println("\nCart Subtotal: $" + subtotal);
                return true;
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
        return false;
    }

    public static void addToCart(String cartId, String stockNo, int quantity) {
        String sql = """
            MERGE INTO cart_items ci
            USING (
                SELECT ? AS cart_id,
                    ? AS stock_no,
                    ? AS cart_qty
                FROM dual
            ) src
            ON (
                ci.cart_id = src.cart_id
                AND ci.stock_no = src.stock_no
            )
            WHEN MATCHED THEN
                UPDATE SET ci.cart_qty =
                    ci.cart_qty + src.cart_qty
            WHEN NOT MATCHED THEN
                INSERT (
                    cart_id,
                    stock_no,
                    cart_qty
                )
                VALUES (
                    src.cart_id,
                    src.stock_no,
                    src.cart_qty
                )
        """;

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, cartId);
            stmt.setString(2, stockNo);
            stmt.setInt(3, quantity);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Update successful! Added " + quantity + " of stock number " + stockNo + " to cart.");
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }

    public static int calculateCartSubtotal(String cartId) {
        String sql = """
            SELECT SUM(mp.price * ci.cart_qty) AS total
            FROM cart_items ci INNER JOIN mart_products mp ON ci.stock_no = mp.stock_no
            WHERE ci.cart_id = ?
        """;

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, cartId);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }

        return 0;
    }

    // Remove specified quantity of stockNo from cartId.
    // Validates existing quantity; if removeQty == existing, deletes the row; otherwise updates the quantity.
    public static void removeFromCart(String cartId, String stockNo, int removeQty) {
        if (stockNo == null || stockNo.isBlank()) {
            System.out.println("Stock number is required.");
            return;
        }
        if (removeQty <= 0) {
            System.out.println("Quantity to remove must be a positive integer.");
            return;
        }

        String selectSql = "SELECT cart_qty FROM cart_items WHERE cart_id = ? AND stock_no = ?";
        String updateSql = "UPDATE cart_items SET cart_qty = ? WHERE cart_id = ? AND stock_no = ?";
        String deleteSql = "DELETE FROM cart_items WHERE cart_id = ? AND stock_no = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {

            selectStmt.setString(1, cartId);
            selectStmt.setString(2, stockNo);
            ResultSet rs = selectStmt.executeQuery();
            if (!rs.next()) {
                System.out.println("Item " + stockNo + " not found in cart " + cartId + ".");
                return;
            }

            int currentQty = rs.getInt("cart_qty");
            if (removeQty > currentQty) {
                System.out.println("Cannot remove more than existing quantity (" + currentQty + ").");
                return;
            }

            try {
                conn.setAutoCommit(false);

                if (removeQty == currentQty) {
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setString(1, cartId);
                        deleteStmt.setString(2, stockNo);
                        deleteStmt.executeUpdate();
                        System.out.println("Removed all of " + stockNo + " from cart.");
                    }
                } else {
                    int newQty = currentQty - removeQty;
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, newQty);
                        updateStmt.setString(2, cartId);
                        updateStmt.setString(3, stockNo);
                        updateStmt.executeUpdate();
                        System.out.println("Removed " + removeQty + " of " + stockNo + ". New quantity: " + newQty);
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Database error during remove operation. Rolled back.");
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error while accessing cart.");
            e.printStackTrace();
        }
    }


    // Checkout: display cart contents & total, confirm, then clear the cart.
    public static void checkout(Scanner scanner, String customerId, String cartId) {
        String sql = """
            SELECT ci.stock_no, ci.cart_qty, mp.price
            FROM cart_items ci
            JOIN mart_products mp ON ci.stock_no = mp.stock_no
            WHERE ci.cart_id = ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cartId);
            ResultSet rs = stmt.executeQuery();

            class Line { String stock; int qty; double price; }
            List<Line> lines = new ArrayList<>();
            double total = 0.0;

            while (rs.next()) {
                Line l = new Line();
                l.stock = rs.getString("stock_no");
                l.qty = rs.getInt("cart_qty");
                l.price = rs.getDouble("price");
                total += l.qty * l.price;
                lines.add(l);
            }

            if (lines.isEmpty()) {
                System.out.println("Cart is empty. Nothing to checkout.");
                return;
            }

            String selWhQty = "SELECT Wh_qty FROM Wh_products WHERE stock_no = ?";
            List<String> shortages = new ArrayList<>();
            try (PreparedStatement whStmt = conn.prepareStatement(selWhQty)) {
                for (Line l : lines) {
                    whStmt.setString(1, l.stock);
                    try (ResultSet wrs = whStmt.executeQuery()) {
                        int avail = wrs.next() ? wrs.getInt("Wh_qty") : 0;
                        if (avail < l.qty) {
                            shortages.add(l.stock + " (need " + l.qty + ", avail " + avail + ")");
                        }
                    }
                }
            }

            if (!shortages.isEmpty()) {
                System.out.println("Cannot proceed to checkout due to inventory shortages:");
                shortages.forEach(System.out::println);
                return;
            }

            System.out.println("\nCheckout summary:");
            for (Line l : lines) {
                System.out.printf("- %s  qty: %d  unit: $%.2f  line: $%.2f%n",
                    l.stock, l.qty, l.price, l.qty * l.price);
            }
            System.out.printf("Total: $%.2f%n", total);

            System.out.print("Proceed to checkout? (y/n): ");
            String confirm = scanner.nextLine().trim();
            if (!confirm.equalsIgnoreCase("y")) {
                System.out.println("Checkout cancelled.");
                return;
            }

            OrderDAO.createOrder(scanner, customerId, cartId);

            // Simple checkout: clear cart. Wrap in transaction so it can be extended later.
            String deleteSql = "DELETE FROM cart_items WHERE cart_id = ?";
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
                    delStmt.setString(1, cartId);
                    delStmt.executeUpdate();
                }
                conn.commit();
                System.out.printf("Checkout complete. Charged: $%.2f. Cart cleared.%n", total);
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Error during checkout. Transaction rolled back.");
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error while processing checkout.");
            e.printStackTrace();
        }
    }
}