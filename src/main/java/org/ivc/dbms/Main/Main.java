// compile with mvn clean compile
// run with mvn exec:java -Dexec.mainClass="org.ivc.dbms.Main.Main"

// Java
package org.ivc.dbms.Main;

import java.util.Scanner;

import org.ivc.dbms.dao.CustomerDAO;
import org.ivc.dbms.model.Customer;
import org.ivc.dbms.ui.CustomerInterface;
import org.ivc.dbms.ui.ExternalWorldInterface;
import org.ivc.dbms.ui.ManagerInterface;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nWelcome - choose storefront");
            System.out.println("1. eMart (requires login)");
            System.out.println("2. eDepot (external world)");
            System.out.println("3. Exit");
            System.out.print("Choose option: ");

            String topChoice = scanner.nextLine();

            switch (topChoice) {
                case "1": // eMart -> require login
                    if (handleEMart(scanner)) {
                        // returned to main menu after user backs out
                    }
                    break;
                case "2": // eDepot -> external world interface
                    ExternalWorldInterface.run(scanner);
                    break;
                case "3":
                    System.out.println("Goodbye.");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private static boolean handleEMart(Scanner scanner) {
        while (true) {
            System.out.println("\neMart Login");
            System.out.println("1. Login as Customer");
            System.out.println("2. Login as Manager");
            System.out.println("3. Back");
            System.out.print("Choose option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    Customer customer = login(scanner, "customer");
                    if (customer != null) {
                        CustomerInterface.run(scanner, customer);
                    } else {
                        System.out.println("Invalid customer credentials.");
                    }
                    break;
                case "2":
                    Customer manager = login(scanner, "manager");
                    if (manager != null) {
                        ManagerInterface.run(scanner, manager);
                    } else {
                        System.out.println("Invalid manager credentials.");
                    }
                    break;
                case "3":
                    return true;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // NOTE: this is a placeholder. Replace with real DB-based authentication.
    private static Customer login(Scanner scanner, String role) {
        System.out.print("Customer ID: ");
        String customerId = scanner.nextLine();

        System.out.print("Password: ");
        String password = scanner.nextLine();

        Customer customer =
                CustomerDAO.login(customerId, password);

        if (customer == null) {
            System.out.println("Invalid login.");
            return null;
        } else {
            if (!customer.isManager() && "manager".equals(role)) {
                System.out.println("User is not a manager.");
                return null;
            } 
            System.out.println(
                    "Welcome "
                    + customer.getFirstName()
            );

            if (customer.isManager()) {
                System.out.println("Manager account");
                return customer;
            } else {
                System.out.println("Customer account");
                return customer;
            }
            }
    }
}
