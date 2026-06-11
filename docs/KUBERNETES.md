# Deploying RDAS on Kubernetes

## Prerequisites

- A Kubernetes cluster (kind/minikube for local; EKS/GKE/AKS for cloud) and `kubectl`
  pointed at it (`kubectl cluster-info`).
- Docker (to build the image).
- For autoscaling: the **metrics-server** installed (`kubectl top pods` should work).
- For the Ingress: an **ingress-nginx** controller in the cluster.
- The pods must have **outbound network access** to the CountryInfo SOAP endpoint.

## What gets deployed (`k8s/`)

| File              | Resource        | Purpose                                            |
|-------------------|-----------------|----------------------------------------------------|
| `namespace.yaml`  | Namespace       | Isolates RDAS in the `rdas` namespace              |
| `configmap.yaml`  | ConfigMap       | Externalised `rdas.*` config via `SPRING_APPLICATION_JSON` |
| `deployment.yaml` | Deployment      | 3 replicas, probes, resources, security context    |
| `service.yaml`    | Service         | ClusterIP `rdas:80` → pod `:8080`                  |
| `hpa.yaml`        | HPA             | Autoscale 3→20 on CPU 70% / memory 80%             |
| `ingress.yaml`    | Ingress         | External routing (`rdas.internal.loop.local`)      |

## Option A — one command

```bash
# Local image (kind/minikube): build and deploy
./k8s/deploy.sh

# Build, push to a registry, and deploy
./k8s/deploy.sh 1.0.0 ghcr.io/<your-org>
```

The script builds the image, (optionally) pushes it, applies all manifests, sets the
Deployment image, and waits for the rollout.

> **kind/minikube tip:** with a local-only image, load it into the cluster so nodes can
> pull it:
> `kind load docker-image rdas:1.0.0`  ·  `minikube image load rdas:1.0.0`

## Option B — manual, step by step

```bash
# 1. Build the image
docker build -t ghcr.io/<your-org>/rdas:1.0.0 .
docker push   ghcr.io/<your-org>/rdas:1.0.0

# 2. Namespace + config
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml

# 3. Workload + networking
kubectl apply -f k8s/deployment.yaml
kubectl -n rdas set image deployment/rdas rdas=ghcr.io/<your-org>/rdas:1.0.0
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

# 4. Autoscaling
kubectl apply -f k8s/hpa.yaml

# 5. Wait for rollout
kubectl -n rdas rollout status deployment/rdas
```

## Verify

```bash
kubectl -n rdas get pods,svc,hpa,ingress

# Port-forward and smoke-test without the Ingress
kubectl -n rdas port-forward svc/rdas 8080:80 &
curl 'http://localhost:8080/actuator/health'
curl 'http://localhost:8080/api/v1/countries?name=ken'
```

Via the Ingress, add a hosts entry (or DNS) for `rdas.internal.loop.local` pointing at
the ingress controller's external IP, then `curl http://rdas.internal.loop.local/api/v1/countries`.

## Probes & startup behaviour

- **startupProbe** hits `/actuator/health/readiness` and allows up to ~60s for the app
  to boot *and warm the cache* from SOAP before liveness/readiness kick in.
- **readinessProbe** includes the custom `referenceData` health check — a pod only
  receives traffic once its snapshot is loaded.
- **livenessProbe** checks only liveness state, so a SOAP outage (serving stale data)
  never triggers a restart.

## Configuration & secrets

- Non-secret config: edit `k8s/configmap.yaml` (`SPRING_APPLICATION_JSON`) and re-apply;
  then `kubectl -n rdas rollout restart deployment/rdas`.
- If the SOAP endpoint ever requires credentials, store them in a `Secret` and reference
  via `envFrom`/`env` — do **not** put them in the ConfigMap.

## Rolling updates & rollback

```bash
# Deploy a new version
kubectl -n rdas set image deployment/rdas rdas=ghcr.io/<your-org>/rdas:1.1.0
kubectl -n rdas rollout status deployment/rdas

# Roll back if needed
kubectl -n rdas rollout undo deployment/rdas
```

`maxUnavailable: 0` + `maxSurge: 1` gives zero-downtime rollouts; each new pod must pass
readiness (cache warmed) before an old one is removed.

## Scaling

- Automatic: the HPA scales on CPU/memory (3→20).
- Manual: `kubectl -n rdas scale deployment/rdas --replicas=6`.

## Teardown

```bash
kubectl delete namespace rdas
```
