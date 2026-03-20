package com.example.order;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    @RestClient
    CatalogClient catalogClient;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createOrder(@Valid CreateOrderRequest request) {
        Product product;
        try {
            product = catalogClient.getProduct(request.productId());
        } catch (WebApplicationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Product not found", "productId", request.productId()))
                    .build();
        }

        Product reserved;
        try {
            reserved = catalogClient.reserveStock(
                    request.productId(), new ReserveRequest(request.quantity()));
        } catch (WebApplicationException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "Stock reservation failed",
                            "productId", request.productId(),
                            "requested", request.quantity()))
                    .build();
        }

        OrderEntity order = new OrderEntity();
        order.orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        order.productId = product.id();
        order.productName = product.name();
        order.quantity = request.quantity();
        order.unitPrice = product.price();
        order.total = product.price() * request.quantity();
        order.status = "confirmed";
        order.createdAt = Instant.now();
        order.persist();

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "orderId", order.orderId,
                        "productId", order.productId,
                        "productName", order.productName,
                        "quantity", order.quantity,
                        "unitPrice", order.unitPrice,
                        "total", order.total,
                        "status", order.status,
                        "remainingStock", reserved.stock()))
                .build();
    }

    @GET
    public List<OrderEntity> listOrders() {
        return OrderEntity.listAll();
    }

    @GET
    @Path("/{orderId}")
    public OrderEntity getOrder(@PathParam("orderId") String orderId) {
        OrderEntity order = OrderEntity.findByOrderId(orderId);
        if (order == null) {
            throw new NotFoundException("Order not found: " + orderId);
        }
        return order;
    }
}
