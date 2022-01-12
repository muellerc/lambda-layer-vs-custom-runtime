#!/bin/sh

set -euo pipefail

cd software/example-function
mvn package

cd ../../infrastructure
cdk synth
cdk deploy --outputs-file target/outputs.json


cd ..
curl -i $(cat infrastructure/target/outputs.json | jq -r '.LambdaLayerVsCustomRuntime.apiendpoint')/layer-custom-runtime
curl -i $(cat infrastructure/target/outputs.json | jq -r '.LambdaLayerVsCustomRuntime.apiendpoint')/layer-provided-runtime
curl -i $(cat infrastructure/target/outputs.json | jq -r '.LambdaLayerVsCustomRuntime.apiendpoint')/custom-runtime

