# Deployment

## Docker image

The published image (`ghcr.io/raynigon/raylevation`) is built in two layers:

* A base image (`Dockerfile` at the repo root, published separately as
  `ghcr.io/raynigon/raylevation-base:21`) — `eclipse-temurin:21-jre-noble` plus `unrar` (required
  by the `update-srtm` command) and a non-root `service` user (uid `1000`).
* The application layer, built on top of that base image with
  [Jib](https://github.com/GoogleContainerTools/jib) (`jib {}` block in `build.gradle.kts`) —
  no Dockerfile needed for the app itself. The container runs as uid `1000`, exposes port
  `8080`, and sets `SPRING_MAIN_BANNER-MODE=off`.

```shell
docker run -p 8080:8080 -v "$(pwd)/data:/data/" \
  -e APP_RAYLEVATION_DB_PATH=/data/ \
  ghcr.io/raynigon/raylevation:latest
```

See [Getting Started](getting-started.md) for the full local workflow, including the
one-time `update-srtm` step.

## Kubernetes

Two ready-to-use manifests live under `deployment/k8s/`:

| Manifest | Contains |
|---|---|
| `install-minimum.yaml` | `Deployment` (2 replicas, pod anti-affinity), `PersistentVolumeClaim` (`ReadWriteMany`, 25Gi), `Service`. |
| `install-full.yaml` | Everything in `install-minimum.yaml`, plus a Prometheus Operator `ServiceMonitor` scraping `/actuator/prometheus`. Use this one if a Prometheus Operator is already installed in the cluster; use `install-minimum.yaml` otherwise, to avoid a `ServiceMonitor` with no matching CRD. |

### The `initContainer` setup pattern

Both manifests run the SRTM setup step as an `initContainer` before the `raylevation-server`
container starts, using the same image:

```yaml
initContainers:
  - name: raylevation-download
    image: ghcr.io/raynigon/raylevation:v0.0.3
    env:
      - name: RAYLEVATION_CLI_CMD
        value: "update-srtm"
      - name: APP_RAYLEVATION_DB_PATH
        value: "/data/"
      - name: APP_RAYLEVATION_SRTM_WORKSPACE
        value: "/srtm/"
      - name: APP_RAYLEVATION_SRTM_SAVE_DISK_SPACE
        value: "true"
```

`RAYLEVATION_CLI_CMD=update-srtm` is exactly the environment-variable command selection described
in [CLI Reference](cli.md): the same image, with no command-line change, runs the setup step
instead of the server. `APP_RAYLEVATION_SRTM_SAVE_DISK_SPACE=true` is paired with the SRTM
workspace being an ephemeral `emptyDir` (`raylevation-srtm` volume) — nothing there needs to
survive past this run anyway, and deleting downloaded archives/GeoTIFFs as each origin tile is
processed keeps peak disk use down (see [Architecture](architecture.md#srtm-ingestion-pipeline)).
The database itself (`/data/`) is a `PersistentVolumeClaim`, shared by the init container and
the main container, so the prepared tiles persist across pod restarts and the server doesn't
re-download anything on its own.

Update the `image:` tag in both manifests to the version you want to deploy — they're not
templated.

### Probes

The `readinessProbe`/`livenessProbe` both hit `/actuator/healthcheck`, the static health
endpoint described in [API Reference](api.md#health-and-metrics). The generous
`initialDelaySeconds` (30s readiness, 120s liveness) account for a first-time `initContainer` run
needing to download and process SRTM data before the main container even starts.

### Resource sizing

The `initContainer` is given a higher CPU/memory limit (1 CPU / 4Gi) than the server container
(0.5 CPU / 2Gi) — tile splitting and GeoTIFF processing during `update-srtm` is more resource
intensive than serving lookups from an already-built database.
