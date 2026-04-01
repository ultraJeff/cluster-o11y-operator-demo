#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="quarkus-otel-demo"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OC_ARGS=()
[[ -n "${OC_CONTEXT:-}" ]] && OC_ARGS+=(--context="${OC_CONTEXT}")

usage() {
    echo "Usage: $0 [crash|recover|status]"
    echo ""
    echo "Simulates a catalog-service OOM failure to demonstrate observability"
    echo "signal correlation with Korrel8r."
    echo ""
    echo "Commands:"
    echo "  crash    Lower catalog-service memory to 32Mi, kill healthy pod,"
    echo "           then generate traffic to produce error traces (500s)"
    echo "  recover  Restore original memory limits and wait for healthy rollout"
    echo "  status   Show current pod state and recent events"
    echo ""
    echo "Environment:"
    echo "  OC_CONTEXT  oc context to use (optional, e.g. default/api-mycluster:6443/admin)"
    echo ""
    echo "What to look for after 'crash':"
    echo "  - Observe → Alerting: KubePodCrashLooping alert for catalog-service"
    echo "  - Troubleshooting panel (puzzle icon in masthead): correlated signals"
    echo "  - Observe → Traces: 500-status traces from store-frontend / order-service"
    echo "  - Grafana dashboard: spike in restarts, drop in ready pods"
    exit 1
}

wait_for_crash() {
    echo "  Waiting for OOM crash..."
    for ((w = 1; w <= 20; w++)); do
        local phase
        phase=$(oc "${OC_ARGS[@]}" get pods -n "${NAMESPACE}" -l app=catalog-service \
            -o jsonpath='{.items[?(@.status.containerStatuses[0].state.waiting)].status.containerStatuses[0].state.waiting.reason}' 2>/dev/null || true)
        if [[ "${phase}" == *"CrashLoopBackOff"* ]] || [[ "${phase}" == *"Error"* ]]; then
            echo "  catalog-service is crash-looping."
            return 0
        fi
        sleep 3
    done
    echo "  Warning: pod hasn't crashed yet — it may need traffic to trigger OOM."
}

crash() {
    echo "==> Step 1: Lowering catalog-service memory to 32Mi..."
    oc "${OC_ARGS[@]}" patch deployment catalog-service -n "${NAMESPACE}" --type=json \
        -p '[{"op":"replace","path":"/spec/template/spec/containers/0/resources/limits/memory","value":"32Mi"},
             {"op":"replace","path":"/spec/template/spec/containers/0/resources/requests/memory","value":"32Mi"}]'
    echo ""

    sleep 10
    wait_for_crash

    echo ""
    echo "==> Step 2: Removing healthy pods (old ReplicaSet)..."
    local old_rs
    for rs in $(oc "${OC_ARGS[@]}" get rs -n "${NAMESPACE}" -l app=catalog-service \
        -o jsonpath='{range .items[?(@.status.replicas>0)]}{.metadata.name}{"\n"}{end}'); do
        local rs_mem
        rs_mem=$(oc "${OC_ARGS[@]}" get rs "${rs}" -n "${NAMESPACE}" \
            -o jsonpath='{.spec.template.spec.containers[0].resources.limits.memory}' 2>/dev/null || true)
        if [[ "${rs_mem}" != "32Mi" ]]; then
            echo "  Scaling down ${rs} (memory: ${rs_mem})..."
            oc "${OC_ARGS[@]}" scale rs "${rs}" --replicas=0 -n "${NAMESPACE}"
        fi
    done
    echo ""

    sleep 5
    echo "==> Step 3: Generating traffic against broken service..."
    local route
    route=$(oc "${OC_ARGS[@]}" get route store-frontend -n "${NAMESPACE}" \
        -o jsonpath='{.spec.host}' 2>/dev/null || true)
    if [[ -z "${route}" ]]; then
        echo "  Could not find store-frontend route."
        return 1
    fi

    local base="https://${route}"
    echo "  Target: ${base}"
    echo ""
    for ((i = 1; i <= 15; i++)); do
        printf "  %2d  GET /menu -> %s  |  POST /order -> %s  |  GET /orders -> %s\n" \
            "$i" \
            "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 "${base}/menu")" \
            "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 -X POST "${base}/order" \
                -H 'Content-Type: application/json' -d '{"productId":"PRD-001","quantity":1}')" \
            "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 "${base}/orders")"
        sleep 1
    done

    echo ""
    echo "==> Failure simulation complete."
    echo ""
    echo "Where to look:"
    echo "  1. Observe → Alerting     Check for KubePodCrashLooping on catalog-service"
    echo "  2. Troubleshooting panel  Click the puzzle icon in the console masthead"
    echo "  3. Observe → Traces       Look for 500-status traces from store-frontend"
    echo "  4. Grafana dashboard      $(oc "${OC_ARGS[@]}" get route grafana -n "${NAMESPACE}" -o jsonpath='https://{.spec.host}' 2>/dev/null || echo '(not deployed)')"
    echo ""
    echo "Run '$0 recover' when done exploring."
}

recover() {
    echo "==> Restoring catalog-service to original memory limits..."
    oc "${OC_ARGS[@]}" patch deployment catalog-service -n "${NAMESPACE}" --type=json \
        -p '[{"op":"replace","path":"/spec/template/spec/containers/0/resources/limits/memory","value":"512Mi"},
             {"op":"replace","path":"/spec/template/spec/containers/0/resources/requests/memory","value":"256Mi"}]'
    echo ""
    echo "==> Waiting for healthy rollout..."
    oc "${OC_ARGS[@]}" rollout status deployment/catalog-service -n "${NAMESPACE}" --timeout=120s
    echo ""
    echo "==> Pods:"
    oc "${OC_ARGS[@]}" get pods -n "${NAMESPACE}" -l app=catalog-service
    echo ""

    local route
    route=$(oc "${OC_ARGS[@]}" get route store-frontend -n "${NAMESPACE}" \
        -o jsonpath='{.spec.host}' 2>/dev/null || true)
    if [[ -n "${route}" ]]; then
        echo "==> Smoke test:"
        printf "  GET /menu -> %s\n" "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 "https://${route}/menu")"
    fi
    echo ""
    echo "catalog-service recovered."
}

status() {
    echo "==> Pods:"
    oc "${OC_ARGS[@]}" get pods -n "${NAMESPACE}" -l app=catalog-service -o wide
    echo ""
    echo "==> Recent events:"
    oc "${OC_ARGS[@]}" get events -n "${NAMESPACE}" \
        --field-selector involvedObject.kind=Pod \
        --sort-by='.lastTimestamp' 2>/dev/null | grep catalog | tail -10
    echo ""
    echo "==> Current memory limits:"
    oc "${OC_ARGS[@]}" get deployment catalog-service -n "${NAMESPACE}" \
        -o jsonpath='  requests: {.spec.template.spec.containers[0].resources.requests.memory}{"\n"}  limits:   {.spec.template.spec.containers[0].resources.limits.memory}{"\n"}'
}

if [[ $# -eq 0 ]]; then
    usage
fi

case "$1" in
    crash)   crash ;;
    recover) recover ;;
    status)  status ;;
    *)       echo "Unknown command: $1"; usage ;;
esac
