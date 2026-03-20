package com.example.catalog;

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
import java.util.List;
import java.util.Map;

@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @GET
    public List<Product> list() {
        return ProductEntity.<ProductEntity>listAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GET
    @Path("/{id}")
    public Product get(@PathParam("id") String id) {
        return toDto(findOrThrow(id));
    }

    @POST
    @Path("/{id}/reserve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Product reserve(@PathParam("id") String id, @Valid ReserveRequest request) {
        ProductEntity entity = findOrThrow(id);
        if (entity.stock < request.quantity()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of(
                                    "error", "Insufficient stock",
                                    "productId", id,
                                    "available", entity.stock,
                                    "requested", request.quantity()))
                            .build());
        }
        entity.stock -= request.quantity();
        entity.persist();
        return toDto(entity);
    }

    private ProductEntity findOrThrow(String productId) {
        ProductEntity entity = ProductEntity.findByProductId(productId);
        if (entity == null) {
            throw new NotFoundException("Product not found: " + productId);
        }
        return entity;
    }

    private Product toDto(ProductEntity entity) {
        return new Product(entity.productId, entity.name, entity.category, entity.price, entity.stock);
    }
}
