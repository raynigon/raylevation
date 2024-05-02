FROM ghcr.io/osgeo/gdal:ubuntu-full-3.8.5

RUN apt-get update && \
    apt-get install -y --no-install-recommends unrar && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -ms /bin/bash -u 1000 service

USER service
WORKDIR /app
