using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using System.Diagnostics;

var builder = WebApplication.CreateBuilder(args);

// Configure OpenTelemetry
builder.Services.AddOpenTelemetry()
    .ConfigureResource(resource => resource.AddService("inventory-service"))
    .WithTracing(tracing => tracing
        .AddAspNetCoreInstrumentation()
        .AddSource("InventoryService")
        .AddOtlpExporter());

var app = builder.Build();

// Create ActivitySource for custom spans
var activitySource = new ActivitySource("InventoryService");

// Simulated inventory database
var inventory = new Dictionary<string, int>
{
    ["widget-001"] = 100,
    ["gadget-002"] = 50,
    ["gizmo-003"] = 25,
    ["doohickey-004"] = 0
};

app.MapGet("/", () => "Inventory Service");

app.MapGet("/check/{productId}", async (string productId) =>
{
    using var activity = activitySource.StartActivity("CheckInventory");
    activity?.SetTag("inventory.product_id", productId);
    
    // Simulate database lookup
    using (var dbActivity = activitySource.StartActivity("QueryDatabase"))
    {
        dbActivity?.SetTag("db.system", "postgresql");
        dbActivity?.SetTag("db.operation", "SELECT");
        await Task.Delay(Random.Shared.Next(5, 30));
    }
    
    var available = inventory.TryGetValue(productId, out var quantity) ? quantity : Random.Shared.Next(0, 100);
    activity?.SetTag("inventory.quantity", available);
    activity?.SetTag("inventory.in_stock", available > 0);
    
    if (available <= 0)
    {
        activity?.SetStatus(ActivityStatusCode.Error, "Out of stock");
        return Results.Ok(new 
        { 
            productId = productId,
            available = false,
            quantity = 0,
            message = "Out of stock"
        });
    }
    
    return Results.Ok(new 
    { 
        productId = productId,
        available = true,
        quantity = available
    });
});

app.MapGet("/health", () => Results.Ok("healthy"));

app.Run();
