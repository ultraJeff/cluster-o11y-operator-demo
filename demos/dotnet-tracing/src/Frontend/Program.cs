using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using System.Diagnostics;

var builder = WebApplication.CreateBuilder(args);

// Configure OpenTelemetry
builder.Services.AddOpenTelemetry()
    .ConfigureResource(resource => resource.AddService("frontend"))
    .WithTracing(tracing => tracing
        .AddAspNetCoreInstrumentation()
        .AddHttpClientInstrumentation()
        .AddOtlpExporter());

builder.Services.AddHttpClient();

var app = builder.Build();

var orderServiceUrl = Environment.GetEnvironmentVariable("ORDER_SERVICE_URL") ?? "http://order-service:8080";
var inventoryServiceUrl = Environment.GetEnvironmentVariable("INVENTORY_SERVICE_URL") ?? "http://inventory-service:8080";

app.MapGet("/", () => "Frontend Service - Try /order/{productId}");

app.MapGet("/order/{productId}", async (string productId, IHttpClientFactory httpClientFactory) =>
{
    var client = httpClientFactory.CreateClient();
    
    // Add custom span for business logic
    using var activity = Activity.Current?.Source.StartActivity("ProcessOrder");
    activity?.SetTag("product.id", productId);
    
    // Check inventory first
    var inventoryResponse = await client.GetAsync($"{inventoryServiceUrl}/check/{productId}");
    var inventoryResult = await inventoryResponse.Content.ReadAsStringAsync();
    
    if (!inventoryResponse.IsSuccessStatusCode)
    {
        return Results.BadRequest(new { error = "Inventory check failed", details = inventoryResult });
    }
    
    // Create order
    var orderResponse = await client.PostAsync($"{orderServiceUrl}/create/{productId}", null);
    var orderResult = await orderResponse.Content.ReadAsStringAsync();
    
    return Results.Ok(new 
    { 
        message = "Order processed",
        productId = productId,
        inventory = inventoryResult,
        order = orderResult
    });
});

app.MapGet("/health", () => Results.Ok("healthy"));

app.Run();
