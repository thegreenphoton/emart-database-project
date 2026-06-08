package org.ivc.dbms.ui;

import java.util.Scanner;

import org.ivc.dbms.dao.CartDAO;
import org.ivc.dbms.dao.OrderDAO;
import org.ivc.dbms.dao.ProductDAO;
import org.ivc.dbms.dao.WarehouseDAO;
import org.ivc.dbms.model.Customer;

public class CustomerInterface {
    public static void run(Scanner scanner, Customer customer) {
        while (true) {
            WarehouseDAO.sweepReplenishments(); // sweep replenishments on each customer menu load
            System.out.println("\nCustomer Interface");
            System.out.println("1. Search products");
            System.out.println("2. View cart");
            System.out.println("3. Checkout");
            System.out.println("4. View order history");
            System.out.println("5. Logout");
            System.out.print("Choose option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    ProductDAO.searchProducts(scanner, customer.getCartId());
                    break;
                case "2":
                    String cartId = customer.getCartId();
                    boolean notEmpty = CartDAO.viewCart(scanner, cartId);

                    if (!notEmpty) {
                        System.out.println("Cart is empty.");
                        break;
                    }
                    System.out.print("Would you like to remove items from cart? (y/n): ");
                    String removeChoice = scanner.nextLine();
                    if (removeChoice.equalsIgnoreCase("y")) {
                        while (true) {
                            System.out.print("Enter stock number to remove (blank to finish): ");
                            String stockNo = scanner.nextLine().trim();
                            if (stockNo.isBlank()) {
                                System.out.println("Finished removing items.");
                                break;
                            }

                            System.out.print("Desired quantity to remove: ");
                            String qtyInput = scanner.nextLine().trim();
                            int qty;
                            try {
                                qty = Integer.parseInt(qtyInput);
                            } catch (NumberFormatException e) {
                                 System.out.println("Invalid quantity. Please enter a positive integer.");
                                continue;
                            }

                            CartDAO.removeFromCart(cartId, stockNo, qty);
                        }
                    } else {
                        System.out.println("Returning to customer menu.");
                    }
                    break;
                case "3":
                    CartDAO.checkout(scanner, customer.getCustomerId(), customer.getCartId());
                    break;
                case "4":
                    OrderDAO.viewOrders(scanner, customer.getCustomerId());
                    break;
                case "5":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }
}