package org.ivc.dbms.model;

public class Customer {

    private String customerId;
    private String firstName;
    private String lastName;
    private String status;
    private String cartId;
    private boolean manager;

    public Customer(
            String customerId,
            String firstName,
            String lastName,
            String status,
            String cartId,
            boolean manager) {

        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.cartId = cartId;
        this.manager = manager;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getStatus() {
        return status;
    }

    public String getCartId() {
        return cartId;
    }

    public boolean isManager() {
        return manager;
    }
}