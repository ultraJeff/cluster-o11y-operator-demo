# Cluster Observability Operator Demo

This repository demonstrates the **Red Hat OpenShift Cluster Observability Operator (COO)** with all five UI plugins, MonitoringStack for Prometheus-based monitoring, and full logging integration with LokiStack.

## Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                   OpenShift Console Enhancements                 │
├─────────────────────────────────────────────────────────────────┤
│  Observe → Traces      │  Distributed Tracing UIPlugin          │
│  Observe → Logs        │  Logging UIPlugin + LokiStack          │
│  Observe → Dashboards  │  Dashboards UIPlugin (Perses)          │
│  Observe → Alerting    │  Monitoring UIPlugin (ACM support)     │
│  Resource → Lightbulb  │  Troubleshooting Panel (Korrel8r)      │
├─────────────────────────────────────────────────────────────────┤
│                   Backend Components                             │
├─────────────────────────────────────────────────────────────────┤
│  MonitoringStack       │  Prometheus + Alertmanager             │
│  LokiStack             │  Log aggregation + query               │
│  Collectors            │  Vector-based log collection           │
└─────────────────────────────────────────────────────────────────┘
```

## Components

| Component | Description |
|-----------|-------------|
| **MonitoringStack** | Prometheus-based monitoring for custom metrics |
| **UIPlugin (Distributed Tracing)** | Adds Observe → Traces to console |
| **UIPlugin (Logging)** | Adds Observe → Logs with LokiStack integration |
| **UIPlugin (Dashboards)** | Adds custom dashboard support via Perses |
| **UIPlugin (Monitoring)** | Enhanced alerting with ACM support |
| **UIPlugin (Troubleshooting Panel)** | Korrel8r-powered troubleshooting sidebar |
| **LokiStack** | Log storage and query engine |
| **ClusterLogForwarder** | Routes logs to LokiStack |

## Prerequisites

- OpenShift 4.14+
- Cluster admin access

## Quick Start

### 1. Install the Cluster Observability Operator

```bash
oc apply -f resources/subscription.yaml

# Wait for operator
until oc get csv -n openshift-cluster-observability-operator | grep -q "Succeeded"; do
  echo "Waiting for COO operator..."
  sleep 10
done
```

### 2. Deploy MonitoringStack and UIPlugins

```bash
oc apply -k resources/
```

### 3. (Optional) Deploy Logging with LokiStack

For full Observe → Logs functionality:

```bash
# Install operators first
oc apply -f resources/logging/namespace.yaml
oc apply -f resources/logging/operators.yaml

# Wait for operators
until oc get csv -n openshift-operators-redhat | grep -q "loki.*Succeeded"; do sleep 10; done
until oc get csv -n openshift-logging | grep -q "cluster-logging.*Succeeded"; do sleep 10; done

# Deploy MinIO storage
oc apply -f resources/logging/minio.yaml
oc wait --for=condition=Available deployment/minio -n openshift-logging --timeout=120s
oc exec -n openshift-logging deploy/minio -- mkdir -p /data/loki

# Deploy LokiStack and log forwarder
oc apply -f resources/logging/lokistack.yaml
oc apply -f resources/logging/clusterlogforwarder.yaml
```

## UIPlugins

### Distributed Tracing
- **Location**: Observe → Traces
- **Requirements**: TempoStack or TempoMonolithic instance
- **Features**: View traces, filter by service/operation, Gantt chart visualization

### Logging
- **Location**: Observe → Logs
- **Requirements**: LokiStack (see `resources/logging/`)
- **Features**: Query logs by namespace, pod, container, severity

### Dashboards
- **Location**: Observe → Dashboards
- **Requirements**: None
- **Features**: Create and manage custom Perses dashboards

### Monitoring
- **Location**: Observe → Alerting (enhanced)
- **Requirements**: MonitoringStack or ACM
- **Features**: Enhanced alerting with multi-cluster support

### Troubleshooting Panel
- **Location**: Lightbulb icon on any resource page
- **Requirements**: None
- **Features**: Korrel8r-powered correlation of related resources

## Directory Structure

```
resources/
├── subscription.yaml              # COO operator subscription
├── namespace.yaml                 # coo-service-mesh namespace
├── monitoringstack.yaml           # Prometheus + Alertmanager
├── podmonitor-istio-proxies.yaml  # Scrape Envoy metrics
├── servicemonitor-istiod.yaml     # Scrape Istiod metrics
├── uiplugin-tracing.yaml          # Distributed Tracing plugin
├── uiplugin-logging.yaml          # Logging plugin
├── uiplugin-dashboards.yaml       # Dashboards plugin
├── uiplugin-monitoring.yaml       # Monitoring plugin
├── uiplugin-troubleshooting-panel.yaml  # Troubleshooting plugin
├── kustomization.yaml
└── logging/                       # Full logging stack
    ├── namespace.yaml
    ├── operators.yaml
    ├── minio.yaml
    ├── lokistack.yaml
    ├── clusterlogforwarder.yaml
    └── README.md
```

## Verification

```bash
# Check UIPlugins
oc get uiplugins

# Check MonitoringStack
oc get monitoringstack -n coo-service-mesh

# Check LokiStack (if deployed)
oc get lokistack -n openshift-logging

# Check console plugins are registered
oc get consoles.operator.openshift.io cluster -o jsonpath='{.spec.plugins}'
```

## Documentation

- [Cluster Observability Operator Documentation](https://docs.redhat.com/en/documentation/red_hat_openshift_cluster_observability_operator/1-latest/html-single/installing_red_hat_openshift_cluster_observability_operator/index)
- [UI Plugins Documentation](https://docs.redhat.com/en/documentation/red_hat_openshift_cluster_observability_operator/1-latest/html-single/ui_plugins_for_red_hat_openshift_cluster_observability_operator/index)

## License

Apache License 2.0

