# Architecture

Raylevation has two halves that share one on-disk database: the **SRTM ingestion pipeline**
(run via the `update-srtm` CLI command, see [CLI Reference](cli.md)) that turns raw SRTM archives
into database tiles, and the **lookup service** that serves elevation queries from them over
HTTP (see [API Reference](api.md)).

```
                          ┌─────────────────────────── update-srtm ───────────────────────────┐
                          │                                                                    │
 srtm.csi.cgiar.org  ──▶  TileDownloadService ──▶ TileUnpackService ──▶ TileSplitService ──▶  RaylevationDB (locked)
  (RAR archive)             (HTTP + ETag check)      (unrar)             (N×M sub-tiles)         .addTile() / .save()
                                                                                                        │
                                                                                                        ▼
                                                                                              workspace/data/
                                                                                                ├── index.json   (state + metadata)
                                                                                                ├── lock.json    (write lock)
                                                                                                └── tiles/*.tif  (GeoTIFF sub-tiles)
                                                                                                        │
                          ┌──────────────────────────────── server ──────────────────────────────────┤
                          │                                                                            ▼
 HTTP client  ──▶  RaylevationController ──▶ RaylevationService ──▶ RaylevationDB (read-only, R-tree index)
                     (/api/v1/lookup)         .lookup() / syncCache()      .lookupElevation()
```

## SRTM ingestion pipeline

`SRTMServiceImpl.updateRaylevationDB()` (`src/main/kotlin/com/raynigon/raylevation/srtm/service/SRTMService.kt`)
drives the pipeline, once per configured origin tile (`app.raylevation.srtm.tiles`, see
[Configuration](configuration.md)):

1. **Download** (`TileDownloadService`) — `HEAD`s the remote URL for its `ETag` first; if it
   matches both the locally stored ETag *and* the previously used `splitX`/`splitY`, the tile is
   already up to date and the rest of the pipeline is skipped for it. Otherwise the RAR archive
   is downloaded and the new ETag persisted alongside it.
2. **Unpack** (`TileUnpackService`) — shells out to the `unrar` binary to extract the GeoTIFF
   from the archive.
3. **Split** (`TileSplitService`) — divides the single large origin GeoTIFF into `splitX ×
   splitY` smaller sub-tiles (`GeoTiffTile.subTile()`), so the database holds many small tiles
   instead of a few huge ones. Each sub-tile is added to the database (`RaylevationDB.addTile()`)
   and then deleted from the split workspace.
4. If this origin tile replaces a previous version, the old sub-tiles recorded for it are removed
   from the database first (falling back to a bounds-based lookup for legacy metadata predating
   per-origin sub-tile tracking).
5. Database metadata (per-origin ETag, split factors, resulting sub-tile names) is updated and
   `RaylevationDB.save()` persists `index.json`.
6. If `saveDiskSpace` is enabled, the downloaded archive, ETag file, and extracted GeoTIFF are
   deleted once the origin tile has been fully processed — the next `update-srtm` run then starts
   from scratch for that tile. This is the mode used in the Kubernetes `initContainer` pattern
   (see [Deployment](deployment.md)), whose download workspace is an ephemeral volume anyway.

This whole run holds a **write lock** on the database the entire time (see below) — it's meant to
be run standalone, not concurrently with another `update-srtm` run.

## The database (`RaylevationDB`)

`RaylevationDB` (`src/main/kotlin/com/raynigon/raylevation/db/RaylevationDB.kt`) is a small,
purpose-built flat-file database rather than a general-purpose one:

* **Spatial index** — tile bounds are kept in an in-memory
  [R-tree](https://github.com/davidmoten/rtree) (`com.github.davidmoten:rtree`), so
  `lookupElevation(point)` only needs a nearest-neighbor query against the tree instead of
  scanning every tile.
* **Persistence** — `index.json` holds the full list of known tiles plus a free-form metadata
  map (used by the SRTM pipeline to track per-origin ETags). Each tile's actual raster data lives
  as its own GeoTIFF file under `tiles/`.
* **GeoTIFF I/O** — tile reads/writes go through [Apache SIS](https://sis.apache.org/)
  (`org.apache.sis.storage:sis-geotiff`), not GDAL/JNI — a deliberate, relatively recent change
  to drop a native-library dependency.
* **Locking** — `RaylevationDBLock` (`src/main/kotlin/com/raynigon/raylevation/db/lock/`) is a
  file-based lock (`lock.json`) with a lock-id and a last-updated timestamp, independent of the
  database content itself. Mutating operations (`addTile`, `removeTile`, `save`) require the
  database to have been `open(locked = true)`'d first and throw `DatabaseNotLockedException`
  otherwise. A lock is considered stale and can be stolen after `LOCK_TIMEOUT` (30 minutes)
  without an update, which bounds how long a crashed `update-srtm` run can block future ones.
  `RaylevationDBFactory.create()` opens the database read-only (and transparently bootstraps an
  empty one in locked mode first if none exists yet); `createLocked()` is used by the SRTM
  pipeline for writes.

## Tile cache

`RaylevationServiceImpl` (`src/main/kotlin/com/raynigon/raylevation/base/service/RaylevationService.kt`)
runs `database.syncCache()` on a fixed 1-second `@Scheduled` interval. `RaylevationDB.syncCache()`
keeps the `cache-tile-count` (see [Configuration](configuration.md)) most-frequently-looked-up
tiles' raster data resident in memory — a lookup-count ranking, not strict LRU — and
unloads the rest, independent of how many tiles exist on disk in total. This bounds memory use
while keeping "hot" regions fast to query even when the on-disk tile set is much larger than
memory allows.

## Serving a lookup

`RaylevationController` (see [API Reference](api.md)) parses the request into `GeoPoint`s and
delegates to `RaylevationService.lookup()`, which looks up each point against the (read-only)
`RaylevationDB` in parallel once a request exceeds `PARALLEL_LIMIT` (10,000 points). A point
outside of any loaded tile's bounds surfaces as a per-point error in the response rather than
failing the whole request — see [Errors](api.md#errors).
