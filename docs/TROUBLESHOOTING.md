# Troubleshooting RDAS on Kubernetes

A practical runbook. Start with the **first-look commands**, then jump to the matching
symptom.

## First-look commands

```bash
kubectl -n rdas get pods -o wide                       # pod status / restarts / node
kubectl -n rdas describe pod <pod>                     # events: scheduling, pulls, probes, OOM
kubectl -n rdas logs <pod>                             # app logs
kubectl -n rdas logs <pod> --previous                  # logs from a crashed/restarted container
kubectl -n rdas get events --sort-by=.lastTimestamp    # recent namespace events

# App's own diagnostics (port-forward first)
kubectl -n rdas port-forward svc/rdas 8080:80 &
curl -s localhost:8080/actuator/health | jq
curl -s localhost:8080/actuator/metrics/<name> | jq
```

---

## Symptom → cause → fix

### Pod stuck `Pending`
- **Causes:** insufficient cluster CPU/memory; no node matches.
- **Check:** `kubectl -n rdas describe pod <pod>` → *Events* (e.g. *Insufficient cpu*).
- **Fix:** add capacity, or lower `resources.requests` in `deployment.yaml`.

### Pod `ImagePullBackOff` / `ErrImagePull`
- **Causes:** wrong image name/tag; missing registry credentials; local image not in cluster.
- **Check:** `describe pod` → *Failed to pull image*.
- **Fix:** verify the image ref (`kubectl -n rdas get deploy rdas -o jsonpath='{..image}'`);
  add an `imagePullSecret`; for kind/minikube `kind load docker-image rdas:1.0.0` /
  `minikube image load rdas:1.0.0`.

### Pod `CrashLoopBackOff`
- **Causes:** app throws on startup; bad config; insufficient memory at boot.
- **Check:** `kubectl -n rdas logs <pod> --previous`.
- **Fix:** read the stack trace. Note: a **SOAP outage does *not* crash the app** — it
  logs an error and starts anyway, retrying on schedule. If it crashes, look for config
  binding errors or OOM (see below).

### Pod never becomes `Ready` (0/1)
- **Most common cause:** the **reference-data snapshot hasn't loaded**, so the readiness
  probe (which includes the `referenceData` check) stays DOWN.
- **Check:**
  ```bash
  curl -s localhost:8080/actuator/health/readiness | jq
  curl -s localhost:8080/actuator/health | jq '.components.referenceData'
  kubectl -n rdas logs <pod> | grep -i "reference data"
  ```
- **Fix:** confirm pods have **egress to the SOAP endpoint** (see *Connectivity* below).
  Once a refresh succeeds, readiness flips to UP automatically. If boot legitimately
  needs longer, raise `startupProbe.failureThreshold`.

### Requests return `503 Service Unavailable`
- **Cause A — cold start, no data yet:** snapshot never loaded (SOAP unreachable at
  startup). Body says *reference data is not yet available*.
- **Cause B — circuit breaker OPEN:** sustained SOAP failures.
- **Check:** `curl -s localhost:8080/actuator/health | jq '.components.circuitBreakers'`
  and the `referenceData` component.
- **Fix:** restore SOAP connectivity. Existing pods with a loaded snapshot keep serving
  (stale) and should *not* 503 — if they do, the very first load never succeeded.

### Connectivity to the SOAP service
```bash
# From inside a pod
kubectl -n rdas exec -it <pod> -- sh -c \
  'curl -s -o /dev/null -w "%{http_code}\n" \
   "http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL"'
```
- **Causes:** NetworkPolicy/egress firewall blocking outbound HTTP; no DNS; no internet
  egress from the node subnet; proxy required.
- **Fix:** allow egress to the endpoint; configure DNS; set proxy env vars if your
  network requires one; verify the endpoint URL in the ConfigMap.

### `OOMKilled` (restart reason in `describe pod`)
- **Cause:** heap/limit too low.
- **Fix:** raise `resources.limits.memory`; the container sets `MaxRAMPercentage=75`, so
  give it headroom. Check `jvm_memory_used_bytes` via `/actuator/prometheus`.

### HPA shows `<unknown>` targets / won't scale
- **Cause:** metrics-server not installed/working.
- **Check:** `kubectl top pods -n rdas`; `kubectl -n rdas describe hpa rdas`.
- **Fix:** install metrics-server; ensure `resources.requests` are set (they are) so
  utilisation can be computed.

### Ingress returns 404 / 502 / no route
- **Causes:** no ingress controller; host not resolving; Service selector mismatch.
- **Check:** `kubectl -n rdas describe ingress rdas`; `kubectl get svc -A | grep ingress`.
- **Fix:** install ingress-nginx; map `rdas.internal.loop.local` to the controller IP;
  confirm `kubectl -n rdas get endpoints rdas` lists pod IPs (empty ⇒ readiness failing).

### Stale data / changes not reflected
- **Cause:** snapshot refreshes every 6h by default; a refresh may be failing.
- **Check:** `curl -s localhost:8080/actuator/health | jq '.components.referenceData.details.loadedAt'`
  and logs for *Scheduled refresh failed*.
- **Fix:** to force fresh data, `kubectl -n rdas rollout restart deployment/rdas` (each
  new pod reloads on startup), or lower `rdas.cache.refresh-interval`.

---

## Useful diagnostics

```bash
# Snapshot freshness & dataset size
curl -s localhost:8080/actuator/health | jq '.components.referenceData.details'

# Circuit-breaker state
curl -s localhost:8080/actuator/health | jq '.components.circuitBreakers.details'

# Cache hit/miss
curl -s localhost:8080/actuator/metrics/cache.gets | jq

# All exposed metric names
curl -s localhost:8080/actuator/metrics | jq '.names'

# Live resource usage
kubectl top pods -n rdas
```

## Escalation checklist

1. Capture `kubectl -n rdas describe pod <pod>` and `logs --previous`.
2. Capture `/actuator/health` output.
3. Confirm SOAP reachability from inside a pod.
4. Note recent deploys (`kubectl -n rdas rollout history deployment/rdas`) and roll back
   if a release correlates with the incident.
```
