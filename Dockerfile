FROM osgeo/gdal:ubuntu-full-3.6.4

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    unrar=1:5.6.6-2build1 \
    && rm -rf /var/lib/apt/lists/* && \
    useradd -ms /bin/bash -u 1000 service

USER service
WORKDIR /app
