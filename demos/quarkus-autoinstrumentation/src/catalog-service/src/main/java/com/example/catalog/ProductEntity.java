package com.example.catalog;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductEntity extends PanacheEntity {

    @Column(name = "product_id", unique = true, nullable = false)
    public String productId;

    @Column(nullable = false)
    public String name;

    public String category;

    public double price;

    public int stock;

    public static ProductEntity findByProductId(String productId) {
        return find("productId", productId).firstResult();
    }
}
