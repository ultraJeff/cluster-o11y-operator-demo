package com.example.catalog;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataSeeder {

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (ProductEntity.count() > 0) {
            return;
        }
        seed("PRD-001", "Espresso", "coffee", 3.50, 100);
        seed("PRD-002", "Cappuccino", "coffee", 4.50, 75);
        seed("PRD-003", "Green Tea", "tea", 3.00, 50);
        seed("PRD-004", "Blueberry Muffin", "pastry", 2.75, 30);
        seed("PRD-005", "Croissant", "pastry", 3.25, 0);
    }

    private void seed(String productId, String name, String category, double price, int stock) {
        ProductEntity p = new ProductEntity();
        p.productId = productId;
        p.name = name;
        p.category = category;
        p.price = price;
        p.stock = stock;
        p.persist();
    }
}
