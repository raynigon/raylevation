FROM osgeo/gdal:ubuntu-full-3.4.3

RUN apt-get update && apt-get install -y \
    unrar \
    && rm -rf /var/lib/apt/lists/*
