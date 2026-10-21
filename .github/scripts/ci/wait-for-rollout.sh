#!/usr/bin/env bash
# Wait until a deployment is available. A startup failure, including the issuer2
# attestation crash, leaves the deployment short of ready replicas and this exits non-zero.
set -euo pipefail

namespace="${1:?namespace}"
deployment="${2:?deployment}"
timeout="${3:-180s}"

kubectl rollout status "deployment/${deployment}" -n "$namespace" --timeout="$timeout"
