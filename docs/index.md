# Raylevation

![](assets/logo.svg)

## About

Raylevation is a free, open-source and self hosted alternative to the Google Elevation API and similar offerings.
It was inspired by the [Open-Elevation API](https://github.com/Jorl17/open-elevation).
This service came out of the need to have a hosted, easy to use and easy to setup elevation API. 
While there are some alternatives out there, none of them work out of the box, and seem to point to dead datasets.
Raylevation is easy to setup, has its own docker image and provides a setup functionality for you 
to automatically download the srtm dataset from https://srtm.csi.cgiar.org/srtmdata/.

## Where to go next

* [Getting Started](getting-started.md) — run the server locally or with Docker.
* [API Reference](api.md) — the `/api/v1/lookup` REST endpoint.
* [Configuration](configuration.md) — every `application.yml` property and its environment variable override.
* [CLI Reference](cli.md) — the `update-srtm` command and the server/CLI dual run mode.
* [Architecture](architecture.md) — how the SRTM ingestion pipeline and the elevation database work.
* [Deployment](deployment.md) — running Raylevation in Kubernetes.
* [Contribution](contribution.md) — set up a development environment and run the test suites.
* [Code Documentation](https://raylevation.raynigon.com/dokka/) — generated KDoc API reference for the Kotlin sources.
