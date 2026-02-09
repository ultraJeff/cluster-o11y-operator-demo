# .NET Distributed Tracing Demo

A simple 3-service .NET 8 application demonstrating OpenTelemetry distributed tracing.

## Architecture

```
┌──────────────┐     ┌─────────────────┐     ┌───────────────────┐
│   Frontend   │────▶│  Order Service  │     │ Inventory Service │
│   (API)      │     │                 │     │                   │
│              │────▶│                 │     │                   │
└──────────────┘     └─────────────────┘     └───────────────────┘
       │                                              ▲
       └──────────────────────────────────────────────┘
```

## Services

### Frontend (`/order/{productId}`)
- Entry point for requests
- Calls Inventory Service to check stock
- Calls Order Service to create order
- Propagates trace context to downstream services

### Order Service (`/create/{productId}`)
- Creates orders
- Simulates database operations and notifications
- Adds custom spans for business logic

### Inventory Service (`/check/{productId}`)
- Checks product availability
- Simulates database lookups
- Returns stock levels

## OpenTelemetry Instrumentation

Each service uses:
- `OpenTelemetry.Instrumentation.AspNetCore` - Automatic HTTP server spans
- `OpenTelemetry.Instrumentation.Http` - Automatic HTTP client spans (Frontend)
- `OpenTelemetry.Exporter.OpenTelemetryProtocol` - OTLP export to collector
- Custom `ActivitySource` spans for business logic

### Key Code Patterns

```csharp
// Configure OpenTelemetry in Program.cs
builder.Services.AddOpenTelemetry()
    .ConfigureResource(resource => resource.AddService("service-name"))
    .WithTracing(tracing => tracing
        .AddAspNetCoreInstrumentation()  // Auto-instrument incoming HTTP
        .AddHttpClientInstrumentation()   // Auto-instrument outgoing HTTP
        .AddSource("CustomActivitySource") // Custom spans
        .AddOtlpExporter());              // Export via OTLP

// Create custom spans
using var activity = activitySource.StartActivity("OperationName");
activity?.SetTag("custom.attribute", "value");
```

## Test Products

- `widget-001` - 100 in stock
- `gadget-002` - 50 in stock
- `gizmo-003` - 25 in stock
- `doohickey-004` - Out of stock
- Any other ID - Random stock level

## Deployment

The `openshift-deploy.yaml` creates:
- Namespace `dotnet-tracing-demo`
- BuildConfigs for each service (binary builds)
- Deployments configured to send traces to the OTel collector
- Services and Route for Frontend

## Building

```bash
# Apply the manifests
oc apply -f openshift-deploy.yaml

# Build each service using binary builds
oc start-build inventory-service --from-dir=src/InventoryService -n dotnet-tracing-demo --follow
oc start-build order-service --from-dir=src/OrderService -n dotnet-tracing-demo --follow
oc start-build frontend --from-dir=src/Frontend -n dotnet-tracing-demo --follow

# Restart deployments to pick up new images
oc rollout restart deployment -n dotnet-tracing-demo
```
