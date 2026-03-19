package com.example.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    private static final Map<String, Product> PRODUCTS = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    static {
        PRODUCTS.put("PRD-001", new Product("PRD-001", "Espresso", "coffee", 3.50, 100));
        PRODUCTS.put("PRD-002", new Product("PRD-002", "Cappuccino", "coffee", 4.50, 75));
        PRODUCTS.put("PRD-003", new Product("PRD-003", "Green Tea", "tea", 3.00, 50));
        PRODUCTS.put("PRD-004", new Product("PRD-004", "Blueberry Muffin", "pastry", 2.75, 30));
        PRODUCTS.put("PRD-005", new Product("PRD-005", "Croissant", "pastry", 3.25, 0));
    }

    @GET
    public List<Product> list() {
        simulateLatency(15, 50);
        return new ArrayList<>(PRODUCTS.values());
    }

    @GET
    @Path("/{id}")
    public Product get(@PathParam("id") String id) {
        simulateLatency(5, 20);
        Product product = PRODUCTS.get(id);
        if (product == null) {
            throw new NotFoundException("Product not found: " + id);
        }
        return product;
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + RANDOM.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
