FROM ghcr.io/osgeo/gdal:ubuntu-full-3.11.0

# The Apache Arrow apt repository bundled in the GDAL image has an expired/unknown signing key,
# which breaks apt-get update. unrar is installed from the Ubuntu repositories only.
# Ubuntu 24.04 images ship a default "ubuntu" user with UID 1000, which is replaced by the "service" user.
# The GDAL image ships Java 17, the application requires Java 21.
# Packages are not version pinned, the base image is pinned and only receives security updates for them
# hadolint ignore=DL3008
RUN rm -f /etc/apt/sources.list.d/*arrow* && \
    apt-get update && \
    apt-get install -y --no-install-recommends unrar openjdk-21-jre-headless && \
    rm -rf /var/lib/apt/lists/* && \
    (userdel -r ubuntu 2>/dev/null || true) && \
    useradd -ms /bin/bash -u 1000 service

USER service
WORKDIR /app
