#!/bin/sh

set -euo pipefail

# remove a maybe earlier build layers
rm -f jre-17-layer.zip

docker build --platform=linux/amd64 --progress=plain -t lambda-jre-17-layer -f Dockerfile-layer .
# extract the runtime.zip from the Docker container and store it locally
docker run --rm --entrypoint cat lambda-jre-17-layer jre-17-layer.zip > jre-17-layer.zip
