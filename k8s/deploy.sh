#!/usr/bin/env bash
#
# Build, push and deploy RDAS to Kubernetes.
#
# Usage:
#   ./k8s/deploy.sh [IMAGE_TAG] [REGISTRY]
#
# Examples:
#   ./k8s/deploy.sh                      # uses defaults, local image (kind/minikube)
#   ./k8s/deploy.sh 1.0.0 ghcr.io/myorg  # builds & pushes ghcr.io/myorg/rdas:1.0.0
#
set -euo pipefail

TAG="${1:-1.0.0}"
REGISTRY="${2:-}"            # empty => local image, no push
NAMESPACE="rdas"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [[ -n "$REGISTRY" ]]; then
  IMAGE="${REGISTRY}/rdas:${TAG}"
else
  IMAGE="rdas:${TAG}"
fi

echo ">> Building image: ${IMAGE}"
docker build -t "${IMAGE}" "${PROJECT_DIR}"

if [[ -n "$REGISTRY" ]]; then
  echo ">> Pushing image: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo ">> Applying manifests (namespace, config, workload, networking, autoscaling)"
kubectl apply -f "${SCRIPT_DIR}/namespace.yaml"
kubectl apply -f "${SCRIPT_DIR}/configmap.yaml"
kubectl apply -f "${SCRIPT_DIR}/service.yaml"

# Inject the resolved image into the Deployment without editing the YAML on disk.
kubectl apply -f "${SCRIPT_DIR}/deployment.yaml"
echo ">> Setting image to ${IMAGE}"
kubectl -n "${NAMESPACE}" set image deployment/rdas rdas="${IMAGE}"

kubectl apply -f "${SCRIPT_DIR}/hpa.yaml"
kubectl apply -f "${SCRIPT_DIR}/ingress.yaml"

echo ">> Waiting for rollout to complete..."
kubectl -n "${NAMESPACE}" rollout status deployment/rdas --timeout=180s

echo ">> Done. Pods:"
kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=rdas -o wide
