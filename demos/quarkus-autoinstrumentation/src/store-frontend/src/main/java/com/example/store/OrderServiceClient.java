package com.example.store;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "order-service")
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public interface OrderServiceClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Map<String, Object> createOrder(OrderRequest request);

    @GET
    List<Map<String, Object>> listOrders();

    @GET
    @Path("/{orderId}")
    Map<String, Object> getOrder(@PathParam("orderId") String orderId);
}
