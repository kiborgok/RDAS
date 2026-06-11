# RDAS API Reference (Part 2 — API Design)

Base path: `/api/v1` · Media type: `application/json` · Interactive docs: `/swagger-ui.html`

The business requirement — *search for countries using a single API* with search by
name, filtering by continent/currency/language, country details, countries sharing a
currency, pagination and sorting — is satisfied by the endpoints below. The SOAP
service has no single operation that does this; RDAS composes it.

---

## Endpoints

| Method | Path                                   | Purpose                                |
|--------|----------------------------------------|----------------------------------------|
| GET    | `/api/v1/countries`                    | Search/list countries (filter+sort+page) |
| GET    | `/api/v1/countries/{code}`             | Get a single country by ISO code       |
| GET    | `/api/v1/currencies/{code}/countries`  | Countries sharing a currency           |
| GET    | `/api/v1/continents`                   | Continent lookup list                  |
| GET    | `/api/v1/currencies`                   | Currency lookup list                   |
| GET    | `/api/v1/languages`                    | Language lookup list                   |

Operational (Actuator): `/actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness`, `/actuator/metrics`, `/actuator/prometheus`.

---

## GET /api/v1/countries

Search and list countries. All filters are optional, combinable, and case-insensitive.

### Query parameters

| Param       | Type    | Default     | Notes                                                        |
|-------------|---------|-------------|--------------------------------------------------------------|
| `name`      | string  | —           | Partial, case-insensitive country-name match                |
| `continent` | string  | —           | Continent code (`AF`) **or** name (`Africa`)                 |
| `currency`  | string  | —           | Currency ISO code (`KES`) **or** name (`Shillings`)          |
| `language`  | string  | —           | Language ISO code (`en`) **or** name (`English`)             |
| `page`      | int     | `0`         | Zero-based page index (`>= 0`)                               |
| `size`      | int     | `20`        | Page size (`1..100`)                                         |
| `sort`      | string  | `name,asc`  | `field[,asc\|desc]`; fields: `name`, `code`, `capital`, `continent`, `currency`, `phoneCode` |

### Example

```bash
curl 'http://localhost:8080/api/v1/countries?continent=AF&size=2&sort=name,asc'
```

```json
{
  "content": [
    {
      "code": "DZ",
      "name": "Algeria",
      "capitalCity": "Algiers",
      "phoneCode": "213",
      "continentCode": "AF",
      "continent": "Africa",
      "currencyCode": "DZD",
      "currency": "Algeria Dinars",
      "flagUrl": "http://www.oorsprong.org/.../Algeria.jpg",
      "languages": [{ "code": "ar", "name": "Arabic" }]
    },
    { "code": "AO", "name": "Angola", "...": "..." }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 56,
  "totalPages": 28,
  "first": true,
  "last": false,
  "sort": "name,asc"
}
```

---

## GET /api/v1/countries/{code}

Returns the full detail for one country by ISO code (case-insensitive).

```bash
curl 'http://localhost:8080/api/v1/countries/KE'
```

```json
{
  "code": "KE", "name": "Kenya", "capitalCity": "Nairobi", "phoneCode": "254",
  "continentCode": "AF", "continent": "Africa",
  "currencyCode": "KES", "currency": "Shillings",
  "flagUrl": "http://www.oorsprong.org/.../Kenya.jpg",
  "languages": [{ "code": "swa", "name": "Swahili" }]
}
```

`404 Not Found` if the code does not exist.

---

## GET /api/v1/currencies/{code}/countries

All countries that use the given currency (ISO code or name). Supports `page`, `size`
and `sort` exactly like `/countries`. Returns the same paginated envelope.

```bash
curl 'http://localhost:8080/api/v1/currencies/USD/countries?size=5'
```

---

## Lookup endpoints

`GET /api/v1/continents`, `/currencies`, `/languages` each return a sorted list of
`{ "code": "...", "name": "..." }` — handy for populating filter drop-downs.

---

## Pagination envelope

Every list endpoint returns:

| Field           | Type    | Meaning                          |
|-----------------|---------|----------------------------------|
| `content`       | array   | Items on this page               |
| `page`          | int     | Zero-based page index            |
| `size`          | int     | Requested page size              |
| `totalElements` | long    | Total matches across all pages   |
| `totalPages`    | int     | Total number of pages            |
| `first` / `last`| boolean | Page-position flags              |
| `sort`          | string  | Applied sort, e.g. `name,asc`    |

---

## Errors

All errors share one shape, produced by a single global handler:

```json
{
  "timestamp": "2026-06-11T12:05:32.981Z",
  "status": 400,
  "error": "Bad Request",
  "message": "size must be <= 100",
  "path": "/api/v1/countries",
  "details": []
}
```

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| `400`  | Invalid input: bad `sort` field/direction, `size` out of range, negative `page`, wrong parameter type |
| `404`  | Unknown country code                                                 |
| `503`  | Reference data not yet loaded, circuit breaker open, or SOAP error (includes `Retry-After: 30`) |
| `500`  | Unexpected error                                                     |

---

## Design notes

- **One read API, many filters.** A single `/countries` endpoint with optional,
  combinable filters keeps the contract small and avoids a proliferation of
  near-duplicate endpoints.
- **Codes *or* names.** `continent`, `currency` and `language` accept either the code
  or the human name so callers don't need a lookup round-trip first.
- **Whitelisted sorting.** `sort` only accepts known fields (an enum), preventing
  arbitrary/injection-style sort expressions and returning a clear `400` otherwise.
- **Bounded paging.** `size` is capped (`max-page-size`, default 100) to protect the
  service from unbounded result requests.
- **Versioned base path** (`/api/v1`) leaves room to evolve without breaking clients.
