# LucentFlow Kubernetes (Phase 4)

Split deployment of the same fat JAR:

| Workload | Profile / flags | Role |
|----------|-----------------|------|
| `lucentflow-api` | `--spring.profiles.active=api` | REST + Actuator |
| `lucentflow-worker` | `--spring.profiles.active=worker` | Indexer + analyzer |

## Apply

```bash
kubectl create secret generic lucentflow-secrets \
  --from-literal=POSTGRES_PASSWORD=... \
  --from-literal=LUCENTFLOW_ADMIN_API_KEY=... \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/lucentflow

kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

Build/push the image from the repository root Dockerfile before applying.
