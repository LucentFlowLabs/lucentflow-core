# LucentFlow Kubernetes (Phase 4)

Split deployment of the same fat JAR:

| Workload | Profile / flags | Role | Replicas |
|----------|-----------------|------|----------|
| `lucentflow-api` | `--spring.profiles.active=api` | REST + Actuator | horizontally scalable |
| `lucentflow-worker` | `--spring.profiles.active=worker` | Indexer + analyzer (`enable-api=false`; Actuator only) | **exactly 1** |

## HARD CONSTRAINT — single-writer worker

Until lease-based leader election ships, **never run more than one worker**:

1. `lucentflow-worker.spec.replicas` **MUST** be `1`
2. Rollout strategy **MUST** be `Recreate` (already set — do not switch to RollingUpdate)
3. **Do not** create a Service / Ingress for the worker
4. Apply `networkpolicy.yaml` so other pods cannot reach worker `:8080`
5. Do not scale the worker Deployment (`kubectl scale` / HPA) without leader election

Violating this races `sync_status` **ID=1** checkpoints and corrupts the indexer cursor.

Worker readiness/liveness probes hit `/actuator/health` via the kubelet. That does not require a ClusterIP Service.

## Apply

```bash
kubectl create secret generic lucentflow-secrets \
  --from-literal=POSTGRES_PASSWORD=... \
  --from-literal=LUCENTFLOW_ADMIN_API_KEY=... \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/lucentflow

kubectl apply -f configmap.yaml
kubectl apply -f networkpolicy.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

Build/push the image from the repository root Dockerfile before applying.

## Verify single-writer posture

```bash
kubectl get deploy lucentflow-worker -o jsonpath='{.spec.replicas}{"\n"}{.spec.strategy.type}{"\n"}'
# expect: 1 / Recreate

kubectl get svc -l component=worker
# expect: empty (no worker Service)

kubectl get networkpolicy lucentflow-worker-deny-ingress
```
