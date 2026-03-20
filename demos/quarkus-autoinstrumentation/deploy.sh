#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="quarkus-otel-demo"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "Usage: $0 [apply|build|restart|all|status]"
    echo ""
    echo "Commands:"
    echo "  apply    Apply OpenShift manifests (namespace, Instrumentation CR, deployments)"
    echo "  build    Run binary builds for all 3 services"
    echo "  restart  Restart deployments to pick up new images"
    echo "  all      Run apply + build + restart (full deploy)"
    echo "  status   Show current deployment status"
    echo ""
    echo "Examples:"
    echo "  $0 all              # Full deploy from scratch"
    echo "  $0 build restart    # Rebuild and restart (after code changes)"
    exit 1
}

apply() {
    echo "==> Applying OpenShift manifests..."
    oc apply -f "${SCRIPT_DIR}/openshift-deploy.yaml"
    if [[ -f "${SCRIPT_DIR}/dashboard.yaml" ]]; then
        echo "==> Applying Perses dashboard..."
        oc apply -f "${SCRIPT_DIR}/dashboard.yaml"
    fi
    echo ""
}

build_service() {
    local svc=$1
    echo "==> Building ${svc}..."
    oc start-build "${svc}" \
        --from-dir="${SCRIPT_DIR}/src/${svc}" \
        -n "${NAMESPACE}" \
        --follow
    echo ""
}

build() {
    build_service catalog-service
    build_service order-service
    build_service store-frontend
}

restart() {
    echo "==> Restarting deployments..."
    oc rollout restart deployment -n "${NAMESPACE}"
    echo "==> Waiting for rollouts..."
    oc rollout status deployment/catalog-service -n "${NAMESPACE}" --timeout=120s
    oc rollout status deployment/order-service -n "${NAMESPACE}" --timeout=120s
    oc rollout status deployment/store-frontend -n "${NAMESPACE}" --timeout=120s
    echo ""
    ROUTE=$(oc get route store-frontend -n "${NAMESPACE}" -o jsonpath='{.spec.host}' 2>/dev/null || true)
    if [[ -n "${ROUTE}" ]]; then
        echo "==> Route: https://${ROUTE}"
    fi
}

status() {
    echo "==> Pods:"
    oc get pods -n "${NAMESPACE}" -o wide
    echo ""
    echo "==> Deployments:"
    oc get deployments -n "${NAMESPACE}"
    echo ""
    echo "==> Route:"
    oc get route store-frontend -n "${NAMESPACE}" -o jsonpath='https://{.spec.host}{"\n"}' 2>/dev/null || echo "(not found)"
    echo ""
    echo "==> Instrumentation:"
    oc get instrumentation -n "${NAMESPACE}" 2>/dev/null || echo "(not found)"
}

if [[ $# -eq 0 ]]; then
    usage
fi

for cmd in "$@"; do
    case "${cmd}" in
        apply)   apply ;;
        build)   build ;;
        restart) restart ;;
        all)     apply; build; restart ;;
        status)  status ;;
        *)       echo "Unknown command: ${cmd}"; usage ;;
    esac
done
