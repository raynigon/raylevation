FROM ghcr.io/osgeo/gdal:ubuntu-full-3.11.0

# The Apache Arrow apt repository bundled in the GDAL image has an expired/unknown signing key,
# which breaks apt-get update. unrar is installed from the Ubuntu repositories only.
RUN rm -f /etc/apt/sources.list.d/*arrow* && \
    apt-get update && \
    apt-get install -y --no-install-recommends unrar && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -ms /bin/bash -u 1000 service

USER service
WORKDIR /app
