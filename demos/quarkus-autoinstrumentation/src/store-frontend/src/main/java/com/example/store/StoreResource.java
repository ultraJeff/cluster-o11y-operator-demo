package com.example.store;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class StoreResource {

    private static final Random RANDOM = new Random();

    @Inject
    @RestClient
    CatalogClient catalogClient;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String welcome() {
        return "Quarkus Cafe — Try GET /menu or POST /order";
    }

    @GET
    @Path("/menu")
    public List<Product> menu() {
        return catalogClient.listProducts();
    }

    @GET
    @Path("/menu/{id}")
    public Product menuItem(@PathParam("id") String id) {
        return catalogClient.getProduct(id);
    }

    @POST
    @Path("/order")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response placeOrder(OrderRequest request) {
        Product product;
        try {
            product = catalogClient.getProduct(request.productId());
        } catch (WebApplicationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Product not found", "productId", request.productId()))
                    .build();
        }

        if (product.stock() < request.quantity()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "Insufficient stock",
                            "available", product.stock(),
                            "requested", request.quantity()))
                    .build();
        }

        simulateProcessing(50, 150);

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        double total = product.price() * request.quantity();

        return Response.ok(new OrderResponse(
                orderId, product.id(), product.name(),
                request.quantity(), total, "confirmed")).build();
    }

    @GET
    @Path("/health")
    @Produces(MediaType.TEXT_PLAIN)
    public String health() {
        return "healthy";
    }

    private void simulateProcessing(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + RANDOM.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
