# CS174A Database Systems Project

## Overview

This project implements an e-commerce system consisting of two subsystems:

* eMart: Customer and manager storefront
* eDepot: Warehouse and inventory management system

The application is built in Java using JDBC and Oracle Autonomous Database. It supports product browsing, shopping carts, order processing, inventory management, shipping notices, shipments, and warehouse order fulfillment.

## Features

### Customer Features

* Customer login
* Product search
* Shopping cart management
* Checkout and order creation
* Order history

### Manager Features

* Product management
* Customer status management
* Inventory monitoring
* Warehouse order management

### Warehouse (eDepot) Features

* Receive shipping notices
* Receive shipments
* Check inventory levels
* Fill warehouse orders
* Generate replenishment orders

## Technologies Used

* Java
* JDBC
* Oracle Autonomous Database
* Maven

## Setup

1. Configure Oracle database credentials using environment variables:

```bash
export DB_URL=<database_url>
export DB_USER=<username>
export DB_PASSWORD=<password>
export TNS_ADMIN=<wallet_directory>
```

2. Build the project:

```bash
mvn clean compile
```

3. Run the application:

```bash
mvn exec:java -Dexec.mainClass="org.ivc.dbms.Main.Main"
```

## Project Structure

* `dao/` - Database access objects
* `model/` - Data models
* `ui/` - User interface menus
* `util/` - Utility classes and database connection management

## Authors

CS174A Database Systems Course Project
