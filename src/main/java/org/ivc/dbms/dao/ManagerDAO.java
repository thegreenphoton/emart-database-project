package org.ivc.dbms.dao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.ivc.dbms.util.DBConnection;

public class ManagerDAO {
    // Monthly summary: sales (quantity, revenue) per product and per category; top customer
    public static void monthlySalesSummary(Scanner scanner) {
        System.out.print("Enter year (YYYY): ");
        String year = scanner.nextLine().trim();
        System.out.print("Enter month (1-12): ");
        String month = scanner.nextLine().trim();

        String perProductSql = """
            SELECT coi.stock_no, mp.category, mp.manufacturer_name, mp.model_no,
                   SUM(coi.qty) AS total_qty, SUM(coi.qty * coi.purchase_price) AS revenue
            FROM cust_order_items coi
            JOIN cust_orders o ON coi.order_no = o.order_no
            LEFT JOIN mart_products mp ON coi.stock_no = mp.stock_no
            WHERE EXTRACT(YEAR FROM o.order_date) = ? AND EXTRACT(MONTH FROM o.order_date) = ?
            GROUP BY coi.stock_no, mp.category, mp.manufacturer_name, mp.model_no
            ORDER BY revenue DESC
            """;

        String perCategorySql = """
            SELECT mp.category, SUM(coi.qty) AS total_qty, SUM(coi.qty * coi.purchase_price) AS revenue
            FROM cust_order_items coi
            JOIN cust_orders o ON coi.order_no = o.order_no
            LEFT JOIN mart_products mp ON coi.stock_no = mp.stock_no
            WHERE EXTRACT(YEAR FROM o.order_date) = ? AND EXTRACT(MONTH FROM o.order_date) = ?
            GROUP BY mp.category
            ORDER BY revenue DESC
            """;

        String topCustomerSql = """
            SELECT o.customer_id, SUM(o.total) AS total_spent
            FROM cust_orders o
            WHERE EXTRACT(YEAR FROM o.order_date) = ? AND EXTRACT(MONTH FROM o.order_date) = ?
            GROUP BY o.customer_id
            ORDER BY total_spent DESC
            FETCH FIRST 1 ROWS ONLY
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement prodStmt = conn.prepareStatement(perProductSql);
             PreparedStatement catStmt = conn.prepareStatement(perCategorySql);
             PreparedStatement topCustStmt = conn.prepareStatement(topCustomerSql)) {

            prodStmt.setString(1, year);
            prodStmt.setString(2, month);
            try (ResultSet prs = prodStmt.executeQuery()) {
                System.out.println("\nSales by product:");
                boolean any = false;
                while (prs.next()) {
                    any = true;
                    System.out.printf("- %s | %s %s %s | qty: %d | revenue: $%.2f%n",
                        prs.getString("stock_no"),
                        prs.getString("category").trim(),
                        prs.getString("manufacturer_name").trim(),
                        prs.getString("model_no").trim(),
                        prs.getInt("total_qty"),
                        prs.getDouble("revenue"));
                }
                if (!any) System.out.println("No product sales for specified month.");
            }

            catStmt.setString(1, year);
            catStmt.setString(2, month);
            try (ResultSet crs = catStmt.executeQuery()) {
                System.out.println("\nSales by category:");
                boolean any = false;
                while (crs.next()) {
                    any = true;
                    System.out.printf("- %s | qty: %d | revenue: $%.2f%n",
                        crs.getString("category").trim(),
                        crs.getInt("total_qty"),
                        crs.getDouble("revenue"));
                }
                if (!any) System.out.println("No category sales for specified month.");
            }

            topCustStmt.setString(1, year);
            topCustStmt.setString(2, month);
            try (ResultSet trs = topCustStmt.executeQuery()) {
                if (trs.next()) {
                    System.out.printf("\nTop customer (ID): %s spent $%.2f%n",
                        trs.getString("customer_id").trim(), trs.getDouble("total_spent"));
                } else {
                    System.out.println("\nNo customer purchases recorded for that month.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Error generating monthly summary:");
            e.printStackTrace();
        }
    }

    public static void autoAdjustCustomerStatus() {
        String selectCustomers = "SELECT customer_id, status FROM customers";
        // get the last three orders for a customer, newest first
        String selectLastThree = "SELECT total FROM cust_orders WHERE customer_id = ? ORDER BY order_date DESC FETCH FIRST 3 ROWS ONLY";
        String updateSql = "UPDATE customers SET status = ? WHERE customer_id = ?";

        int updatedCount = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement selCustStmt = conn.prepareStatement(selectCustomers);
             PreparedStatement selOrdersStmt = conn.prepareStatement(selectLastThree);
             PreparedStatement updStmt = conn.prepareStatement(updateSql);
             ResultSet custRs = selCustStmt.executeQuery()) {

            while (custRs.next()) {
                String customerId = custRs.getString("customer_id");
                String currentMembership = custRs.getString("status").trim();

                selOrdersStmt.setString(1, customerId);
                try (ResultSet ordRs = selOrdersStmt.executeQuery()) {
                    double sumLastThree = 0.0;
                    while (ordRs.next()) {
                        sumLastThree += ordRs.getDouble("total");
                    }

                    String newStatus;
                    if (sumLastThree > 500.0) {
                        newStatus = "Gold";
                    } else if (sumLastThree > 100.0) {
                        newStatus = "Silver";
                    } else if (sumLastThree > 0.0) {
                        newStatus = "Green";
                    } else {
                        newStatus = "New";
                    }

                    if (!newStatus.equalsIgnoreCase(currentMembership == null ? "" : currentMembership)) {
                        updStmt.setString(1, newStatus);
                        updStmt.setString(2, customerId);
                        int rows = updStmt.executeUpdate();
                        if (rows > 0) updatedCount += rows; else System.out.println("Failed to update status for customer " + customerId.trim());
                        System.out.println("Customer " + customerId.trim() + " status changed: " +
                                           (currentMembership == null ? "null" : currentMembership) + " -> " + newStatus);
                    }
                }
            }

            System.out.println("Auto-adjust complete. Updated statuses for " + updatedCount + " customers.");

        } catch (SQLException e) {
            System.out.println("Error auto-adjusting customer statuses:");
            e.printStackTrace();
        }
    }

    // Manual adjustment of a single customer's status
    public static void manualAdjustCustomerStatus(Scanner scanner) {
        System.out.print("Enter customer id: ");
        String cid = scanner.nextLine().trim();
        if (cid.isBlank()) {
            System.out.println("Canceled.");
            return;
        }
        System.out.print("Enter new status (New/Green/Silver/Gold): ");
        String status = scanner.nextLine().trim();
        if (!status.matches("New|Green|Silver|Gold")) {
            System.out.println("Invalid status.");
            return;
        }

        String sql = "UPDATE customers SET status = ? WHERE TRIM(customer_id) = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, cid);
            int updated = stmt.executeUpdate();
            if (updated > 0) System.out.println("Customer " + cid.trim() + " set to " + status);
            else System.out.println("Customer not found.");
        } catch (SQLException e) {
            System.out.println("Error updating customer status:");
            e.printStackTrace();
        }
    }

    // Change product price
    public static void changeProductPrice(Scanner scanner) {
        System.out.print("Enter stock number: ");
        String stock = scanner.nextLine().trim();
        String[] validStockNums = {"AA00101", "AA00201", "AA00202", "AA00301", "AA00302", "AA00401", "AA00402", "AA00403", "AA00501", "AA00601", "AA00602"};

        if (!Arrays.stream(validStockNums).anyMatch(stock::equals)) {
                System.out.println("Invalid number. Please try again.");
                return;
            }
        if (stock.isBlank()) { System.out.println("Canceled."); return; }
        System.out.print("Enter new price (x.xx format): ");
        String p = scanner.nextLine().trim();
        double price;
        try { price = Double.parseDouble(p); if (price < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { System.out.println("Invalid price."); return; }

        String sql = "UPDATE mart_products SET price = ? WHERE stock_no = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setString(2, stock);
            int u = stmt.executeUpdate();
            if (u > 0) System.out.println("Updated price for " + stock + " to $" + price);
            else System.out.println("Product not found.");
        } catch (SQLException e) {
            System.out.println("Error updating price:");
            e.printStackTrace();
        }
    }

    // Delete orders that are not needed to compute customer status:
    // keep the latest 3 orders per customer; delete any older orders and their items
    public static void deleteOrdersNotNeededForStatus(Scanner scanner) {
        String findSql = """
            SELECT order_no FROM (
                SELECT order_no,
                       ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY order_date DESC) AS rn
                FROM cust_orders
            ) t
            WHERE t.rn > 3
            """;

        List<String> toDelete = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement findStmt = conn.prepareStatement(findSql);
             ResultSet rs = findStmt.executeQuery()) {

            while (rs.next()) {
                toDelete.add(rs.getString("order_no"));
            }

            if (toDelete.isEmpty()) {
                System.out.println("No orders are eligible for deletion (all orders are within last 3 per customer).");
                return;
            }

            System.out.println("Found " + toDelete.size() + " orders that are older than the last 3 per customer.");
            System.out.print("Proceed to delete them and their items? (y/n): ");
            String confirm = scanner.nextLine().trim();
            if (!confirm.equalsIgnoreCase("y")) {
                System.out.println("Deletion cancelled.");
                return;
            }

            String deleteItemsSql = "DELETE FROM cust_order_items WHERE order_no = ?";
            String deleteOrdersSql = "DELETE FROM cust_orders WHERE order_no = ?";

            try {
                conn.setAutoCommit(false);
                try (PreparedStatement delItems = conn.prepareStatement(deleteItemsSql);
                     PreparedStatement delOrders = conn.prepareStatement(deleteOrdersSql)) {

                    for (String orderNo : toDelete) {
                        delItems.setString(1, orderNo);
                        delItems.addBatch();
                        delOrders.setString(1, orderNo);
                        delOrders.addBatch();
                    }

                    delItems.executeBatch();
                    delOrders.executeBatch();
                }
                conn.commit();
                System.out.println("Deleted " + toDelete.size() + " orders and their items.");
            } catch (SQLException ex) {
                conn.rollback();
                System.out.println("Error while deleting orders. Rolled back.");
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error while finding/deleting old orders:");
            e.printStackTrace();
        }
    }
}