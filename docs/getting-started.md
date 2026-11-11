# Getting Started

Raylevation has two parts to get running: a one-time **data setup** step that downloads and
prepares the [SRTM](https://en.wikipedia.org/wiki/Shuttle_Radar_Topography_Mission) elevation
tiles, and the **server** itself, which serves lookups from that data. Both are the same
application, started with a different command — see [CLI Reference](cli.md) for how that
dispatch works.

## Requirements

* A machine with enough disk space for the SRTM tiles you configure (the three default tiles in
  `application.yml` cover large parts of the globe at 250m resolution).
* [`unrar`](https://www.rarlab.com/rar_add.htm) on `PATH` if you run the server outside of the
  provided Docker image — the SRTM archives are RAR files. The Docker image already includes it
  (see the `Dockerfile`).

## Run with Docker

```shell
# 1. Download and prepare the configured SRTM tiles into ./data
docker run --rm -v "$(pwd)/data:/data/" \
  -e RAYLEVATION_CLI_CMD=update-srtm \
  -e APP_RAYLEVATION_DB_PATH=/data/ \
  ghcr.io/raynigon/raylevation:latest

# 2. Start the server against that data
docker run --rm -p 8080:8080 -v "$(pwd)/data:/data/" \
  -e APP_RAYLEVATION_DB_PATH=/data/ \
  ghcr.io/raynigon/raylevation:latest
```

Replace `latest` with a released version tag (e.g. `v0.0.4`) for anything beyond local
experimentation. See [Deployment](deployment.md) for running this in Kubernetes, where the setup
step runs automatically as an `initContainer`.

## Run from source

```shell
# 1. Download and prepare the configured SRTM tiles
./gradlew bootRun --args="update-srtm"

# 2. Start the server
./gradlew bootRun
```

Data is written to `workspace/data/` and `workspace/srtm/` by default (see
[Configuration](configuration.md)).

## Try it out

Once the server is running, request the elevation of a point (here, roughly the summit of the
Matterhorn):

```shell
curl "http://localhost:8080/api/v1/lookup?locations=45.9763,7.6586"
```

```json
{
  "errors": false,
  "results": [
    { "latitude": 45.9763, "longitude": 7.6586, "elevation": 4473.0, "error": null }
  ]
}
```

See the [API Reference](api.md) for the full request/response format, including the `POST`
variant and error responses.

## Next steps

* [Configuration](configuration.md) — all `application.yml` properties and their environment
  variable overrides.
* [CLI Reference](cli.md) — the `update-srtm` command and the dual server/CLI run mode.
* [Architecture](architecture.md) — how the SRTM ingestion pipeline and the elevation database
  fit together.
* [Deployment](deployment.md) — running Raylevation in Kubernetes.
