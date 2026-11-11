# API Reference

Raylevation exposes a single REST endpoint, `/api/v1/lookup`, available as both `GET` (query
string) and `POST` (JSON body). Both return the same response shape. The code blocks below are
pulled directly from the current source via `codeinclude`, so they stay in sync with the
endpoint signatures as the code changes.

<!--codeinclude-->
[RaylevationController.kt](../src/main/kotlin/com/raynigon/raylevation/base/controller/RaylevationController.kt)
<!--/codeinclude-->

## `GET /api/v1/lookup`

Locations are passed as a pipe-separated list of `latitude,longitude` pairs in the `locations`
query parameter (parsed by `LocationsParser`, see
`src/main/kotlin/com/raynigon/raylevation/base/service/LocationsParser.kt` for the exact grammar):

```shell
curl "http://localhost:8080/api/v1/lookup?locations=45.9763,7.6586|46.5197,6.6323"
```

```json
{
  "errors": false,
  "results": [
    { "latitude": 45.9763, "longitude": 7.6586, "elevation": 4473.0, "error": null },
    { "latitude": 46.5197, "longitude": 6.6323, "elevation": 375.0, "error": null }
  ]
}
```

## `POST /api/v1/lookup`

Locations are passed as a JSON body (`Content-Type: application/json`):

```shell
curl -X POST "http://localhost:8080/api/v1/lookup" \
  -H "Content-Type: application/json" \
  -d '{"locations": [{"latitude": 45.9763, "longitude": 7.6586}]}'
```

The request and response DTOs:

<!--codeinclude-->
[ElevationRequest.kt](../src/main/kotlin/com/raynigon/raylevation/base/dto/ElevationRequest.kt)
[ElevationResponse.kt](../src/main/kotlin/com/raynigon/raylevation/base/dto/ElevationResponse.kt)
<!--/codeinclude-->

`elevation` is always present and is `0` when `error` is non-null — check `error`/`errors`
rather than treating `0` elevation as a signal by itself.

## Errors

When a point has no matching elevation tile (outside of the configured SRTM coverage), the
corresponding result entry gets an `error` message and the top-level `errors` flag is set to
`true` — the HTTP status is still `200 OK`, since other points in the same request may have
succeeded.

Request-level failures (malformed JSON, wrong field types, an unreadable body) return a non-2xx
status with an `ApiError` body instead:

<!--codeinclude-->
[ApiError.kt](../src/main/kotlin/com/raynigon/raylevation/infrastructure/model/ApiError.kt)
<!--/codeinclude-->

```shell
curl -X POST "http://localhost:8080/api/v1/lookup" \
  -H "Content-Type: application/json" \
  -d '{not valid json'
```

```json
{
  "status": "400 BAD_REQUEST",
  "message": "InvalidBody",
  "errors": ["Given request body is not valid"]
}
```

The exact 400 cases (invalid body, wrong-typed `locations`, unknown property, malformed JSON) are
pinned down as executable contracts in
`src/contractTest/resources/contracts/v1/lookup/*.yaml` — check there for the authoritative
request/response pairs the API is tested against.

## Health and metrics

* `GET /api/v1/health` and `GET /actuator/healthcheck` — static `{"status": "UP"}` liveness/readiness
  probe (`HealthCheckController`), used by the Kubernetes manifests in [Deployment](deployment.md).
* `GET /actuator/health` — the standard Spring Boot Actuator health endpoint.
* `GET /actuator/prometheus` — Prometheus-formatted metrics, including database lookup timers and
  cache gauges (see [Architecture](architecture.md)).
