package com.example.order;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "catalog-service")
@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
public interface CatalogClient {

    @GET
    @Path("/{id}")
    Product getProduct(@PathParam("id") String id);

    @POST
    @Path("/{id}/reserve")
    @Consumes(MediaType.APPLICATION_JSON)
    Product reserveStock(@PathParam("id") String id, ReserveRequest request);
}
