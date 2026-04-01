# Quarkus Auto-Instrumentation Demo

A 3-service Quarkus application demonstrating **zero-code OpenTelemetry auto-instrumentation** on OpenShift. Unlike traditional SDK-based instrumentation, this demo uses the OpenTelemetry Operator's `Instrumentation` CR to inject the Java agent automatically — no OTel dependencies or code changes in the application.

## Architecture

```
┌──────────────────┐         ┌───────────────────┐
│  Store Frontend  │────────▶│  Catalog Service  │
│  (REST API)      │         │  (Product Data)   │
│                  │         └───────────────────┘
│                  │                   ▲
│                  │                   │
│                  │         ┌─────────┴─────────┐
│                  │────────▶│  Order Service    │
└──────────────────┘         │  (H2 Database)    │
                             └───────────────────┘
```

### Trace depth for POST /order

```
store-frontend: POST /order
└── order-service: POST /orders
    ├── catalog-service: GET /catalog/{id}         (validate — JDBC SELECT)
    ├── catalog-service: POST /catalog/{id}/reserve (reserve — JDBC UPDATE)
    └── JDBC: INSERT INTO orders ...               (persist order to H2)
```

This produces a **3-level deep distributed trace** with HTTP spans at each hop plus JDBC/Hibernate spans in both catalog-service and order-service — all captured by the auto-instrumented Java agent without any OTel code.

## How Auto-Instrumentation Works

1. The **OpenTelemetry Operator** watches for `Instrumentation` CRs in the namespace
2. Deployments annotated with `instrumentation.opentelemetry.io/inject-java: "true"` get a Java agent injected via an init container at pod startup
3. The agent automatically instruments popular frameworks (RESTEasy, JAX-RS, HTTP clients, JDBC, Hibernate, etc.) using bytecode manipulation
4. Traces are exported to the configured collector endpoint via OTLP
5. **No OTel code, dependencies, or configuration in the application source**

## Services

### Store Frontend (`/`)
- `GET /` — Welcome message
- `GET /menu` — List all products (calls Catalog Service)
- `GET /menu/{id}` — Get product details (calls Catalog Service)
- `POST /order` — Place an order with validation (delegates to Order Service)
- `GET /orders` — List all orders (calls Order Service)
- `GET /orders/{orderId}` — Get order details (calls Order Service)
- `GET /q/health` — SmallRye Health (liveness + readiness)

### Order Service (`/orders`)
- `POST /orders` — Create order with validation: validates product, reserves stock via Catalog Service, persists to H2 database
- `GET /orders` — List all orders (JDBC SELECT)
- `GET /orders/{orderId}` — Get order by ID (JDBC SELECT)
- `GET /q/health` — SmallRye Health (liveness + readiness + datasource check)

### Catalog Service (`/catalog`)
- `GET /catalog` — List all products (JDBC SELECT)
- `GET /catalog/{id}` — Get product by ID (JDBC SELECT)
- `POST /catalog/{id}/reserve` — Reserve stock with validation (JDBC SELECT + UPDATE)
- `GET /q/health` — SmallRye Health (liveness + readiness + datasource check)

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

Use the deploy script for the full workflow:

```bash
# Full deploy from scratch (apply manifests + build + restart)
./deploy.sh all

# Or run steps individually:
./deploy.sh apply              # Apply OpenShift manifests
./deploy.sh build              # Binary-build all 3 services
./deploy.sh restart            # Restart deployments + wait for rollout

# After code changes, just rebuild and restart:
./deploy.sh build restart

# Check current status:
./deploy.sh status
```

<details>
<summary>Manual deployment commands</summary>

```bash
oc apply -f openshift-deploy.yaml
oc start-build catalog-service --from-dir=src/catalog-service -n quarkus-otel-demo --follow
oc start-build order-service --from-dir=src/order-service -n quarkus-otel-demo --follow
oc start-build store-frontend --from-dir=src/store-frontend -n quarkus-otel-demo --follow
oc rollout restart deployment -n quarkus-otel-demo
oc get route store-frontend -n quarkus-otel-demo -o jsonpath='{.spec.host}'
```

</details>

## Generating Traces

Use the trace generator script to create a batch of realistic traffic:

```bash
# Generate 5 rounds of requests (default), 2s delay between each
./generate-traces.sh

# Generate 20 rounds with 1s delay
./generate-traces.sh 20 1
```

Each round hits menu browsing, product lookup, order placement, order listing, and health checks. Every other round also tests out-of-stock errors, and every 3rd round triggers a validation error — producing a mix of success and error traces.

### Manual testing

```bash
ROUTE=$(oc get route store-frontend -n quarkus-otel-demo -o jsonpath='{.spec.host}')

# Browse the menu (store-frontend → catalog-service, JDBC SELECT)
curl -s https://$ROUTE/menu | jq

# Place an order (store-frontend → order-service → catalog-service + JDBC INSERT)
curl -s -X POST https://$ROUTE/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-001", "quantity": 2}' | jq

# List orders (store-frontend → order-service → JDBC SELECT)
curl -s https://$ROUTE/orders | jq

# Out-of-stock error (409)
curl -s -X POST https://$ROUTE/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-005", "quantity": 1}' | jq

# Validation error (400)
curl -s -X POST https://$ROUTE/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-001", "quantity": 0}' | jq

# Health check
curl -s https://$ROUTE/q/health | jq
```

## Viewing Traces

1. In the OpenShift console, go to **Observe → Traces**
2. Select the TempoStack as the data source
3. Search for service names `store-frontend`, `order-service`, or `catalog-service`
4. A `POST /order` trace spans all 3 services and includes JDBC spans from order-service

### What to look for in the trace

- **HTTP server spans** on each service (auto-instrumented RESTEasy)
- **HTTP client spans** for each cross-service REST call (auto-instrumented MicroProfile REST Client)
- **JDBC spans** in both catalog-service and order-service showing `SELECT`, `UPDATE`, and `INSERT` queries
- **Trace context propagation** across all 3 services linked under a single trace ID

## Custom Dashboard (Grafana)

A Grafana instance with a pre-loaded dashboard is included in `dashboard.yaml` and
deployed automatically by `deploy.sh`. This demonstrates the **Dashboard-as-Code**
pattern — the dashboard JSON lives in Git as a ConfigMap and is provisioned into
Grafana at startup.

Anonymous read-only access is enabled so developers can view the dashboard without
needing Grafana credentials. Access it at:

```bash
oc get route grafana -n quarkus-otel-demo -o jsonpath='https://{.spec.host}{"\n"}'
```

The **Quarkus Auto-Instrumentation Demo** dashboard shows:

| Row | Panels |
|-----|--------|
| Service Health | CPU stat, Memory stat, Pod Restarts, Ready Pods |
| Resource Usage | CPU over time, Memory over time |
| Network I/O | Receive rate, Transmit rate |
| Traces | Recent Traces table, Trace View (search + waterfall) |

Grafana is configured with two datasources:

- **Prometheus** — queries Thanos Querier (`cluster-monitoring-view` role) for
  CPU, memory, network, and pod metrics
- **Tempo** — queries the TempoStack gateway (`tempo.grafana.com` RBAC) for
  distributed traces, including trace search, waterfall view, and node graph

The trace panels let you browse recent traces, click into individual trace IDs
to see the full span waterfall, and correlate traces with resource metrics — all
in one dashboard. You can also use Grafana's **Explore** view for ad-hoc trace
queries with TraceQL.

In a real-world workflow, dashboard JSON like this would live in a separate team
repository and be deployed by a GitOps pipeline (ArgoCD, Flux), giving developers a
self-service view of their services without needing cluster-admin access.

## Quarkus Extensions Used

All services use standard Quarkus extensions — **none are OTel-related**:

| Extension | Services | Purpose |
|-----------|----------|---------|
| `quarkus-rest` | all | RESTEasy Reactive server |
| `quarkus-rest-jackson` | all | JSON serialization |
| `quarkus-rest-client-jackson` | store-frontend, order-service | MicroProfile REST Client |
| `quarkus-hibernate-orm-panache` | catalog-service, order-service | JPA via Panache (generates JDBC spans) |
| `quarkus-jdbc-h2` | catalog-service, order-service | H2 in-memory database |
| `quarkus-smallrye-health` | all | `/q/health/live` and `/q/health/ready` for k8s probes |
| `quarkus-hibernate-validator` | all | Bean Validation (`@NotBlank`, `@Min`) on request DTOs |

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
| `deploy.sh` | Deploy, build, restart, and check status — `./deploy.sh all` for full deploy |
| `generate-traces.sh` | Generate realistic traffic to populate traces — `./generate-traces.sh 10` |
| `openshift-deploy.yaml` | Namespace, `Instrumentation` CR, BuildConfigs, Deployments (with auto-instrumentation annotation), Services, Route |
| `dashboard.yaml` | Grafana Deployment with Prometheus + Tempo datasources, dashboard (ConfigMap), SA, RBAC, Route |
| `src/catalog-service/` | Quarkus REST API + H2/Panache product catalog with stock reservation — **no OTel code** |
| `src/order-service/` | Quarkus REST API + H2/Panache order persistence, calls catalog-service — **no OTel code** |
| `src/store-frontend/` | Quarkus REST gateway with validation, calls catalog-service and order-service — **no OTel code** |

## Local Development

```bash
# Terminal 1: Start catalog-service
cd src/catalog-service
mvn quarkus:dev -Dquarkus.http.port=8082

# Terminal 2: Start order-service
cd src/order-service
mvn quarkus:dev -Dquarkus.http.port=8081 \
  -Dquarkus.rest-client.catalog-service.url=http://localhost:8082

# Terminal 3: Start store-frontend
cd src/store-frontend
mvn quarkus:dev \
  -Dquarkus.rest-client.catalog-service.url=http://localhost:8082 \
  -Dquarkus.rest-client.order-service.url=http://localhost:8081

# Test
curl http://localhost:8080/menu
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRD-001", "quantity": 2}'
curl http://localhost:8080/orders
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
