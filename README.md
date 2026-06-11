# Reference Data Aggregation Service (RDAS)

Country reference data at LOOP DFS.

RDAS exposes a clean, consistent **REST/JSON** API and consumes the third-party
**CountryInfo SOAP** service internally. All channels (mobile, web, partner APIs,
internal portals) call RDAS instead of the SOAP service directly — giving consistent
responses, filtering, pagination, sorting, caching, auditability, and a single place
that holds the upstream credentials.

---

## Why this exists

Every channel previously called the SOAP service directly, which caused:

| Problem (before)                          | How RDAS solves it                                            |
|-------------------------------------------|---------------------------------------------------------------|
| Inconsistent responses across channels    | One canonical JSON model + one global error format            |
| Poor performance (repeated SOAP calls)    | In-memory snapshot; user queries never hit SOAP               |
| Limited filtering                         | Filter by name, continent, currency, language                 |
| No pagination                             | Page/size on every list endpoint                              |
| Lack of auditability                      | Centralised logging/metrics at one choke point                |
| No centralised caching                    | Snapshot cache + per-query cache                              |
| SOAP credentials spread across apps       | Only RDAS talks SOAP                                          |

## Tech stack

- **Java 17+** (built and tested on JDK 21)
- **Spring Boot 3.3**
- **Maven**
- Caffeine (caching), Resilience4j (retry + circuit breaker), springdoc-openapi
  (Swagger UI), Spring Boot Actuator + Micrometer/Prometheus (ops).

## Requirements implemented

Pagination · Sorting · Filtering · Caching · Global error handling · Input validation.

---

## Quick start

```bash
# Build + test
mvn clean verify

# Run
mvn spring-boot:run
#   or
java -jar target/rdas-1.0.0.jar
```

The service boots on **http://localhost:8080**, warms its cache from the SOAP service
on startup (~250 countries in 3 SOAP calls), and is then ready.

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health:** http://localhost:8080/actuator/health

### Try it

```bash
# Search by name
curl 'http://localhost:8080/api/v1/countries?name=ken'

# Filter by continent, paginate, sort descending by name
curl 'http://localhost:8080/api/v1/countries?continent=AF&page=0&size=10&sort=name,desc'

# Filter by language
curl 'http://localhost:8080/api/v1/countries?language=swahili'

# Country detail
curl 'http://localhost:8080/api/v1/countries/KE'

# Countries sharing a currency
curl 'http://localhost:8080/api/v1/currencies/USD/countries'
```

See full endpoint reference in [`docs/API.md`](docs/API.md).

---

## Architecture at a glance

```
 Channels ─► RDAS REST API ─► CountryQueryService ─► ReferenceDataService (snapshot)
 (mobile,                                                   ▲
  web,                                          (3 SOAP calls on startup
  partners,                                      + every 6h refresh)
  portals)                                                  │
                                              CountryInfoSoapClient ─► CountryInfo SOAP
                                              (retry + circuit breaker)
```

The whole dataset is loaded into memory once and refreshed on a schedule, so **user
requests never trigger a SOAP call**. Full diagram and rationale in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Project layout

```
src/main/java/com/ncba/rdas
├── config/      RdasProperties, RestTemplate, cache, OpenAPI
├── soap/        CountryInfoSoapClient + Jackson-XML response DTOs
├── cache/       ReferenceDataService (snapshot, scheduled refresh) + health indicator
├── domain/      Country, Language (canonical model)
├── service/     CountryQueryService (filter/sort/paginate), criteria, sort fields
└── web/         Controllers, response DTOs, global exception handler
docs/            API, architecture, k8s deploy + troubleshooting, engineering answers
k8s/             Manifests + deploy.sh
Dockerfile       Multi-stage, non-root, container-aware JVM
```

## Configuration

All settings live under `rdas.*` in [`application.yml`](src/main/resources/application.yml)
and can be overridden by environment variables (e.g. `SPRING_APPLICATION_JSON`, see
[`k8s/configmap.yaml`](k8s/configmap.yaml)).

| Property                          | Default | Meaning                                  |
|-----------------------------------|---------|------------------------------------------|
| `rdas.soap.endpoint`              | oorsprong URL | Upstream SOAP endpoint              |
| `rdas.soap.connect-timeout-ms`    | 5000    | Connect timeout                          |
| `rdas.soap.read-timeout-ms`       | 15000   | Read timeout                             |
| `rdas.cache.refresh-interval`     | PT6H    | Full snapshot refresh cadence            |
| `rdas.cache.serve-stale-on-failure` | true | Keep serving last good data on refresh failure |
| `rdas.query.default-page-size`    | 20      | Default page size                        |
| `rdas.query.max-page-size`        | 100     | Maximum allowed page size                |

## Testing

```bash
mvn test
```

Covers SOAP-response parsing, query filtering/sorting/pagination, controller
validation/error mapping, and Spring context wiring (18 tests).

## Deployment (Kubernetes)

```bash
./k8s/deploy.sh 1.0.0 ghcr.io/<your-org>
```

Step-by-step guide: [`docs/KUBERNETES.md`](docs/KUBERNETES.md) ·
Troubleshooting: [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

## Further reading

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — solution design + diagram (Part 1)
- [`docs/API.md`](docs/API.md) — API design & reference (Part 2)
- [`docs/ENGINEERING.md`](docs/ENGINEERING.md) — caching, resilience & scaling discussion (Parts 3, 4, 6)
