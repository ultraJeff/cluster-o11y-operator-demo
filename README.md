# OpenShift Cluster Observability Operator Demo

This repository contains resources for deploying the Red Hat OpenShift Cluster Observability Operator (COO) with full logging, tracing, and monitoring capabilities.

## Overview

The Cluster Observability Operator provides a unified observability experience in OpenShift with:

- **Logging** - Log collection and viewing via LokiStack
- **Distributed Tracing** - Trace visualization via TempoStack
- **Monitoring** - Enhanced monitoring UI
- **Dashboards** - Custom dashboard support
- **Troubleshooting Panel** - Integrated troubleshooting tools

## Repository Structure

```
.
├── observability/           # Core observability infrastructure
│   ├── kustomization.yaml   # Kustomize entry point
│   ├── *-operator.yaml      # Operator subscriptions
│   ├── minio.yaml           # Object storage for Loki/Tempo
│   ├── lokistack.yaml       # Log storage configuration
│   ├── observability-installer.yaml  # TempoStack + OTel Collector
│   ├── clusterlogforwarder.yaml      # Log forwarding config
│   ├── ui-plugin-*.yaml     # UI plugin configurations
│   └── sample-tracing-app.yaml       # Simple trace generator
├── demos/
│   ├── hotrod/              # Jaeger HotROD demo (Go, 4 services)
│   └── dotnet-tracing/      # .NET 8 demo (3 services)
└── rbac/
    └── traces-reader.yaml   # RBAC for trace access
```

## Quick Start

### 1. Deploy Observability Stack

```bash
# Apply all observability resources
oc apply -k observability/
```

**Note**: Resources should be applied in order. The operators need to be installed first, then the CRDs become available for LokiStack, ObservabilityInstaller, etc.

### 2. Verify Installation

```bash
# Check operators
oc get csv -A | grep -E "loki|tempo|opentelemetry|observability|logging"

# Check components
oc get lokistack -A
oc get tempostack -A
oc get opentelemetrycollector -A
oc get uiplugin
```

### 3. Deploy Demo Applications

#### HotROD (Jaeger demo)
```bash
oc apply -f demos/hotrod/hotrod.yaml
```

#### .NET Distributed Tracing Demo
```bash
# Apply manifests
oc apply -f demos/dotnet-tracing/openshift-deploy.yaml

# Build services
oc start-build inventory-service --from-dir=demos/dotnet-tracing/src/InventoryService -n dotnet-tracing-demo --follow
oc start-build order-service --from-dir=demos/dotnet-tracing/src/OrderService -n dotnet-tracing-demo --follow
oc start-build frontend --from-dir=demos/dotnet-tracing/src/Frontend -n dotnet-tracing-demo --follow
```

### 4. Grant User Access

```bash
# Apply traces-reader role
oc apply -f rbac/traces-reader.yaml

# Bind to a user
oc create clusterrolebinding user1-traces-reader \
  --clusterrole=tempostack-traces-reader \
  --user=user1

# Grant namespace access
oc create rolebinding user1-view --clusterrole=view --user=user1 -n hotrod-demo
oc create rolebinding user1-view --clusterrole=view --user=user1 -n dotnet-tracing-demo
```

## Components

### Operators Installed

| Operator | Namespace | Purpose |
|----------|-----------|---------|
| Cluster Observability Operator | openshift-cluster-observability-operator | UI plugins, dashboards |
| Loki Operator | openshift-operators-redhat | Log storage |
| Tempo Operator | openshift-operators | Trace storage |
| OpenTelemetry Operator | openshift-operators | Trace collection |
| Cluster Logging Operator | openshift-logging | Log collection |

### Storage

MinIO is deployed as S3-compatible object storage for both LokiStack (logs) and TempoStack (traces). For production, use AWS S3, Azure Blob, or another supported object storage.

### ObservabilityInstaller (Technology Preview)

The `ObservabilityInstaller` CRD simplifies distributed tracing setup by automatically creating:
- TempoStack with proper OpenShift authentication
- OpenTelemetry Collector with k8sattributes processor
- All necessary RBAC

## Demo Applications

### HotROD
A ride-sharing simulation with 4 interconnected services demonstrating distributed tracing in Go.

### .NET Tracing Demo
A 3-service .NET 8 application showing:
- Automatic ASP.NET Core instrumentation
- Automatic HttpClient instrumentation (context propagation)
- Custom spans with `ActivitySource`
- OTLP gRPC export

## Accessing the UI

After deployment, access the observability features in the OpenShift Console:

- **Observe → Logs** - View logs from all namespaces
- **Observe → Traces** - View distributed traces
- **Observe → Dashboards** - Custom dashboards
- **Observe → Targets** - Prometheus targets (with Monitoring plugin)

## Troubleshooting

### Traces not appearing

1. Check the OTel collector logs:
   ```bash
   oc logs -n observability deployment/tracing-collector
   ```

2. Verify the trace generator is running:
   ```bash
   oc get pods -n tracing-demo
   ```

3. Check TempoStack status:
   ```bash
   oc get tempostack -n observability -o yaml
   ```

### User can't access traces

Ensure the user has:
1. The `tempostack-traces-reader` ClusterRole bound
2. View access to the specific namespaces they want to see traces from

## References

- [COO Documentation](https://docs.redhat.com/en/documentation/red_hat_openshift_cluster_observability_operator)
- [OpenTelemetry .NET](https://opentelemetry.io/docs/languages/net/)
- [Jaeger HotROD](https://github.com/jaegertracing/jaeger/tree/main/examples/hotrod)
