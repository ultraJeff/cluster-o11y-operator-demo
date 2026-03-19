# Quarkus Auto-Instrumentation Demo

A 2-service Quarkus application demonstrating **zero-code OpenTelemetry auto-instrumentation** on OpenShift. Unlike traditional SDK-based instrumentation, this demo uses the OpenTelemetry Operator's `Instrumentation` CR to inject the Java agent automatically — no OTel dependencies or code changes in the application.

## Architecture

```
┌──────────────────┐         ┌───────────────────┐
│  Store Frontend  │────────▶│  Catalog Service  │
│  (REST API)      │         │  (Product Data)   │
└──────────────────┘         └───────────────────┘
        │                             │
        └──────────┬──────────────────┘
                   ▼
         OTel Java Agent (injected)
                   │
                   ▼
    ┌──────────────────────────┐
    │  tracing-collector       │
    │  (observability ns)      │
    └──────────────────────────┘
                   │
                   ▼
              TempoStack
```

## How Auto-Instrumentation Works

1. The **OpenTelemetry Operator** watches for `Instrumentation` CRs in the namespace
2. Deployments annotated with `instrumentation.opentelemetry.io/inject-java: "true"` get a Java agent injected via an init container at pod startup
3. The agent automatically instruments popular frameworks (RESTEasy, JAX-RS, HTTP clients, JDBC, etc.) using bytecode manipulation
4. Traces are exported to the configured collector endpoint via OTLP
5. **No OTel code, dependencies, or configuration in the application source**

## Services

### Store Frontend (`/`)
- `GET /` — Welcome message
- `GET /menu` — List all products (calls Catalog Service)
- `GET /menu/{id}` — Get product details (calls Catalog Service)
- `POST /order` — Place an order (validates stock via Catalog Service)
- `GET /health` — Health check

### Catalog Service (`/catalog`)
- `GET /catalog` — List all products
- `GET /catalog/{id}` — Get product by ID

## Test Products

| ID | Name | Category | Price | Stock |
|----|------|----------|-------|-------|
| PRD-001 | Espresso | coffee | $3.50 | 100 |
| PRD-002 | Cappuccino | coffee | $4.50 | 75 |
| PRD-003 | Green Tea | tea | $3.00 | 50 |
| PRD-004 | Blueberry Muffin | pastry | $2.75 | 30 |
| PRD-005 | Croissant | pastry | $3.25 | 0 (out of stock) |

## Prerequisites

- OpenShift cluster with the observability stack deployed (`oc apply -k observability/`)
- The **OpenTelemetry Operator** installed and running
- The `tracing-collector` available in the `observability` namespace (created by `ObservabilityInstaller`)

## Deployment

```bash
# Apply the manifests (namespace, Instrumentation CR, BuildConfigs, Deployments, Services, Route)
oc apply -f openshift-deploy.yaml

# Build each service using binary builds
oc start-build catalog-service --from-dir=src/catalog-service -n quarkus-otel-demo --follow
oc start-build store-frontend --from-dir=src/store-frontend -n quarkus-otel-demo --follow

# Restart deployments to pick up new images
oc rollout restart deployment -n quarkus-otel-demo

# Get the route URL
oc get route store-frontend -n quarkus-otel-demo -o jsonpath='{.spec.host}'
```

## Testing

```bash
# Get the route
ROUTE=$(oc get route store-frontend -n quarkus-otel-demo -o jsonpath='{.spec.host}')

# List the menu
curl -s https://$ROUTE/menu | jq

# Get a specific product
curl -s https://$ROUTE/menu/PRD-001 | jq

# Place an order
curl -s -X POST https://$ROUTE/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-001", "quantity": 2}' | jq

# Try ordering an out-of-stock item
curl -s -X POST https://$ROUTE/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-005", "quantity": 1}' | jq
```

## Viewing Traces

1. In the OpenShift console, go to **Observe > Traces**
2. Select the TempoStack as the data source
3. Search for service names `catalog-service` or `store-frontend`
4. Each request to the store-frontend generates a distributed trace spanning both services

## Verifying Auto-Instrumentation

Confirm the Java agent was injected into your pods:

```bash
# Check that an init container was added
oc get pod -n quarkus-otel-demo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .spec.initContainers[*]}{.name}{" "}{end}{"\n"}{end}'

# Check the JAVA_TOOL_OPTIONS env var includes the agent
oc get pod -n quarkus-otel-demo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .spec.containers[*].env[*]}{.name}={.value}{"\n"}{end}{end}' | grep JAVA_TOOL_OPTIONS

# View collector logs for incoming traces
oc logs deployment/tracing-collector -n observability | head -50
```

## Key Files

| File | Purpose |
|------|---------|
| `openshift-deploy.yaml` | Namespace, `Instrumentation` CR, BuildConfigs, Deployments (with auto-instrumentation annotation), Services, Route |
| `src/catalog-service/` | Quarkus REST API serving product data — **no OTel code** |
| `src/store-frontend/` | Quarkus REST API calling catalog-service — **no OTel code** |

## Local Development

```bash
# Terminal 1: Start catalog-service
cd src/catalog-service
mvn quarkus:dev -Dquarkus.http.port=8081

# Terminal 2: Start store-frontend (pointing to local catalog)
cd src/store-frontend
mvn quarkus:dev -Dquarkus.rest-client.catalog-service.url=http://localhost:8081

# Test
curl http://localhost:8080/menu
```

## Contrast with SDK Instrumentation

This demo intentionally has **zero OpenTelemetry dependencies** in `pom.xml` and **zero OTel code** in the Java source. Compare with the `.NET tracing demo` in this repo, which uses explicit SDK integration (`AddOpenTelemetry()`, `AddOtlpExporter()`, etc.).

The auto-instrumentation approach is ideal for:
- Large organizations with hundreds of services
- Legacy applications that can't be easily modified
- Standardizing telemetry across teams without requiring developer effort

See also:
- [How to use auto-instrumentation with OpenTelemetry](https://developers.redhat.com/articles/2026/02/25/how-use-auto-instrumentation-opentelemetry)
- [How Quarkus works with OpenTelemetry on OpenShift](https://developers.redhat.com/articles/2025/07/07/how-quarkus-works-opentelemetry-openshift)
