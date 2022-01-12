#!/bin/sh

set -euo pipefail

# remove a maybe earlier build layers
rm -f jre-17-custom-runtime.zip

docker build --platform=linux/amd64 --progress=plain -t lambda-jre-17-custom-runtime -f Dockerfile-custom-runtime .
# extract the runtime.zip from the Docker container and store it locally
docker run --rm --entrypoint cat lambda-jre-17-custom-runtime jre-17-custom-runtime.zip > jre-17-custom-runtime.zip
