# RDAS Architecture (Part 1 — Solution Design)

## Goal

Replace direct, per-channel SOAP consumption with a single **Reference Data
Aggregation Service (RDAS)** that exposes REST/JSON, owns the SOAP integration, and
provides consistency, caching, filtering, pagination, sorting and auditability.

## Component diagram

<img width="970" height="1145" alt="architecture_diagram" src="https://github.com/user-attachments/assets/17e3f80a-51cd-4aef-a1cc-4ee5ff04905f" />

### Text fallback

```
Channels ──HTTPS/JSON──► [Controllers → Validation → Query svc] ──► ReferenceDataService
                                              │                          │ (snapshot, in-memory)
                                       per-query cache                   │
                                                                  CountryInfoSoapClient
                                                                  (retry + circuit breaker)
                                                                         │ SOAP 1.2
                                                                         ▼
                                                            CountryInfo SOAP (3rd party)
```

## Key decision: snapshot-and-serve

The dataset is small (~246 countries) and changes rarely, but read volume is high.
Instead of translating each REST request into one-or-more SOAP calls, RDAS:

1. **Loads the entire dataset on startup** in **3 SOAP calls**
   (`FullCountryInfoAllCountries`, `ListOfContinentsByName`, `ListOfCurrenciesByName`),
   enriches it (resolving continent/currency names) into a canonical `Country` model,
   and holds it as an **immutable snapshot** in memory.
2. **Serves every user query from memory** — filtering, sorting and pagination are
   in-process stream operations. No user request ever touches SOAP.
3. **Refreshes the snapshot on a schedule** (default every 6h). The new snapshot is
   built into local variables and swapped atomically, so readers always see a
   consistent view and never a half-built one.

Consequences:

- **SOAP traffic ≈ 3 calls / 6h per instance** — orders of magnitude below the
  100 req/min provider limit, regardless of user traffic.
- **Latency** is in-memory (sub-millisecond filtering), not network-bound.
- **Resilience**: a SOAP outage only affects the *refresh*, not live traffic — the
  last good snapshot keeps serving (see [ENGINEERING.md](ENGINEERING.md), Part 4).

## Layers

| Layer            | Responsibility                                                        |
|------------------|------------------------------------------------------------------------|
| `web`            | HTTP contract: controllers, DTOs, validation, one global error handler |
| `service`        | `CountryQueryService` — filter/sort/paginate; whitelisted sort fields  |
| `cache`          | `ReferenceDataService` — snapshot lifecycle, scheduled refresh, health |
| `soap`           | `CountryInfoSoapClient` — the **only** component that speaks SOAP      |
| `domain`         | Canonical `Country`/`Language` model used everywhere except the wire   |

## Caching (two layers)

1. **Reference snapshot** (source data) — long-lived, scheduled refresh, the core of
   the design.
2. **Per-query cache** (Caffeine, bounded 1 000 entries, 10-min TTL) — memoises
   identical `(filter, sort, page)` queries; evicted whenever the snapshot refreshes.

See [ENGINEERING.md](ENGINEERING.md) Part 3 for the full caching rationale.

## Cross-cutting concerns

- **Resilience**: Resilience4j retry (transient SOAP blips) + circuit breaker
  (sustained outage) around the SOAP client; timeouts on the HTTP client.
- **Observability**: Actuator health (incl. a custom `referenceData` indicator and
  circuit-breaker state), Micrometer metrics, Prometheus scrape endpoint.
- **Security posture**: only RDAS holds the upstream endpoint config; container runs
  non-root with a read-only root filesystem.
- **Auditability**: a single ingress/egress point makes request logging and metrics
  centralised and uniform.

## Technology choices

| Concern        | Choice                    | Why                                             |
|----------------|---------------------------|-------------------------------------------------|
| Framework      | Spring Boot 3.3 / Java 17 | Required stack; mature, productive              |
| SOAP call      | RestTemplate + hand-built envelope | WSDL is tiny and stable; avoids brittle codegen |
| XML parsing    | Jackson `XmlMapper`       | Local-name matching tolerates SOAP namespaces   |
| Cache          | Caffeine                  | Fast, bounded, native Spring Cache support      |
| Resilience     | Resilience4j              | First-class Spring Boot 3 integration           |
| Docs           | springdoc-openapi         | Swagger UI + OpenAPI from annotations           |
```
