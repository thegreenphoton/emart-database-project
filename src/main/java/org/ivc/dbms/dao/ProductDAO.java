package org.ivc.dbms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.ivc.dbms.util.DBConnection;

public class ProductDAO {
    public static void searchProducts(Scanner scanner, String cartId) {

        System.out.println("\nView all products or search?");
        System.out.println("1. View all products");
        System.out.println("2. Search by criteria");
        System.out.println("3. Search by compatible items");
        System.out.print("Choose option: ");

        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                viewAllProducts(scanner, cartId);
                break;
            case "2":
                searchByCriteria(scanner, cartId);
                break;
            case "3":
                searchByCompatibleItems(scanner, cartId);
                break;
            default:
                System.out.println("Invalid option.");
        }


    }

    public static void viewAllProducts(Scanner scanner, String cartId) {
        String sql = "SELECT stock_no, category, manufacturer_name, model_no, price FROM mart_products";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()
        ) {
            System.out.println("\nAll products:");

            boolean found = false;
            int i = 1;
            while (rs.next()) {
                found = true;
                System.out.print("\n" + i + ". ");
                System.out.println("\nStock Number: " + rs.getString("stock_no"));
                System.out.println("Category: " + rs.getString("category"));
                System.out.println("Manufacturer: " + rs.getString("manufacturer_name"));
                System.out.println("Model: " + rs.getString("model_no"));
                System.out.println("Price: $" + rs.getDouble("price"));
                i++;
            }

            System.out.println("\nAdd product to cart by entering the corresponding stock number (or blank to skip): ");
            String selectedStockNum = scanner.nextLine();
            String[] validStockNums = {"AA00101", "AA00201", "AA00202", "AA00301", "AA00302", "AA00401", "AA00402", "AA00403", "AA00501", "AA00601", "AA00602"};
            while (!selectedStockNum.isBlank()) {
                try {
                    if (!Arrays.stream(validStockNums).anyMatch(selectedStockNum::equals)) {
                        System.out.println("Invalid number. Please try again.");
                    } else {
                        System.out.print("Desired quantity of " + selectedStockNum + ": ");
                        int desiredQuantity = Integer.parseInt(scanner.nextLine());

                        CartDAO.addToCart(cartId, selectedStockNum, desiredQuantity);
                    }   
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a stock number.");
                }
                System.out.print("Add another product (or blank to finish): ");
                selectedStockNum = scanner.nextLine();
            }

            if (!found) {
                System.out.println("No products found.");
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }

    public static void searchByCriteria(Scanner scanner, String cartId) {
        System.out.println("\nAvailable criteria: Stock Number, Manufacturer, Model Number, Category, Description Attribute/Value");
        System.out.println("Please enter the following criteria (leave blank to skip):");
        System.out.print("Stock Number: ");
        String stockNo = scanner.nextLine();

        System.out.print("Manufacturer: ");
        String manufacturer = scanner.nextLine();

        System.out.print("Model Number: ");
        String modelNo = scanner.nextLine();

        System.out.print("Category: ");
        String category = scanner.nextLine();

        System.out.print("Description Attribute: ");
        String descriptionAttr = scanner.nextLine();

        System.out.print("Description Value: ");
        String descriptionValue = scanner.nextLine();

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT P.stock_no, P.category, P.manufacturer_name, P.model_no, P.price
            FROM mart_products P INNER JOIN Product_Descriptions PD ON P.stock_no = PD.stock_no
            WHERE 1=1
        """);

        List<String> params = new ArrayList<>();

        if (!stockNo.isBlank()) {
            sql.append(" AND TRIM(P.stock_no) = TRIM(?)");
            params.add(stockNo);
        }

        if (!manufacturer.isBlank()) {
            sql.append(" AND TRIM(LOWER(P.manufacturer_name)) = TRIM(LOWER(?))");
            params.add(manufacturer);
        }

        if (!modelNo.isBlank()) {
            sql.append(" AND TRIM(LOWER(P.model_no)) = TRIM(LOWER(?))");
            params.add(modelNo);
        }

        if (!category.isBlank()) {
            sql.append(" AND TRIM(LOWER(P.category)) = TRIM(LOWER(?))");
            params.add(category);
        }

        if (!descriptionAttr.isBlank() && !descriptionValue.isBlank()) {
            sql.append(" AND TRIM(LOWER(PD.attr_name)) = TRIM(LOWER(?)) AND TRIM(LOWER(PD.attr_desc)) = TRIM(LOWER(?))");
            params.add(descriptionAttr);
            params.add(descriptionValue);
        }
      
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql.toString());

            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            System.out.println("\nSearch results:");
            
            boolean found = false;
            int i = 1;
            while (rs.next()) {
                found = true;
                System.out.print("\n" + i + ". ");
                System.out.println("\nStock Number: " + rs.getString("stock_no"));
                System.out.println("Category: " + rs.getString("category"));
                System.out.println("Manufacturer: " + rs.getString("manufacturer_name"));
                System.out.println("Model: " + rs.getString("model_no"));
                System.out.println("Price: $" + rs.getDouble("price"));
                i++;
            }

            if (!found) {
                System.out.println("No products found matching criteria.");
                return;
            }

            System.out.println("\nAdd products to cart by entering the corresponding stock number (or blank to skip): ");
            String selectedStockNum = scanner.nextLine();
            String[] validStockNums = {"AA00101", "AA00201", "AA00202", "AA00301", "AA00302", "AA00401", "AA00402", "AA00403", "AA00501", "AA00601", "AA00602"};
            while (!selectedStockNum.isBlank()) {
                try {
                    if (!Arrays.stream(validStockNums).anyMatch(selectedStockNum::equals)) {
                        System.out.println("Invalid number. Please try again.");
                    } else {
                        System.out.print("Desired quantity of " + selectedStockNum + ": ");
                        int desiredQuantity = Integer.parseInt(scanner.nextLine());

                        CartDAO.addToCart(cartId, selectedStockNum, desiredQuantity);
                    }   
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a stock number.");
                }
                System.out.print("Add another product (or blank to finish): ");
                selectedStockNum = scanner.nextLine();
            }

            if (!found) {
                System.out.println("No products found.");
            }
        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }

    public static void searchByCompatibleItems(Scanner scanner, String cartId) {
        System.out.print("Enter stock number of product: ");
        String stockNo = scanner.nextLine();

        String sql = """
            SELECT P.stock_no, P.category, P.manufacturer_name, P.model_no, P.price
            FROM mart_products P 
            WHERE P.stock_no IN (
                SELECT CI.compatible_stock_no 
                FROM compatibilities CI
                WHERE CI.product_stock_no = ?
            )
        """;

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, stockNo);

            ResultSet rs = stmt.executeQuery();

            boolean found = false;

            while (rs.next()) {
                found = true;
                System.out.println("\nStock No: " + rs.getString("stock_no"));
                System.out.println("Category: " + rs.getString("category"));
                System.out.println("Manufacturer: " + rs.getString("manufacturer_name"));
                System.out.println("Model: " + rs.getString("model_no"));
                System.out.println("Price: $" + rs.getDouble("price"));
            }

            System.out.println("\nAdd products to cart by entering the corresponding stock number (or blank to skip): ");
            String selectedStockNum = scanner.nextLine();
            String[] validStockNums = {"AA00101", "AA00201", "AA00202", "AA00301", "AA00302", "AA00401", "AA00402", "AA00403", "AA00501", "AA00601", "AA00602"};
            while (!selectedStockNum.isBlank()) {
                try {
                    if (!Arrays.stream(validStockNums).anyMatch(selectedStockNum::equals)) {
                        System.out.println("Invalid number. Please try again.");
                    } else {
                        System.out.print("Desired quantity of " + selectedStockNum + ": ");
                        int desiredQuantity = Integer.parseInt(scanner.nextLine());

                        CartDAO.addToCart(cartId, selectedStockNum, desiredQuantity);
                    }   
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a stock number.");
                }
                System.out.print("Add another product (or blank to finish): ");
                selectedStockNum = scanner.nextLine();
            }

            if (!found) {
                System.out.println("No compatible products found.");
            }

        } catch (Exception e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }

}