# HotROD Demo

The HotROD (Hot Rides On-Demand) application is a demo ride-sharing service from the Jaeger project that demonstrates distributed tracing across multiple microservices.

## Architecture

HotROD consists of 4 services running in a single container:
- **Frontend** (port 8080) - Web UI and API gateway
- **Customer** (port 8081) - Customer lookup service
- **Driver** (port 8082) - Driver location service (gRPC)
- **Route** (port 8083) - Route calculation service

## Deployment

```bash
oc apply -f hotrod.yaml
```

## Usage

1. Get the route URL:
   ```bash
   oc get route hotrod -n hotrod-demo -o jsonpath='https://{.spec.host}'
   ```

2. Open the URL in a browser

3. Click on a customer name (Rachel, Trom, etc.) to request a ride

4. View the generated traces in **Observe → Traces**

## Source Code

https://github.com/jaegertracing/jaeger/tree/main/examples/hotrod
