// Java
// filepath: src/main/java/org/ivc/dbms/dao/WarehouseDAO.java
package org.ivc.dbms.dao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.ivc.dbms.util.DBConnection;

public class WarehouseDAO {
    private static final SecureRandom RAND = new SecureRandom();
    private static final String ALPH = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // generate stock_no like AA##### (7 chars)
    private static String genStockNo() {
        StringBuilder sb = new StringBuilder(7);
        for (int i = 0; i < 2; i++) sb.append((char) ('A')); //+ RAND.nextInt(26)));
        for (int i = 0; i < 5; i++) sb.append(RAND.nextInt(10));
        return sb.toString();
    }

    private static String genLocation() {
        StringBuilder sb = new StringBuilder(3);
        sb.append((char) ('A' + RAND.nextInt(26)));
        sb.append(RAND.nextInt(10));
        sb.append(RAND.nextInt(10));
        return sb.toString();
    }

    // generate short id with prefix; total length <= 20
    private static String genId(String prefix, int totalLen) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        int remain = Math.max(0, totalLen - prefix.length());
        for (int i = 0; i < remain; i++) sb.append(ALPH.charAt(RAND.nextInt(ALPH.length())));
        return sb.toString();
    }

    // 1) Receive shipping notice: record Shipping_notices and Notice_items, ensure manufacturer and product exist,
    // and increment Wh_products.replenishment by notice_qty.
    public static void receiveShippingNotice(Scanner scanner) {
        String noticeId = genId("SN", 6);
        if (noticeId.isBlank()) { System.out.println("Canceled."); return; }
        System.out.print("Shipping company: ");
        String shipCo = scanner.nextLine().trim();

        List<NoticeLine> lines = new ArrayList<>();
        while (true) {
            System.out.print("Manufacturer (blank to finish): ");
            String man = scanner.nextLine().trim();
            if (man.isBlank()) break;
            System.out.print("Model number: ");
            String model = scanner.nextLine().trim();
            int availableForRepl = -1;
            // If product exists, inform user of maximum quantity that can be added to replenishment
            String checkSql = "SELECT stock_no, Wh_qty, COALESCE(replenishment,0) AS replenishment, max_level FROM Wh_products WHERE TRIM(manufacturer_name) = TRIM(?) AND TRIM(model_no) = TRIM(?)";
            try (var connCheck = DBConnection.getConnection();
                 var ps = connCheck.prepareStatement(checkSql)) {
                ps.setString(1, man);
                ps.setString(2, model);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String stock = rs.getString("stock_no");
                        int whQty = rs.getInt("Wh_qty");
                        int repl = rs.getInt("replenishment");
                        int max = rs.getInt("max_level");
                        availableForRepl = Math.max(0, max - (whQty + repl));
                        System.out.println("Existing product " + stock + " -> Wh_qty=" + whQty + ", replenishment=" + repl + ", max_level=" + max + ".");
                        System.out.println("You may add up to " + availableForRepl + " units in this shipping notice (will be applied to replenishment).");
                    }
                }
            } catch (Exception e) {
                System.out.println("Warning: could not check existing product limits: " + e.getMessage());
            }
            System.out.print("Quantity: ");
            String q = scanner.nextLine().trim();
            int qty;
            try { qty = Integer.parseInt(q); if (qty <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { System.out.println("Invalid qty. Skipping line."); continue; }
            if (qty > availableForRepl && availableForRepl >= 0) {
                System.out.println("Entered quantity exceeds available replenishment space for existing product.");
                continue;
            }
            lines.add(new NoticeLine(man, model, qty));
        }
        if (lines.isEmpty()) { System.out.println("No items provided. Aborting."); return; }

        String insertNotice = "INSERT INTO Shipping_notices (notice_id, shipping_co, shipment_received) VALUES (?, ?, 'N')";
        String findProd = "SELECT stock_no, Wh_qty, replenishment, min_level, max_level FROM Wh_products WHERE TRIM(manufacturer_name) = TRIM(?) AND TRIM(model_no) = TRIM(?)";
        String insertMan = "INSERT INTO Manufacturers (manufacturer_name) VALUES (?)";
        String insertProd = "INSERT INTO Wh_products (stock_no, manufacturer_name, model_no, Wh_qty, min_level, max_level, location, replenishment) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String insertNoticeItem = "INSERT INTO Notice_items (notice_id, stock_no, notice_qty) VALUES (?, ?, ?)";
        String updReplBy = "UPDATE Wh_products SET replenishment = COALESCE(replenishment,0) + ? WHERE stock_no = ?";

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement pInsertNotice = conn.prepareStatement(insertNotice);
            PreparedStatement pFindProd = conn.prepareStatement(findProd);
            PreparedStatement pInsertMan = conn.prepareStatement(insertMan);
            PreparedStatement pInsertProd = conn.prepareStatement(insertProd);
            PreparedStatement pInsertNoticeItem = conn.prepareStatement(insertNoticeItem);
            PreparedStatement pUpdReplBy = conn.prepareStatement(updReplBy)) {

            conn.setAutoCommit(false);

            pInsertNotice.setString(1, noticeId);
            pInsertNotice.setString(2, shipCo);
            pInsertNotice.executeUpdate();

            for (NoticeLine nl : lines) {
                // ensure manufacturer exists (ignore unique violation)
                try {
                    pInsertMan.setString(1, nl.manufacturer);
                    pInsertMan.executeUpdate();
                } catch (SQLException ignored) {
                    // likely already exists; ignore
                }

                // try to find existing product by manufacturer+model and read its min/max if present
                String stockNo = null;
                Integer existingMin = 0;
                Integer existingMax = 0;
                Integer existingQty = 0;
                Integer existingRepl = 0;
                pFindProd.setString(1, nl.manufacturer);
                pFindProd.setString(2, nl.model);
                try (ResultSet rs = pFindProd.executeQuery()) {
                    if (rs.next()) {
                        stockNo = rs.getString("stock_no");
                        existingQty = rs.getInt("Wh_qty");
                        existingMin = rs.getInt("min_level");
                        existingMax = rs.getInt("max_level");
                        existingRepl = rs.getInt("replenishment");
                        System.out.println("Found existing product " + stockNo + " (qty=" + existingQty + ", repl=" + existingRepl + ", min=" + existingMin + ", max=" + existingMax + ")");                    }
                }

                // if not found, create it
                if (stockNo == null) {
                    // create new product but ensure Wh_qty does not exceed max_level
                    stockNo = genStockNo();
                    int minLevel = 5;
                    int maxLevel = 100;
                    try {
                        System.out.print("New product " + nl.manufacturer + " " + nl.model + " detected. Enter min_level (blank for 5): ");
                        String minIn = scanner.nextLine().trim();
                        if (!minIn.isBlank()) minLevel = Integer.parseInt(minIn);
                        System.out.print("Enter max_level (blank for 100): ");
                        String maxIn = scanner.nextLine().trim();
                        if (!maxIn.isBlank()) maxLevel = Integer.parseInt(maxIn);
                        if (maxLevel < minLevel) { System.out.println("max_level < min_level; using defaults (5/100)."); minLevel = 5; maxLevel = 100; }
                    } catch (NumberFormatException nfe) {
                        System.out.println("Invalid min/max input; using defaults (5/100).");
                        minLevel = 5; maxLevel = 100;
                    }
                    String location = genLocation();
                    // For new product: Wh_qty remains 0 until actual shipment.
                    int setQty = 0;
                    int replenishment = Math.min(nl.qty, maxLevel); // track up to max_level
                    int leftover = Math.max(0, nl.qty - replenishment);
                    pInsertProd.setString(1, stockNo);
                    pInsertProd.setString(2, nl.manufacturer);
                    pInsertProd.setString(3, nl.model);
                    pInsertProd.setInt(4, setQty);
                    pInsertProd.setInt(5, minLevel);
                    pInsertProd.setInt(6, maxLevel);
                    pInsertProd.setString(7, location);
                    pInsertProd.setInt(8, replenishment);
                    try {
                        pInsertProd.executeUpdate();
                        System.out.println("Created new product " + stockNo + " for " + nl.manufacturer + " " + nl.model + " (Wh_qty=0, repl=" + replenishment + ")");
                        if (leftover > 0) System.out.println("Notice contains " + leftover + " items beyond max_level; not tracked in replenishment.");
                    } catch (SQLException e) {
                        // race: someone else created it concurrently -> fetch existing and treat as existing below
                        try (ResultSet rs2 = pFindProd.executeQuery()) {
                            if (rs2.next()) {
                                stockNo = rs2.getString("stock_no");
                                existingQty = rs2.getInt("Wh_qty");
                                if (rs2.wasNull()) existingQty = 0;
                                existingRepl = rs2.getInt("replenishment");
                                if (rs2.wasNull()) existingRepl = 0;
                                existingMax = rs2.getInt("max_level");
                                if (rs2.wasNull()) existingMax = 0;
                                System.out.println("Product created concurrently; using existing " + stockNo + " (qty=" + existingQty + ", repl=" + existingRepl + ", max=" + existingMax + ")");
                                // fall through to existing-product handling
                            } else {
                                throw e;
                            }
                        }
                        }
                    }
                    // if we inserted successfully, still need to insert notice item and continue

                // handle existing product case: only update replenishment so that (Wh_qty + replenishment) <= max_level
                if (stockNo != null) {
                    int currentTotal = existingQty + existingRepl;
                    int spaceForRepl = Math.max(0, existingMax - currentTotal);
                    if (spaceForRepl <= 0) {
                        // no room for replenishment
                        System.out.println("No replenishment space for " + stockNo + " (max reached). Notice qty not tracked in replenishment.");
                    } else {
                        int toAdd = Math.min(nl.qty, spaceForRepl);
                        pUpdReplBy.setInt(1, toAdd);
                        pUpdReplBy.setString(2, stockNo);
                        pUpdReplBy.executeUpdate();
                        if (nl.qty > toAdd) {
                            int leftover = nl.qty - toAdd;
                            System.out.println("Only " + toAdd + " of " + nl.qty + " added to replenishment for " + stockNo + "; " + leftover + " not tracked due to max_level.");
                        }
                    }
                } else if (stockNo != null) {
                    // missing max info: conservatively add all to replenishment
                    pUpdReplBy.setInt(1, nl.qty);
                    pUpdReplBy.setString(2, stockNo);
                    pUpdReplBy.executeUpdate();
                }


                // insert notice item
                pInsertNoticeItem.setString(1, noticeId);
                pInsertNoticeItem.setString(2, stockNo);
                pInsertNoticeItem.setInt(3, nl.qty);
                pInsertNoticeItem.executeUpdate();
             }

            conn.commit();
            System.out.println("Shipping notice (ID: " + noticeId + " recorded with " + lines.size() + " items.");
        } catch (SQLException e) {
            System.out.println("Error recording shipping notice:");
            e.printStackTrace();
        }
    }

    // 2) Receive shipment: given a Shipping_notices.notice_id, create a Shipments record and apply notice quantities to Wh_qty,
    // and reduce replenishment accordingly.
    public static void receiveShipment(Scanner scanner) {
        boolean anyActive = listActiveNotices();
        if (!anyActive) return;
        System.out.print("Notice id to receive shipment for: ");
        String noticeId = scanner.nextLine().trim();
        if (noticeId.isBlank()) { System.out.println("Canceled."); return; }

        String checkNotice = "SELECT shipment_id FROM Shipping_notices WHERE TRIM(notice_id) = ?";
        String selectNoticeItems = "SELECT stock_no, notice_qty FROM Notice_items WHERE TRIM(notice_id) = ?";
        String upProdSql = "UPDATE Wh_products SET Wh_qty = COALESCE(Wh_qty,0) + ?, replenishment = GREATEST(COALESCE(replenishment,0) - ?, 0) WHERE stock_no = ?";
        String insertShipment = "INSERT INTO Shipments (shipment_id, received_date, notice_id) VALUES (?, ?, ?)";
        String updateNotice = "UPDATE Shipping_notices SET shipment_id = ?, shipment_received = 'Y' WHERE TRIM(notice_id) = ?";
        // emart-related statements for creating new product metadata
        String selMart = "SELECT stock_no FROM mart_products WHERE stock_no = ?";
        String selWhMeta = "SELECT manufacturer_name, model_no FROM Wh_products WHERE stock_no = ?";
        String insMart = "INSERT INTO mart_products (stock_no, category, manufacturer_name, model_no, price, warranty_mo) VALUES (?, ?, ?, ?, ?, ?)";
        String insDesc = "INSERT INTO product_descriptions (stock_no, attr_name, attr_desc) VALUES (?, ?, ?)";
        String insCompat = "INSERT INTO compatibilities (product_stock_no, compatible_stock_no) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pCheck = conn.prepareStatement(checkNotice);
             PreparedStatement pSelItems = conn.prepareStatement(selectNoticeItems);
             PreparedStatement pUpProd = conn.prepareStatement(upProdSql);
             PreparedStatement pInsShipment = conn.prepareStatement(insertShipment);
             PreparedStatement pUpdNotice = conn.prepareStatement(updateNotice);
             PreparedStatement pSelMart = conn.prepareStatement(selMart);
             PreparedStatement pSelWhMeta = conn.prepareStatement(selWhMeta);
             PreparedStatement pInsMart = conn.prepareStatement(insMart);
             PreparedStatement pInsDesc = conn.prepareStatement(insDesc);
             PreparedStatement pInsCompat = conn.prepareStatement(insCompat)) {

            // ensure notice exists and has not been received
            pCheck.setString(1, noticeId);
            try (ResultSet rs = pCheck.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Shipping notice " + noticeId + " not found.");
                    return;
                }
                String existingShipment = rs.getString("shipment_id");
                if (existingShipment != null && !existingShipment.trim().isEmpty()) {
                    System.out.println("Shipping notice " + noticeId + " already received (shipment " + existingShipment + ").");
                    return;
                }
            }

            // gather notice items
            pSelItems.setString(1, noticeId);
            List<NoticeLine> items = new ArrayList<>();
            try (ResultSet irs = pSelItems.executeQuery()) {
                while (irs.next()) {
                    String stock = irs.getString("stock_no");
                    int qty = irs.getInt("notice_qty");
                    items.add(new NoticeLine(null, null, qty).withStock(stock));
                }
            }
            if (items.isEmpty()) {
                System.out.println("No notice items found for notice " + noticeId);
                return;
            }

            // create shipment record and apply quantities
            String shipmentId = genId("SH", 12);
            conn.setAutoCommit(false);
            try {
                pInsShipment.setString(1, shipmentId);
                pInsShipment.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                pInsShipment.setString(3, noticeId);
                pInsShipment.executeUpdate();

                for (NoticeLine nl : items) {
                    // if emart product missing, prompt user to create it with details
                    pSelMart.setString(1, nl.stockNo);
                    try (ResultSet mrs = pSelMart.executeQuery()) {
                        if (!mrs.next()) {
                            System.out.println("EMart product for stock " + nl.stockNo + " not found. Enter product details to create:");
                            // get manufacturer/model from Wh_products if available
                            String man = null, model = null;
                            pSelWhMeta.setString(1, nl.stockNo);
                            try (ResultSet whm = pSelWhMeta.executeQuery()) {
                                if (whm.next()) {
                                    man = whm.getString("manufacturer_name");
                                    model = whm.getString("model_no");
                                }
                            }
                            System.out.print("Category: ");
                            String category = scanner.nextLine().trim();
                            System.out.print("Price (e.g. 19.95): ");
                            double price = 0.0;
                            try { price = Double.parseDouble(scanner.nextLine().trim()); } catch (Exception ex) { price = 0.0; }
                            System.out.print("Warranty months (0 for none): ");
                            int warranty = 0;
                            try { warranty = Integer.parseInt(scanner.nextLine().trim()); } catch (Exception ex) { warranty = 0; }

                            // collect multiple descriptions
                            List<String[]> descriptions = new ArrayList<>();
                            while (true) {
                                System.out.print("Description name (blank to finish): ");
                                String dname = scanner.nextLine().trim();
                                if (dname.isBlank()) break;
                                System.out.print("Description value: ");
                                String dval = scanner.nextLine().trim();
                                descriptions.add(new String[]{dname, dval});
                            }

                            // collect compatibilities (comma separated)
                            System.out.print("Compatible stock numbers (comma-separated, blank for none): ");
                            String compIn = scanner.nextLine().trim();
                            List<String> comps = new ArrayList<>();
                            if (!compIn.isBlank()) {
                                for (String s : compIn.split(",")) {
                                    String c = s.trim();
                                    if (!c.isBlank()) comps.add(c);
                                }
                            }

                            // insert mart_products row
                            pInsMart.setString(1, nl.stockNo);
                            pInsMart.setString(2, category.isEmpty() ? null : category);
                            pInsMart.setString(3, man);
                            pInsMart.setString(4, model);
                            pInsMart.setDouble(5, price);
                            pInsMart.setInt(6, warranty);
                            pInsMart.executeUpdate();

                            // insert descriptions
                            for (String[] d : descriptions) {
                                pInsDesc.setString(1, nl.stockNo);
                                pInsDesc.setString(2, d[0]);
                                pInsDesc.setString(3, d[1]);
                                pInsDesc.addBatch();
                            }
                            pInsDesc.executeBatch();

                            // insert compatibilities
                            for (String c : comps) {
                                pInsCompat.setString(1, nl.stockNo);
                                pInsCompat.setString(2, c);
                                pInsCompat.addBatch();
                            }
                            pInsCompat.executeBatch();
                            System.out.println("Created emart product " + nl.stockNo + " with " + descriptions.size() + " descriptions and " + comps.size() + " compatibilities.");
                        }
                    }

                    // now apply warehouse quantity updates
                    pUpProd.setInt(1, nl.qty);
                    pUpProd.setInt(2, nl.qty);
                    pUpProd.setString(3, nl.stockNo);
                    int updated = pUpProd.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        System.out.println("Shipment failed: warehouse product " + nl.stockNo + " not found.");
                        return;
                    }
                }

                pUpdNotice.setString(1, shipmentId);
                pUpdNotice.setString(2, noticeId);
                pUpdNotice.executeUpdate();

                conn.commit();
                System.out.println("Shipment " + shipmentId + " received for notice " + noticeId + ".");
            } catch (SQLException ex) {
                try { conn.rollback(); } catch (Exception ignore) {}
                System.out.println("Error while receiving shipment; transaction rolled back.");
                ex.printStackTrace();
            } finally {
                try { conn.setAutoCommit(true); } catch (Exception ignore) {}
            }

        } catch (SQLException e) {
            System.out.println("Error receiving shipment:");
            e.printStackTrace();
        }
    }
// ...existing code...

    // 3) Check inventory quantity
    public static void checkInventory(Scanner scanner) {
        String sql = "SELECT stock_no, manufacturer_name, model_no, Wh_qty, COALESCE(replenishment,0) AS replenishment, min_level, max_level, location FROM Wh_products ORDER BY manufacturer_name, model_no, stock_no";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean any = false;
            System.out.println("Warehouse inventory:");
            while (rs.next()) {
                any = true;
                String stock = rs.getString("stock_no");
                String man = rs.getString("manufacturer_name");
                String model = rs.getString("model_no");
                int qty = rs.getInt("Wh_qty");
                int repl = rs.getInt("replenishment");
                int min = rs.getInt("min_level");
                int max = rs.getInt("max_level");
                String loc = rs.getString("location");
                System.out.printf("%s | %s %s | qty=%d | repl=%d | min=%d | max=%d | loc=%s%n",
                        stock == null ? "" : stock.trim(),
                        man == null ? "" : man.trim(),
                        model == null ? "" : model.trim(),
                        qty, repl, min, max,
                        loc == null ? "" : loc.trim());
            }
            if (!any) System.out.println("No products found in warehouse.");
        } catch (SQLException e) {
            System.out.println("Error checking inventory:");
            e.printStackTrace();
        }
    }

    public static void sweepReplenishments() {
        String findManufacturersSql =
            "SELECT TRIM(manufacturer_name) AS manufacturer_name " +
            "FROM Wh_products " +
            "WHERE COALESCE(Wh_qty,0) < COALESCE(min_level,0) " +
            "GROUP BY TRIM(manufacturer_name) HAVING COUNT(*) >= 3";

        String selProductsSql =
            "SELECT stock_no, COALESCE(Wh_qty,0) AS wh_qty, COALESCE(replenishment,0) AS repl, COALESCE(max_level,0) AS max_level " +
            "FROM Wh_products " +
            "WHERE TRIM(manufacturer_name)=TRIM(?) AND (COALESCE(Wh_qty,0) + COALESCE(replenishment,0)) < COALESCE(max_level,0)";

        String insertNotice = "INSERT INTO Shipping_notices (notice_id, shipping_co, shipment_received) VALUES (?, ?, 'N')";
        String insertNoticeItem = "INSERT INTO Notice_items (notice_id, stock_no, notice_qty) VALUES (?, ?, ?)";
        String updRepl = "UPDATE Wh_products SET replenishment = COALESCE(replenishment,0) + ? WHERE stock_no = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pFindMan = conn.prepareStatement(findManufacturersSql);
             PreparedStatement pSelProducts = conn.prepareStatement(selProductsSql);
             PreparedStatement pInsNotice = conn.prepareStatement(insertNotice);
             PreparedStatement pInsNoticeItem = conn.prepareStatement(insertNoticeItem);
             PreparedStatement pUpdRepl = conn.prepareStatement(updRepl)) {

            List<String> manufacturers = new ArrayList<>();
            try (ResultSet rs = pFindMan.executeQuery()) {
                while (rs.next()) manufacturers.add(rs.getString("manufacturer_name"));
            }

            for (String man : manufacturers) {
                List<Record> toNotice = new ArrayList<>();
                pSelProducts.setString(1, man);
                try (ResultSet prs = pSelProducts.executeQuery()) {
                    while (prs.next()) {
                        String stock = prs.getString("stock_no");
                        int wh = prs.getInt("wh_qty");
                        int repl = prs.getInt("repl");
                        int max = prs.getInt("max_level");
                        int need = Math.max(0, max - (wh + repl));
                        if (need > 0) toNotice.add(new Record(stock, need));
                    }
                }

                if (toNotice.isEmpty()) continue;
                boolean prevAuto = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    String noticeId = genId("SN", 6);

                    pInsNotice.setString(1, noticeId);
                    pInsNotice.setString(2, "Shipping Co.");
                    pInsNotice.executeUpdate();

                    for (Record r : toNotice) {
                        pInsNoticeItem.setString(1, noticeId);
                        pInsNoticeItem.setString(2, r.stockNo);
                        pInsNoticeItem.setInt(3, r.qty);
                        pInsNoticeItem.executeUpdate();

                        pUpdRepl.setInt(1, r.qty);
                        pUpdRepl.setString(2, r.stockNo);
                        pUpdRepl.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("Auto shipping notice created: " + noticeId + " for manufacturer " + man + " (" + toNotice.size() + " items).");
                } catch (SQLException ex) {
                    try { conn.rollback(); } catch (Exception ignore) {}
                    System.out.println("Failed to create auto shipping notice for " + man + "; transaction rolled back.");
                    ex.printStackTrace();
                } finally {
                    try { conn.setAutoCommit(prevAuto); } catch (Exception ignore) {}
                }
            }

        } catch (SQLException e) {
            System.out.println("Error during replenishment sweep:");
            e.printStackTrace();
        }
    }

    // helper holders

    public static boolean listActiveNotices() {
        String sql = "SELECT notice_id, shipping_co FROM Shipping_notices WHERE shipment_received = 'N'";
        String itemCount = "SELECT COUNT(*) FROM (SELECT * FROM Notice_items WHERE TRIM(notice_id) = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement psCount = conn.prepareStatement(itemCount);
             ResultSet rs = ps.executeQuery();) {
            boolean any = false;
            System.out.println("Active shipping notices:");
            while (rs.next()) {
                any = true;
                String noticeId = rs.getString("notice_id").trim();
                psCount.setString(1, noticeId);
                int noticeCount = 0;
                try (ResultSet crs = psCount.executeQuery()) {
                    if (crs.next()) noticeCount = crs.getInt(1);
                }
                System.out.println("- " + noticeId + " from " + rs.getString("shipping_co").trim() + " with " + noticeCount + (noticeCount == 1 ? " item" : " items"));
            }

            if (!any) {
                System.out.println("No active shipping notices found.");
                return false;
            }
            return true;
        } catch (SQLException e) {
            System.out.println("Error listing active notices:");
            e.printStackTrace();
            return false;
        }
    }
    private static class NoticeLine { String manufacturer; String model; int qty; String stockNo;
        NoticeLine(String m, String mo, int q){ manufacturer=m; model=mo; qty=q; }
        NoticeLine withStock(String s){ this.stockNo = s; return this; }
    }
    private static class OrderLine { String stockNo; int qty; int currentQty;
        OrderLine(String s, int q){ stockNo=s; qty=q; currentQty=0; }
    }
    private static class ReplItem { String stockNo; int qty; ReplItem(String s,int q){stockNo=s;qty=q;} }
    private static final class Record { final String stockNo; final int qty; Record(String s,int q){stockNo=s;qty=q;} }

}