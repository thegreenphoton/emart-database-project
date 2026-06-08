package org.ivc.dbms.dao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.ivc.dbms.util.DBConnection;

public class OrderDAO {
    public static void viewOrders(Scanner scanner, String customerId) {
        String sql = """
            SELECT order_no, order_date, total
            FROM cust_orders
            WHERE customer_id = ?
            ORDER BY order_date DESC
        """;

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, customerId);

            ResultSet rs = stmt.executeQuery();

            boolean found = false;

            while (rs.next()) {
                found = true;
                System.out.println("\nOrder Number: " + rs.getString("order_no"));
                System.out.println("Order Date: " + rs.getString("order_date"));
                System.out.println("Total: " + rs.getString("total"));
            }

            if (!found) {
                System.out.println("No orders found.");
            }

            System.out.print("\nEnter order number to view details (blank to return): ");
            String orderNo = scanner.nextLine().trim();
            if (!orderNo.isBlank()) {
                viewOrderDetails(scanner, orderNo);
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }

    public static void createOrder(Scanner scanner, String customerId, String cartId) {
      
        // Gather cart items
        String cartSql = """
            SELECT ci.stock_no, ci.cart_qty, mp.price
            FROM cart_items ci
            JOIN mart_products mp ON ci.stock_no = mp.stock_no
            WHERE ci.cart_id = ?
            """;

        class Line {
            String stockNo;
            int qty;
            double unitPrice;
            double lineTotal;
        }

        List<Line> lines = new ArrayList<>();
        double subtotal = 0.0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement cartStmt = conn.prepareStatement(cartSql)) {

            cartStmt.setString(1, cartId);
            try (ResultSet rs = cartStmt.executeQuery()) {
                while (rs.next()) {
                    Line l = new Line();
                    l.stockNo = rs.getString("stock_no");
                    l.qty = rs.getInt("cart_qty");
                    l.unitPrice = rs.getDouble("price");
                    l.lineTotal = l.qty * l.unitPrice;
                    subtotal += l.lineTotal;
                    lines.add(l);
                }
            }

            if (lines.isEmpty()) {
                System.out.println("Cart is empty. Nothing to checkout.");
                return;
            }

            // Determine customer membership / discount
            String membership = null;
            String memSql = "SELECT status FROM customers WHERE customer_id = ?";
            try (PreparedStatement memStmt = conn.prepareStatement(memSql)) {
                memStmt.setString(1, customerId);
                try (ResultSet rs = memStmt.executeQuery()) {
                    if (rs.next()) {
                        membership = rs.getString("status").trim();
                        System.out.println("Customer membership status: " + membership);
                    }
                }
            } catch (SQLException e) {
                // ignore and treat as NEW if missing
            }

            boolean isNew = (membership == null) || membership.equalsIgnoreCase("NEW");
            double discountRate = 0.0;
            if (isNew || (membership != null && membership.equalsIgnoreCase("GOLD"))) {
                discountRate = 0.10;
            } else if (membership != null && membership.equalsIgnoreCase("SILVER")) {
                discountRate = 0.05;
            }

            double discountAmount = round(subtotal * discountRate);

            // Shipping and handling: 10% of subtotal unless subtotal > 100 or customer is new
            boolean waiveShipping = (subtotal > 100.0) || isNew;
            double shipping = waiveShipping ? 0.0 : round(subtotal * 0.10);

            double total = round(subtotal - discountAmount + shipping);

            // Confirm with user
            System.out.println("\nOrder summary:");
            for (Line l : lines) {
                System.out.printf("- %s  qty: %d  unit: $%.2f  line: $%.2f%n", l.stockNo, l.qty, l.unitPrice, l.lineTotal);
            }
            System.out.printf("Subtotal: $%.2f%n", subtotal);
            System.out.printf("Discount (%.0f%%): -$%.2f%n", discountRate * 100, discountAmount);
            System.out.printf("Shipping & Handling: $%.2f%n", shipping);
            System.out.printf("Total: $%.2f%n", total);

            System.out.print("Confirm order? (y/n): ");
            String confirm = scanner.nextLine().trim();
            if (!confirm.equalsIgnoreCase("y")) {
                System.out.println("Checkout cancelled.");
                return;
            }

            String orderId = generateOrderId(20);
            String insertOrderSql = """
                INSERT INTO cust_orders (order_no, customer_id, order_date, subtotal, discount_amt, shipping_fee, total, policy_id)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, 'P1')
                """;
            String insertItemSql = """
                INSERT INTO cust_order_items (order_no, stock_no, qty, purchase_price)
                VALUES (?, ?, ?, ?)
                """;
            String deleteCartSql = "DELETE FROM cart_items WHERE cart_id = ?";
            String adjustInventorySql = "UPDATE Wh_products SET wh_qty = wh_qty - ? WHERE stock_no = ?";

            try {
                conn.setAutoCommit(false);

                try (PreparedStatement orderStmt = conn.prepareStatement(insertOrderSql)) {
                    orderStmt.setString(1, orderId);
                    orderStmt.setString(2, customerId);
                    orderStmt.setDouble(3, subtotal);
                    orderStmt.setDouble(4, discountAmount);
                    orderStmt.setDouble(5, shipping);
                    orderStmt.setDouble(6, total);
                    orderStmt.executeUpdate();
                }

                try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql)) {
                    for (Line l : lines) {
                        itemStmt.setString(1, orderId);
                        itemStmt.setString(2, l.stockNo);
                        itemStmt.setInt(3, l.qty);
                        itemStmt.setDouble(4, l.unitPrice); // purchase price = unit price at time of order
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }

                try (PreparedStatement delStmt = conn.prepareStatement(deleteCartSql)) {
                    delStmt.setString(1, cartId);
                    delStmt.executeUpdate();
                }

                try (PreparedStatement adjInvStmt = conn.prepareStatement(adjustInventorySql)) {
                    for (Line l : lines) {
                        adjInvStmt.setInt(1, l.qty);
                        adjInvStmt.setString(2, l.stockNo);
                        adjInvStmt.addBatch();
                    }
                    adjInvStmt.executeBatch();
                }
                conn.commit();
                System.out.println("Order confirmed. Order ID: " + orderId);
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Error creating order. Transaction rolled back.");
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error while creating order:");
            e.printStackTrace();
        }
    }

    public static void viewOrderDetails(Scanner scanner, String orderNo) {
        String orderSql = """
            SELECT order_no, customer_id, order_date, subtotal, discount_amt, shipping_fee, total
            FROM cust_orders
            WHERE order_no = ?
            """;

        String itemsSql = """
            SELECT coi.stock_no, coi.qty, coi.purchase_price,
                   mp.category, mp.manufacturer_name, mp.model_no
            FROM cust_order_items coi
            LEFT JOIN mart_products mp ON coi.stock_no = mp.stock_no
            WHERE coi.order_no = ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement orderStmt = conn.prepareStatement(orderSql);
             PreparedStatement itemsStmt = conn.prepareStatement(itemsSql)) {

            orderStmt.setString(1, orderNo);
            try (ResultSet ors = orderStmt.executeQuery()) {
                if (!ors.next()) {
                    System.out.println("Order " + orderNo + " not found.");
                    return;
                }
                String customerId = ors.getString("customer_id").trim();
                System.out.println("\nOrder: " + ors.getString("order_no"));
                System.out.println("Customer: " + (customerId == null ? "unknown" : customerId));
                System.out.println("Date: " + ors.getString("order_date"));
                System.out.printf("Subtotal: $%.2f%n", ors.getDouble("subtotal"));
                System.out.printf("Discount: $%.2f%n", ors.getDouble("discount_amt"));
                System.out.printf("Shipping & Handling: $%.2f%n", ors.getDouble("shipping_fee"));
                System.out.printf("Total: $%.2f%n", ors.getDouble("total"));
            }

            // collect items while printing so we can later add to cart if user chooses to reorder
            List<OrderItem> items = new ArrayList<>();
            itemsStmt.setString(1, orderNo);
            try (ResultSet irs = itemsStmt.executeQuery()) {
                boolean itemsFound = false;
                System.out.println("\nOrder items:");
                while (irs.next()) {
                    itemsFound = true;
                    String stock = irs.getString("stock_no");
                    int qty = irs.getInt("qty");
                    double price = irs.getDouble("purchase_price");
                    String man = irs.getString("manufacturer_name").trim();
                    String model = irs.getString("model_no").trim();
                    String cat = irs.getString("category").trim();
                    System.out.printf("- %s  qty: %d  unit: $%.2f  (%s %s %s)%n",
                        stock, qty, price,
                        cat == null ? "" : cat,
                        man == null ? "" : man,
                        model == null ? "" : model);
                    items.add(new OrderItem(stock, qty));
                }
                if (!itemsFound) {
                    System.out.println("No items recorded for this order.");
                }
            }

            // Offer reorder option
            System.out.print("\nReorder this order (add same items to cart)? (y/n): ");
            String ro = scanner.nextLine().trim();
            if (ro.equalsIgnoreCase("y")) {
                // need customer id and cart id
                String custId = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT customer_id FROM cust_orders WHERE order_no = ?")) {
                    ps.setString(1, orderNo);
                    try (ResultSet r = ps.executeQuery()) {
                        if (r.next()) custId = r.getString("customer_id").trim();
                    }
                }
                if (custId == null) {
                    System.out.println("Cannot determine customer for this order. Reorder aborted.");
                    return;
                }

                String cartId = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT cart_id FROM customers WHERE TRIM(customer_id) = ?")) {
                    ps.setString(1, custId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (r.next()) cartId = r.getString("cart_id");
                    }
                }
                if (cartId == null) {
                    System.out.println("Cannot determine cart for customer. Reorder aborted.");
                    return;
                }

                // upsert items into cart_items
                String selCartItem = "SELECT cart_qty FROM cart_items WHERE cart_id = ? AND stock_no = ?";
                String insertCartItem = "INSERT INTO cart_items (cart_id, stock_no, cart_qty) VALUES (?, ?, ?)";
                String updateCartItem = "UPDATE cart_items SET cart_qty = ? WHERE cart_id = ? AND stock_no = ?";


                try {
                    conn.setAutoCommit(false);
                    try (PreparedStatement selStmt = conn.prepareStatement(selCartItem);
                         PreparedStatement insStmt = conn.prepareStatement(insertCartItem);
                         PreparedStatement updStmt = conn.prepareStatement(updateCartItem)) {

                        for (OrderItem oi : items) {
                            selStmt.setString(1, cartId);
                            selStmt.setString(2, oi.stockNo);
                            try (ResultSet crs = selStmt.executeQuery()) {
                                if (crs.next()) {
                                    int existing = crs.getInt("cart_qty");
                                    int newQty = existing + oi.qty;
                                    updStmt.setInt(1, newQty);
                                    updStmt.setString(2, cartId);
                                    updStmt.setString(3, oi.stockNo);
                                    updStmt.executeUpdate();
                                } else {
                                    insStmt.setString(1, cartId);
                                    insStmt.setString(2, oi.stockNo);
                                    insStmt.setInt(3, oi.qty);
                                    insStmt.executeUpdate();
                                }
                            }
                        }
                    }
                    conn.commit();
                    System.out.println("Reorder added to cart " + cartId + ".");
                } catch (SQLException ex) {
                    try { conn.rollback(); } catch (Exception ignore) {}
                    System.out.println("Failed to add items to cart; transaction rolled back.");
                    ex.printStackTrace();
                } finally {
                    try { conn.setAutoCommit(true); } catch (Exception ignore) {}
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error while fetching order details:");
            e.printStackTrace();
        }
    }

    // simple rounding helper to 2 decimals
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

 // Generate a short alphanumeric order id (length <= 20)
    private static final String ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom ID_RANDOM = new SecureRandom();
    private static String generateOrderId(int length) {
        if (length <= 0 || length > 20) {
            throw new IllegalArgumentException("length must be 1..20");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = ID_RANDOM.nextInt(ID_ALPHABET.length());
            sb.append(ID_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private static class OrderItem { String stockNo; int qty; OrderItem(String s,int q){stockNo=s;qty=q;} }



}