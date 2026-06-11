# Engineering Discussion (Parts 3, 4 & 6)

This document answers the written parts of the assignment and explains the design
decisions behind the implementation.

---

## Part 3 — Data Processing Challenge

> The SOAP provider has introduced a usage limit of **100 requests per minute**.

### How the design reduces SOAP traffic

RDAS decouples user traffic from SOAP traffic entirely:

- User requests are served from an **in-memory snapshot** — they never call SOAP.
- The snapshot is built from **3 SOAP calls** (`FullCountryInfoAllCountries`,
  `ListOfContinentsByName`, `ListOfCurrenciesByName`) and refreshed on a schedule
  (default every 6 hours).

So SOAP usage is **~3 calls per refresh interval per instance** — e.g. with a 6-hour
refresh, **12 calls/day/instance**. Even 50 instances refreshing simultaneously would
issue 150 calls in a burst, still trivially within 100 req/min if mildly staggered.
User load has **zero** effect on SOAP traffic. We are nowhere near the limit.

### What data should be cached

The **entire reference dataset**, because it is:

- **Small** — ~246 countries, ~175 currencies, 6 continents (~150 KB).
- **Slow-changing** — country reference data changes on the order of months/years.
- **Read-heavy** — queried constantly by every channel.

Concretely cached:

1. **Snapshot cache** — the canonical, enriched `Country` list plus the
   continent/currency name maps and an ISO-code → country index for O(1) detail lookups.
2. **Per-query cache** — memoised results of `(filters, sort, page)` combinations
   (Caffeine, bounded 1 000 entries, 10-minute TTL). Saves recomputation for popular
   queries (dashboards, partner polling).

### Cache expiration strategy

- **Snapshot**: time-based, **scheduled full refresh** (`rdas.cache.refresh-interval`,
  default `PT6H`). Reference data has no event feed, so a periodic refresh is the
  pragmatic correctness/freshness trade-off. The interval is configurable per
  environment.
- **Per-query cache**: `expireAfterWrite = 10 min` **and** explicit eviction whenever
  the snapshot refreshes — so query results can never be staler than the snapshot.

### Cache refresh strategy

- **Asynchronous, scheduled** (`@Scheduled`) — refresh happens in the background, off
  the request path; users never wait for SOAP.
- **Atomic swap** — the new snapshot is fully built into local variables and only then
  swapped into the live `AtomicReference`. A partial/failed refresh never corrupts or
  empties the served data.
- **Serve-stale-on-failure** — if a refresh fails, the previous good snapshot keeps
  serving (configurable). The failure is logged and surfaced via metrics/health.
- **Warm on startup** — the cache loads when the app becomes ready, and the readiness
  probe only passes once it has, so no instance receives traffic with an empty cache.

### Justification

This is a classic **read-through cache of a small, slow-changing, read-heavy dataset**.
Caching the whole thing (rather than per-entity, on-demand) is simpler, removes SOAP
from the hot path completely, gives the best possible latency, and makes the provider's
rate limit a non-issue. The only cost is up-to-6-hour staleness, which is perfectly
acceptable for country reference data and tunable.

---

## Part 4 — Resilience Challenge

> The SOAP service becomes unavailable for **6 hours**.

### What happens when a request arrives

Because user requests are served from the in-memory snapshot, **they continue to
succeed normally** throughout the outage — the snapshot is already loaded and does not
depend on SOAP being up. Only **scheduled refreshes** during the window fail; with
`serve-stale-on-failure` (default on), the last good snapshot keeps serving and the
failure is logged + counted. Data simply ages by up to 6 hours — invisible to users
for a slow-changing dataset.

The dependency is protected by Resilience4j:

- **Retry** (3 attempts, exponential backoff) absorbs transient blips.
- **Circuit breaker** opens after sustained failure (≥50% of a 10-call window),
  stops hammering the dead service, and half-opens after 30s to probe recovery.

### How users should experience the failure

- **Normal case** (snapshot already loaded — the expected situation): **no visible
  impact.** Full functionality, normal latency.
- **Cold-start edge case** (the very first load never succeeded because SOAP was
  already down when the pod started): queries return **`503 Service Unavailable`** with
  a clear JSON body and a `Retry-After: 30` header — an honest, actionable error rather
  than a hang or a confusing 500. The readiness probe also fails, so Kubernetes keeps
  that pod out of the load balancer.

### What fallback mechanisms exist

1. **Stale-while-error** — serve the last good snapshot during refresh failures.
2. **Atomic swap** — failed refreshes never replace good data with bad/empty data.
3. **Circuit breaker** — fail fast instead of piling onto a dead dependency.
4. **Timeouts** — bounded connect/read timeouts so a slow SOAP cannot exhaust threads.
5. **Independent liveness vs readiness** — a SOAP outage does **not** trip liveness
   (no pointless restarts); it only affects readiness on a cold start.
6. *(Optional future)* persist the last snapshot to disk/Redis so even a cold start
   during an outage can serve recent data — see Part 6.

### What monitoring and alerting should be triggered

Exposed via Actuator + Micrometer/Prometheus:

- **Health**: custom `referenceData` indicator (loaded? how old?) and circuit-breaker
  state in `/actuator/health`.
- **Metrics**: refresh success/failure count and timestamp, snapshot age, circuit-breaker
  state transitions, SOAP call latency/error rate, cache hit ratio.

Suggested alerts:

| Alert                                   | Severity | Rationale                              |
|-----------------------------------------|----------|----------------------------------------|
| Snapshot age > 2× refresh interval      | Warning  | Refreshes are silently failing         |
| Circuit breaker OPEN                     | Warning  | Upstream down; still serving stale      |
| `referenceData` health DOWN (cold start) | Critical | Pod cannot serve — user impact         |
| Refresh failure count rising             | Warning  | Early signal before staleness bites     |
| SOAP error rate / latency spike          | Info     | Upstream degradation visibility         |

---

## Part 6 — Engineering Discussion

### Q1 — What if the SOAP limit dropped to **10 requests per minute**?

Almost nothing changes, because traffic is already decoupled from user load — RDAS
uses ~3 calls per refresh. To be safe under a tighter limit:

- **Stagger refreshes** across instances (jitter on the schedule) and/or elect a single
  **leader** to refresh and share the snapshot, so N pods don't all call at once.
- **Centralise the refresh**: a small scheduled job (or one designated instance) writes
  the snapshot to a **shared store (e.g. Redis)**; app instances read from there. SOAP
  is then called by exactly one actor — a handful of calls per interval, total.
- **Rate-limit the egress** to SOAP with a client-side limiter (Resilience4j
  `RateLimiter`) as a hard guard.

Even naively, 3 calls every 6 hours is far under 10/min; the only real risk is a
thundering herd of instances, which staggering/leader-election removes.

### Q2 — How would you scale to **20 million requests/day**?

20M/day ≈ **230 req/s average**, with peaks perhaps 3–5×. This is comfortable because
**every request is an in-memory lookup**, not a SOAP call.

- **Scale horizontally**: the app is stateless apart from the (identical) snapshot, so
  add replicas behind the Service. The provided **HPA** scales 3→20 pods on CPU/memory.
  Each pod handles thousands of req/s for in-memory work.
- **Keep SOAP traffic flat**: snapshot model means SOAP usage is independent of request
  volume. Move refresh to a **leader/shared cache (Redis)** so adding pods adds zero
  SOAP load.
- **Edge caching/CDN**: reference data is highly cacheable — add `Cache-Control`/`ETag`
  and front with a CDN or reverse-proxy cache to absorb most read traffic before it
  reaches the pods.
- **Tune the JVM/pods**: container-aware heap (already set), right-size requests/limits,
  enable HTTP keep-alive; consider readiness-gated rolling deploys (already configured).
- **Observability at scale**: dashboards on p99 latency, cache hit ratio, pod
  saturation; alert on SLO burn.

The architecture needs no fundamental change — it scales by adding stateless replicas.

### Q3 — What would you add with another week?

- **Persistent/shared snapshot** (Redis) + **leader-elected refresh** — survive
  cold-starts during outages and minimise SOAP egress fleet-wide.
- **`ETag`/`Cache-Control` + CDN** for client/edge caching.
- **Authentication & rate limiting per consumer** (API keys / OAuth2) for partner APIs,
  with per-client quotas and usage auditing.
- **Contract & integration tests** against a stubbed SOAP (WireMock) in CI, plus
  load tests (k6/Gatling) to validate the 20M/day target.
- **Richer querying**: multi-value filters (`continent=AF,EU`), full-text search,
  field selection/sparse responses.
- **Distributed tracing** (OpenTelemetry) and structured JSON logging with correlation
  IDs end-to-end.
- **CI/CD pipeline** (build, test, scan, image push, deploy) + Helm chart/Kustomize
  overlays per environment.
- **Resilience tooling**: bulkheads, a client-side egress `RateLimiter`, and chaos
  tests that kill the SOAP dependency to verify stale-serving behaviour.
```
