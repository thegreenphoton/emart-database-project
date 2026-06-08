package org.ivc.dbms.ui;
import java.util.Scanner;

import org.ivc.dbms.dao.ManagerDAO;
import org.ivc.dbms.dao.WarehouseDAO;
import org.ivc.dbms.model.Customer;


public class ManagerInterface {
    public static void run(Scanner scanner, Customer manager) {
        while (true) {
            System.out.println("\nManager Interface");
            System.out.println("1. Monthly sales summary");
            System.out.println("2. Change product price");
            System.out.println("3. Change customer status (auto/manual)");
            System.out.println("4. Auto-adjust customer statuses");
            System.out.println("5. Send order to manufacturer");
            System.out.println("6. Clear unneeded sales transactions");
            System.out.println("7. Logout");
            System.out.print("Choose option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    ManagerDAO.monthlySalesSummary(scanner);
                     break;
                case "2":
                    ManagerDAO.changeProductPrice(scanner);
                     break;
                case "3":
                    ManagerDAO.manualAdjustCustomerStatus(scanner);
                     break;
                case "4":
                    ManagerDAO.autoAdjustCustomerStatus();
                     break;
                case "5":
                    WarehouseDAO.receiveShippingNotice(scanner);
                     break;
                case "6":
                    ManagerDAO.deleteOrdersNotNeededForStatus(scanner);
                     break;
                case "7":
                     return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }
}