# RBAC for Observability

## Traces Access

The TempoStack gateway uses OpenShift RBAC to control tenant access. The OPA sidecar performs SubjectAccessReview checks against the `tempo.grafana.com` API group.

### Grant a user access to view traces

1. Apply the traces-reader ClusterRole:
   ```bash
   oc apply -f traces-reader.yaml
   ```

2. Bind the role to a user:
   ```bash
   oc create clusterrolebinding <user>-tempostack-traces-reader \
     --clusterrole=tempostack-traces-reader \
     --user=<username>
   ```

### Grant a user access to a namespace

Users also need view access to namespaces to see traces from those namespaces:

```bash
oc create rolebinding <user>-view \
  --clusterrole=view \
  --user=<username> \
  -n <namespace>
```

## Example: Full access for user1

```bash
# Grant traces-reader role
oc apply -f traces-reader.yaml
oc create clusterrolebinding user1-tempostack-traces-reader \
  --clusterrole=tempostack-traces-reader \
  --user=user1

# Grant namespace access
oc create rolebinding user1-view --clusterrole=view --user=user1 -n hotrod-demo
oc create rolebinding user1-view --clusterrole=view --user=user1 -n dotnet-tracing-demo
```
