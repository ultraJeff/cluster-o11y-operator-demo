package com.example.order;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class OrderEntity extends PanacheEntity {

    @Column(name = "order_id", unique = true, nullable = false)
    public String orderId;

    @Column(name = "product_id", nullable = false)
    public String productId;

    @Column(name = "product_name")
    public String productName;

    public int quantity;

    @Column(name = "unit_price")
    public double unitPrice;

    public double total;

    public String status;

    @Column(name = "created_at")
    public Instant createdAt;

    public static OrderEntity findByOrderId(String orderId) {
        return find("orderId", orderId).firstResult();
    }
}
