package org.ivc.dbms.ui;
import java.util.Scanner;

import org.ivc.dbms.dao.WarehouseDAO;

public class ExternalWorldInterface {
    public static void run(Scanner scanner) {
        while (true) {
            System.out.println("\nExternal World Interface");
            System.out.println("1. Submit shipping notice");
            System.out.println("2. Receive shipment");
            System.out.println("3. Check inventory quantity");
            System.out.println("4. Back");
            System.out.print("Choose option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    WarehouseDAO.receiveShippingNotice(scanner);
                    break;
                case "2":
                    WarehouseDAO.receiveShipment(scanner);
                    break;
                case "3":
                    WarehouseDAO.sweepReplenishments();
                    WarehouseDAO.checkInventory(scanner);
                    break;
                case "4":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }
}