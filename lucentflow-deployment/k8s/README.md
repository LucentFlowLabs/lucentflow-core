# LucentFlow Kubernetes (Phase 4)

Split deployment of the same fat JAR:

| Workload | Profile / flags | Role | Replicas |
|----------|-----------------|------|----------|
| `lucentflow-api` | `--spring.profiles.active=api` | REST + Actuator | horizontally scalable |
| `lucentflow-worker` | `--spring.profiles.active=worker` | Indexer + analyzer (`enable-api=false`; Actuator + indexer admin backfill) | **exactly 1** |

## HARD CONSTRAINT — single-writer worker

PostgreSQL TTL lease election (`worker_leases` / `WorkerLeaseCoordinator`) now gates indexer scan and analyzer drain.
**Deploy policy is unchanged for this release:** still run **exactly one** worker until multi-replica failover is validated.

1. `lucentflow-worker.spec.replicas` **MUST** be `1`
2. Rollout strategy **MUST** be `Recreate` (already set — do not switch to RollingUpdate)
3. **Do not** create a Service / Ingress for the worker
4. Apply `networkpolicy.yaml` so other pods cannot reach worker `:8080`
5. Do not `kubectl scale` / HPA the worker — lease is defense-in-depth, not a green light to multi-writer yet

Violating the deploy gate still risks overlapping writers during lease expiry windows and corrupts `sync_status` **ID=1**.

Worker readiness/liveness probes hit `/actuator/health` via the kubelet. That does not require a ClusterIP Service.

`POST /api/v1/admin/backfill` lives on the **worker** (indexer) process. The API Service returns **503**. Invoke via port-forward (NetworkPolicy still blocks pod-to-pod ingress):

```bash
kubectl -n <ns> port-forward deploy/lucentflow-worker 8080:8080
curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/backfill \
  -H "X-Admin-Key: $LUCENTFLOW_ADMIN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"fromBlock":100,"toBlock":200}'
```

The same rules are enforced by:

1. **CI early gate** — `python3 lucentflow-deployment/k8s/assert_single_writer.py` in `.github/workflows/ci.yml`
2. **Maven gate** — `com.lucentflow.ops.SingleWriterK8sGateTest` (runs under `mvn verify`)

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

## CI / deploy gate (manifest assertions)

Before merge and before apply, run the single-writer gate (also wired in `.github/workflows/ci.yml`):

```bash
python3 lucentflow-deployment/k8s/assert_single_writer.py
# Windows: py lucentflow-deployment/k8s/assert_single_writer.py
```

The gate fails if any of these regress:

| Check | Required |
|-------|----------|
| `lucentflow-worker` replicas | exactly `1` |
| Rollout strategy | `Recreate` |
| Annotations | `lucentflow.io/single-writer=true`, `max-replicas=1` |
| Service / HPA | none targeting `component=worker` |
| NetworkPolicy | `lucentflow-worker-deny-ingress` with `ingress: []` |
| Spring profile | `application-worker.yml` → `enable-api: false` |

## Verify single-writer posture (live cluster)

```bash
kubectl get deploy lucentflow-worker -o jsonpath='{.spec.replicas}{"\n"}{.spec.strategy.type}{"\n"}'
# expect: 1 / Recreate

kubectl get svc -l component=worker
# expect: empty (no worker Service)

kubectl get networkpolicy lucentflow-worker-deny-ingress
```
