#!/bin/bash
mkdir -p build/gdal/output
cd build/gdal/
docker run --rm -v $(pwd)/output:/output osgeo/gdal:ubuntu-full-3.4.3 cp -R /usr/ /output/usr/

apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        libsqlite3-0 libtiff5 libcurl4 \
        wget curl unzip ca-certificates \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        libopenjp2-7 libcairo2 python3-numpy \
        libpng16-16 libjpeg-turbo8 libgif7 liblzma5 libgeos3.10.2 libgeos-c1v5 \
        libxml2 libexpat1 \
        libxerces-c3.2 libnetcdf-c++4 netcdf-bin libpoppler118 libspatialite7 librasterlite2-1 gpsbabel \
        libhdf4-0-alt libhdf5-103 libhdf5-cpp-103 poppler-utils libfreexl1 unixodbc mdbtools libwebp7 \
        liblcms2-2 libpcre3 libcrypto++8 libfyba0 \
        libkmlbase1 libkmlconvenience1 libkmldom1 libkmlengine1 libkmlregionator1 libkmlxsd1 \
        libmysqlclient21 libogdi4.1 libcfitsio9 openjdk-"$JAVA_VERSION"-jre \
        libzstd1 bash bash-completion libpq5 libssl3 \
        libarmadillo10 libpython3.10 libopenexr25 libheif1 \
        libdeflate0 libblosc1 liblz4-1 \
        libbrotli1 \
        python-is-python3 \
    && ln -s /usr/lib/ogdi/libvrf.so /usr/lib \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y -V ca-certificates lsb-release wget \
    && wget https://apache.jfrog.io/artifactory/arrow/$(lsb_release --id --short | tr 'A-Z' 'a-z')/apache-arrow-apt-source-latest-$(lsb_release --codename --short).deb \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y -V ./apache-arrow-apt-source-latest-$(lsb_release --codename --short).deb \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y -V libarrow800 libparquet800 libarrow-dataset800 \
    && rm -rf /var/lib/apt/lists/*

yes | sudo cp -rf /output/usr/ /usr/