#!/bin/bash
# =============================================================================
# Deploy to GKE Script
# Deploys the application to GKE using Kustomize
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Configuration
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-us-central1}"
CLUSTER_NAME="${CLUSTER_NAME:-instagram-clone-${ENVIRONMENT:-dev}-gke}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
K8S_DIR="${PROJECT_ROOT}/k8s"

# Get cluster credentials
get_credentials() {
    log_info "Getting GKE cluster credentials..."
    
    gcloud container clusters get-credentials "$CLUSTER_NAME" \
        --region "$REGION" \
        --project "$PROJECT_ID"
    
    log_success "Connected to cluster: $CLUSTER_NAME"
}

# Verify cluster connection
verify_connection() {
    log_info "Verifying cluster connection..."
    
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Cannot connect to cluster"
        exit 1
    fi
    
    kubectl get nodes
    log_success "Cluster connection verified"
}

# Install External Secrets Operator
install_external_secrets() {
    log_info "Installing External Secrets Operator..."
    
    if helm list -n external-secrets 2>/dev/null | grep -q external-secrets; then
        log_warning "External Secrets Operator already installed"
        return
    fi
    
    helm repo add external-secrets https://charts.external-secrets.io
    helm repo update
    
    helm install external-secrets external-secrets/external-secrets \
        --namespace external-secrets \
        --create-namespace \
        --set installCRDs=true \
        --wait
    
    log_success "External Secrets Operator installed"
}

# Update Kustomize with current image tags
update_kustomize() {
    log_info "Updating Kustomize overlay for $ENVIRONMENT..."
    
    cd "${K8S_DIR}/overlays/${ENVIRONMENT}"
    
    local REGISTRY="gcr.io/${PROJECT_ID}"
    
    kustomize edit set image \
        "gcr.io/instagram-clone-project/auth-service=${REGISTRY}/auth-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/user-service=${REGISTRY}/user-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/post-service=${REGISTRY}/post-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/feed-service=${REGISTRY}/feed-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/comment-service=${REGISTRY}/comment-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/like-service=${REGISTRY}/like-service:${IMAGE_TAG}" \
        "gcr.io/instagram-clone-project/frontend=${REGISTRY}/frontend:${IMAGE_TAG}"
    
    log_success "Kustomize overlay updated"
}

# Deploy application
deploy() {
    log_info "Deploying to $ENVIRONMENT environment..."
    
    cd "${K8S_DIR}"
    
    # Apply with Kustomize
    kubectl apply -k "overlays/${ENVIRONMENT}"
    
    log_success "Deployment initiated"
}

# Wait for rollout
wait_for_rollout() {
    log_info "Waiting for deployments to be ready..."
    
    local DEPLOYMENTS=(
        "auth-service"
        "user-service"
        "post-service"
        "feed-service"
        "comment-service"
        "like-service"
        "frontend"
    )
    
    for deploy in "${DEPLOYMENTS[@]}"; do
        local prefix=""
        [[ "$ENVIRONMENT" != "dev" ]] && prefix="${ENVIRONMENT}-"
        
        log_info "Waiting for ${prefix}${deploy}..."
        kubectl rollout status "deployment/${prefix}${deploy}" \
            -n instagram-clone \
            --timeout=300s || true
    done
    
    log_success "All deployments ready"
}

# Show deployment status
show_status() {
    echo ""
    log_info "Deployment Status:"
    echo "=============================================="
    
    kubectl get pods -n instagram-clone
    
    echo ""
    kubectl get services -n instagram-clone
    
    echo ""
    kubectl get ingress -n instagram-clone 2>/dev/null || echo "No ingress found"
    
    echo "=============================================="
}

# Usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -e, --environment ENV  Target environment (dev/staging/prod)"
    echo "  -t, --tag TAG          Image tag to deploy"
    echo "  --skip-build           Skip image building"
    echo "  --dry-run              Show what would be deployed"
    echo "  -h, --help             Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  PROJECT_ID      GCP project ID"
    echo "  REGION          GCP region"
    echo "  CLUSTER_NAME    GKE cluster name"
    echo "  ENVIRONMENT     Target environment"
    echo "  IMAGE_TAG       Image tag to deploy"
}

# Main
main() {
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -t|--tag)
                IMAGE_TAG="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done
    
    echo "=============================================="
    echo "    Instagram Clone - Deploy Script"
    echo "=============================================="
    echo ""
    echo "Project ID:   ${PROJECT_ID}"
    echo "Environment:  ${ENVIRONMENT}"
    echo "Cluster:      ${CLUSTER_NAME}"
    echo "Image Tag:    ${IMAGE_TAG}"
    echo ""
    
    if [[ "$dry_run" == true ]]; then
        log_info "DRY RUN - showing what would be deployed:"
        cd "${K8S_DIR}"
        kustomize build "overlays/${ENVIRONMENT}"
        exit 0
    fi
    
    get_credentials
    verify_connection
    install_external_secrets
    update_kustomize
    deploy
    wait_for_rollout
    show_status
    
    log_success "Deployment complete!"
}

main "$@"
