# Configuration

Raylevation is configured through standard Spring Boot mechanisms: `application.yml`, or any
override Spring Boot supports (another `application.yml`, `-D` system properties, or environment
variables). The defaults shipped in the jar:

<!--codeinclude-->
[application.yml](../src/main/resources/application.yml)
<!--/codeinclude-->

## Environment variables

Spring Boot's [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
maps any property to an upper-snake-case environment variable, e.g. `app.raylevation.db.path`
becomes `APP_RAYLEVATION_DB_PATH`. This is how the Kubernetes manifests in
[Deployment](deployment.md) configure the container without a custom `application.yml`.

## `app.raylevation.db.*`

Bound to `DatabaseConfig`:

<!--codeinclude-->
[DatabaseConfig.kt](../src/main/kotlin/com/raynigon/raylevation/db/config/DatabaseConfig.kt)
<!--/codeinclude-->

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `app.raylevation.db.path` | `APP_RAYLEVATION_DB_PATH` | `workspace/data/` | Directory holding the database state (`index.json`), per-tile lock file, and the `tiles/` GeoTIFF files. |
| `app.raylevation.db.cache-tile-count` | `APP_RAYLEVATION_DB_CACHE_TILE_COUNT` | `10` | Number of most-recently-looked-up tiles kept loaded in memory. See [Architecture](architecture.md#tile-cache) for the eviction strategy. |

## `app.raylevation.srtm.*`

Bound to `SRTMConfig`:

<!--codeinclude-->
[SRTMConfig.kt](../src/main/kotlin/com/raynigon/raylevation/srtm/model/SRTMConfig.kt)
<!--/codeinclude-->

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `app.raylevation.srtm.workspace` | `APP_RAYLEVATION_SRTM_WORKSPACE` | `workspace/srtm/` | Scratch directory for downloaded archives, extracted GeoTIFFs and ETag files. |
| `app.raylevation.srtm.url` | `APP_RAYLEVATION_SRTM_URL` | `https://srtm.csi.cgiar.org/wp-content/uploads/files/250m/{{name}}_TIF.rar` | Download URL template; `{{name}}` is replaced with the origin tile name. |
| `app.raylevation.srtm.tiles` | — (list, see below) | 3 default tiles | The origin tiles to download and split; see `OriginTileConfig` in `SRTMConfig.kt`. |
| `app.raylevation.srtm.save-disk-space` | `APP_RAYLEVATION_SRTM_SAVE_DISK_SPACE` | `false` | When `true`, deletes the downloaded archive, ETag file and extracted GeoTIFF for each origin tile immediately after it's split and loaded into the database — trading disk space for a full re-download on the next `update-srtm` run. Used in the Kubernetes `initContainer` setup, where the download workspace is an ephemeral `emptyDir`. |

`tiles` is a list of objects; each has `name` (the SRTM archive name, e.g. `SRTM_NE_250m`),
`splitX` and `splitY` (how many sub-tiles to split the downloaded origin tile into along each
axis — see [Architecture](architecture.md#srtm-ingestion-pipeline)). The list isn't overridable
item-by-item via environment variables; provide a replacement `application.yml` (e.g. mounted
into the container) if you need different tiles than the three CGIAR 250m defaults.

## CLI command selection

`RAYLEVATION_CLI_CMD` is read directly from the environment (not through Spring's configuration
binding) as a fallback when no command is given on the command line — see
[CLI Reference](cli.md).
