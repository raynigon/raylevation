FROM eclipse-temurin:21-jre-noble

# unrar is required to unpack the downloaded SRTM archives (update-srtm).
# Ubuntu 24.04 images ship a default "ubuntu" user with UID 1000, which is replaced by the "service" user.
# Packages are not version pinned, the base image is pinned and only receives security updates for them
# hadolint ignore=DL3008
RUN apt-get update && \
    apt-get install -y --no-install-recommends unrar && \
    rm -rf /var/lib/apt/lists/* && \
    (userdel -r ubuntu 2>/dev/null || true) && \
    useradd -ms /bin/bash -u 1000 service

USER service
WORKDIR /app
