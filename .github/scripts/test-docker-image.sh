#!/bin/bash
mkdir -p data/tiles
sudo chmod -R 777 data/
sudo chown -R 1000 data/
echo "Available Images:"
docker images | grep "raylevation"
echo "Start Container:"
docker run --name raylevation-docker-test -d --user 1000 -p 8080:8080 -v $(pwd)/data/:/app/workspace/data/ raynigon/raylevation:latest
sleep 10
docker logs raylevation-docker-test
echo "Start connection attempts..."
attempt=0
while [ $attempt -le 15 ]; do
  attempt=$((attempt + 1))
  echo "Waiting for server to be up (attempt: $attempt)..."
  curl -s --retry 3 --retry-connrefused http://localhost:8080/actuator/healthcheck
  result=$?
  if [ $result == 0 ]; then
    echo ""
    echo "Application is up!"
    break
  fi
  sleep 2
done
echo "Stop Container..."
docker stop raylevation-docker-test
echo "Remove Container..."
docker rm raylevation-docker-test
