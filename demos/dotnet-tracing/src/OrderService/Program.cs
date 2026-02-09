using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using System.Diagnostics;

var builder = WebApplication.CreateBuilder(args);

// Configure OpenTelemetry
builder.Services.AddOpenTelemetry()
    .ConfigureResource(resource => resource.AddService("order-service"))
    .WithTracing(tracing => tracing
        .AddAspNetCoreInstrumentation()
        .AddSource("OrderService")
        .AddOtlpExporter());

var app = builder.Build();

// Create ActivitySource for custom spans
var activitySource = new ActivitySource("OrderService");

app.MapGet("/", () => "Order Service");

app.MapPost("/create/{productId}", async (string productId) =>
{
    using var activity = activitySource.StartActivity("CreateOrder");
    activity?.SetTag("order.product_id", productId);
    
    // Simulate order processing
    var orderId = Guid.NewGuid().ToString("N")[..8];
    activity?.SetTag("order.id", orderId);
    
    // Simulate some processing time
    using (var dbActivity = activitySource.StartActivity("SaveToDatabase"))
    {
        dbActivity?.SetTag("db.system", "postgresql");
        dbActivity?.SetTag("db.operation", "INSERT");
        await Task.Delay(Random.Shared.Next(10, 50));
    }
    
    // Simulate sending notification
    using (var notifyActivity = activitySource.StartActivity("SendNotification"))
    {
        notifyActivity?.SetTag("notification.type", "email");
        await Task.Delay(Random.Shared.Next(5, 20));
    }
    
    return Results.Ok(new 
    { 
        orderId = orderId,
        productId = productId,
        status = "created",
        timestamp = DateTime.UtcNow
    });
});

app.MapGet("/health", () => Results.Ok("healthy"));

app.Run();
