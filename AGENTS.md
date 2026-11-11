# AGENTS.md

This file provides guidance to AI coding agents (including Claude Code)
when working with code in this repository.

Markdown files in this repo (including this one) should keep lines at
or under 80 characters.

## What this is

Raylevation is a self-hosted elevation lookup API (a free alternative
to the Google Elevation API), built with Kotlin + Spring Boot. It
downloads SRTM 250m GeoTIFF tiles, stores them in a custom flat-file +
R-tree spatial database, and serves elevation lookups over REST. See
`docs/architecture.md` for the full data-flow diagram and
`docs/index.md` for the rest of the docs site nav.

## Commands

Single Gradle module (Kotlin DSL), JDK 21, Kotlin. Use the wrapper
(`./gradlew`), no local Gradle install needed. `unrar` must be on
`PATH` locally (tests/CLI use it to unpack SRTM archives); the Docker
image already includes it.

```shell
./gradlew installLocalGitHooks   # once, after cloning: auto-formats on commit
./gradlew bootRun                # start the server
./gradlew bootRun --args="update-srtm"   # run the SRTM setup CLI command
./gradlew test                   # unit + contract tests (Spock + generated)
./gradlew test --tests "com.raynigon.raylevation.db.RaylevationDBSpec"
./gradlew smokeTest              # hits real external hosts, not part of check
./gradlew check                  # test + ktlintCheck + checkstyleMain + pmdMain
./gradlew ktlintFormat           # auto-fix Kotlin formatting
./gradlew jibDockerBuild         # build the app Docker image locally
./gradlew dokkaGenerateHtml      # generate the KDoc API reference
```

Gradle's build cache is on by default; add `--no-build-cache` to
disable it for a run.

Contract tests live as YAML under
`src/contractTest/resources/contracts/v1/lookup/*.yaml` (Spring Cloud
Contract) — edit those to change API-contract coverage, not the
generated test classes. They're folded into the `test` task's
classpath automatically.

To preview the MkDocs documentation site locally:

```shell
pip install -r requirements.txt
mkdocs serve
```

## Architecture

### Package layout

- `base/` — the API surface: `controller/` (REST), `dto/` (request
  /response types), `service/` (`RaylevationService` orchestrates
  lookups, `LocationsParser` parses the query-string grammar,
  `CliRunner` picks server-vs-CLI mode, `RaylevationDBFactory` opens
  the database).
- `db/` — the elevation database engine itself: spatial index, GeoTIFF
  I/O, file locking, persisted state model.
- `srtm/` — the ingestion pipeline that turns raw SRTM archives into
  database tiles (download, unpack, split).
- `infrastructure/` — cross-cutting concerns: config beans, the
  generic `ApiError`/`GeoPoint` models, the global exception-handling
  controller advisor, external-process logging.

Throughout, an interface plus a `*Impl` class pair is the norm (e.g.
`CliRunner`/`CliRunnerImpl`, `IRaylevationDB`/`RaylevationDB`) to keep
things mockable through Spring DI — follow that pattern for new
services rather than a concrete class alone.

### Dual run mode (`base/service/CliRunner.kt`)

The same application either serves HTTP (default) or runs a one-shot
CLI command and exits, decided by `CliRunnerImpl.run()`:

- A non-option command-line argument (not `--name=value`) is checked
  first, then the `RAYLEVATION_CLI_CMD` env var as a fallback.
- Only one command currently exists: `update-srtm`.
- Unknown command → usage message logged, exit `127`. Success → exit
  `0`. Exception → exit `1`.
- Commands are declared in a single `List<CliCommandDefinition>` so
  the usage text can't drift from the dispatch logic — add new CLI
  commands there.

### The elevation database (`db/RaylevationDB.kt`)

A purpose-built flat-file database, not a general-purpose one:

- Tile metadata lives in `index.json` under
  `app.raylevation.db.path`; each tile's raster is its own GeoTIFF
  file under `tiles/`.
- An in-memory R-tree (`com.github.davidmoten:rtree`) indexes tile
  bounds for nearest-tile lookups.
- GeoTIFF reads/writes go through Apache SIS, not GDAL/JNI (migrated
  away from GDAL).
- Writes (`addTile`/`removeTile`/`save`) require a file-based lock
  (`lock.json`, see `db/lock/RaylevationDBLock.kt`) acquired via
  `open(locked = true)`; they throw `DatabaseNotLockedException`
  otherwise. `RaylevationDBFactory.create()` opens read-only
  (bootstrapping an empty DB first if none exists);
  `createLocked()` is for writers (the SRTM pipeline).
- `RaylevationServiceImpl` runs a 1s `@Scheduled` `syncCache()` that
  keeps the `cache-tile-count` most-frequently-looked-up tiles
  resident in memory; this is lookup-count based, not strict LRU.

### SRTM ingestion pipeline (`srtm/service/SRTMService.kt`)

`SRTMServiceImpl.updateRaylevationDB()` drives, per configured origin
tile (`app.raylevation.srtm.tiles`): `TileDownloadService` (HTTP +
ETag check, skips already-up-to-date tiles) → `TileUnpackService`
(shells out to `unrar`) → `TileSplitService` (splits one origin
GeoTIFF into `splitX × splitY` sub-tiles) → `RaylevationDB.addTile()`
for each sub-tile. Per-origin ETag/split state is tracked in the
database's metadata map so re-runs are idempotent. The whole run
holds a write lock on the database.

### Serving a lookup

`RaylevationController` (`/api/v1/lookup`, `GET`/`POST`) → parses
locations → `RaylevationService.lookup()` (parallelizes above 10,000
points) → `RaylevationDB.lookupElevation()` (read-only). A point
outside any known tile surfaces as a per-point error in the response
rather than failing the whole request.

### Error handling

Two `@ControllerAdvice` layers, both formatting errors as `ApiError`:
`RaylevationControllerAdvisor` (scoped to `RaylevationController`,
`HIGHEST_PRECEDENCE`) and `FallbackControllerAdvisor` (global,
`LOWEST_PRECEDENCE`, wraps generic Spring MVC exceptions). When an
`@ExceptionHandler` returns a `ResponseEntity` directly, that entity's
own status wins over any `@ResponseStatus` annotation on the method —
don't rely on both being consistent; prefer just the `ResponseEntity`.

### Configuration

`@ConfigurationProperties` classes (`DatabaseConfig`, `SRTMConfig`)
bind `app.raylevation.db.*` / `app.raylevation.srtm.*` from
`src/main/resources/application.yml`; every property is overridable
via Spring's relaxed-binding env vars (e.g.
`APP_RAYLEVATION_DB_PATH`). See `docs/configuration.md` for the full
reference table.

### Test suites

Three separate Gradle-driven suites: `src/test/groovy` (Spock
unit/integration specs, part of `test`), `src/contractTest` (YAML
contracts generated into JUnit 5 tests, folded into `test`), and
`src/smokeTest/groovy` (hits real external hosts, excluded from
`test`/`check`, run only via the `smokeTest` task).

### Docs and releases

The MkDocs site (`docs/`, nav in `mkdocs.yml`) uses the
`codeinclude` plugin in several pages to embed real source directly,
so those sections track the code instead of drifting. A separate
Dokka-generated KDoc site is built in CI. Releases are driven by the
`net.researchgate.release` plugin from a `release` branch; the
release build auto-rewrites `mkdocs.yml`'s `latest_version` and the
image tag/version labels in `deployment/k8s/*.yaml`
(`gradle/scripts/external.gradle`) — don't hand-edit those version
strings outside of a release.
