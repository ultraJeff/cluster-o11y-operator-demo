package com.example.store;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
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

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class StoreResource {

    @Inject
    @RestClient
    CatalogClient catalogClient;

    @Inject
    @RestClient
    OrderServiceClient orderServiceClient;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String welcome() {
        return "Quarkus Cafe — Try GET /menu, POST /order, or GET /orders";
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
    public Response placeOrder(@Valid OrderRequest request) {
        try {
            Map<String, Object> result = orderServiceClient.createOrder(request);
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (WebApplicationException e) {
            return Response.status(e.getResponse().getStatus())
                    .entity(e.getResponse().readEntity(Map.class))
                    .build();
        }
    }

    @GET
    @Path("/orders")
    public List<Map<String, Object>> listOrders() {
        return orderServiceClient.listOrders();
    }

    @GET
    @Path("/orders/{orderId}")
    public Map<String, Object> getOrder(@PathParam("orderId") String orderId) {
        return orderServiceClient.getOrder(orderId);
    }
}
