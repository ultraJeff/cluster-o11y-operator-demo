#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="quarkus-otel-demo"
ROUNDS=${1:-5}
DELAY=${2:-2}

ROUTE=$(oc get route store-frontend -n "${NAMESPACE}" -o jsonpath='{.spec.host}' 2>/dev/null || true)
if [[ -z "${ROUTE}" ]]; then
    echo "Error: could not find store-frontend route in namespace ${NAMESPACE}"
    echo "Make sure the demo is deployed first: ./deploy.sh all"
    exit 1
fi

BASE="https://${ROUTE}"
PRODUCTS=("PRD-001" "PRD-002" "PRD-003" "PRD-004")

echo "Generating traces against ${BASE}"
echo "Rounds: ${ROUNDS}, Delay: ${DELAY}s between requests"
echo "---"

request() {
    local method=$1 path=$2 label=$3
    shift 3
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "$@" "${BASE}${path}")
    printf "  %-6s %-35s -> %s  (%s)\n" "${method}" "${path}" "${status}" "${label}"
}

for ((i = 1; i <= ROUNDS; i++)); do
    echo ""
    echo "Round ${i}/${ROUNDS}"

    # Browse the full menu (frontend → catalog, JDBC SELECT)
    request GET /menu "list menu"
    sleep "${DELAY}"

    # Look up a specific product
    pid=${PRODUCTS[$((RANDOM % ${#PRODUCTS[@]}))]}
    request GET "/menu/${pid}" "product detail"
    sleep "${DELAY}"

    # Place an order (frontend → order-service → catalog validate + reserve + JDBC INSERT)
    qty=$(( (RANDOM % 3) + 1 ))
    request POST /order "place order" \
        -H "Content-Type: application/json" \
        -d "{\"productId\": \"${pid}\", \"quantity\": ${qty}}"
    sleep "${DELAY}"

    # List orders (frontend → order-service → JDBC SELECT)
    request GET /orders "list orders"
    sleep "${DELAY}"

    # Try an out-of-stock item every other round (expect 409)
    if (( i % 2 == 0 )); then
        request POST /order "out-of-stock order" \
            -H "Content-Type: application/json" \
            -d '{"productId": "PRD-005", "quantity": 1}'
        sleep "${DELAY}"
    fi

    # Trigger a validation error every 3rd round (expect 400)
    if (( i % 3 == 0 )); then
        request POST /order "validation error" \
            -H "Content-Type: application/json" \
            -d '{"productId": "", "quantity": 0}'
        sleep "${DELAY}"
    fi

    # Health check
    request GET /q/health "health check"
    sleep "${DELAY}"
done

echo ""
echo "---"
echo "Done. View traces in OpenShift console: Observe > Traces"
echo "Look for service names: store-frontend, order-service, catalog-service"
