#!/bin/bash
# =============================================================================
# Build and Push Docker Images Script
# Builds all microservices and frontend, then pushes to GCR
# =============================================================================

set -euo pipefail

# Colors for output
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
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo 'latest')}"
REGISTRY="gcr.io/${PROJECT_ID}"

# Services to build
BACKEND_SERVICES=(
    "auth-service"
    "user-service"
    "post-service"
    "feed-service"
    "comment-service"
    "like-service"
)

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    if [[ -z "$PROJECT_ID" ]]; then
        log_error "PROJECT_ID not set. Run 'gcloud config set project <PROJECT_ID>' or export PROJECT_ID"
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Build backend services
build_backend() {
    log_info "Building backend services with Maven..."
    
    cd "${PROJECT_ROOT}/backend"
    
    mvn clean package -DskipTests -T 1C
    
    log_success "Backend build complete"
}

# Build and tag Docker images
build_images() {
    log_info "Building Docker images..."
    
    # Build backend services
    for service in "${BACKEND_SERVICES[@]}"; do
        log_info "Building $service..."
        
        docker build \
            -t "${REGISTRY}/${service}:${IMAGE_TAG}" \
            -t "${REGISTRY}/${service}:latest" \
            -f "${PROJECT_ROOT}/backend/${service}/Dockerfile" \
            "${PROJECT_ROOT}/backend/${service}"
        
        log_success "Built ${service}"
    done
    
    # Build frontend
    log_info "Building frontend..."
    
    cd "${PROJECT_ROOT}/frontend"
    npm ci --silent
    npm run build
    
    docker build \
        -t "${REGISTRY}/frontend:${IMAGE_TAG}" \
        -t "${REGISTRY}/frontend:latest" \
        -f "${PROJECT_ROOT}/frontend/Dockerfile" \
        "${PROJECT_ROOT}/frontend"
    
    log_success "Built frontend"
}

# Push images to registry
push_images() {
    log_info "Pushing images to ${REGISTRY}..."
    
    # Configure Docker for GCR
    gcloud auth configure-docker gcr.io --quiet
    
    # Push backend services
    for service in "${BACKEND_SERVICES[@]}"; do
        log_info "Pushing $service..."
        docker push "${REGISTRY}/${service}:${IMAGE_TAG}"
        docker push "${REGISTRY}/${service}:latest"
    done
    
    # Push frontend
    log_info "Pushing frontend..."
    docker push "${REGISTRY}/frontend:${IMAGE_TAG}"
    docker push "${REGISTRY}/frontend:latest"
    
    log_success "All images pushed to registry"
}

# List built images
list_images() {
    echo ""
    log_info "Built images:"
    echo "=============================================="
    for service in "${BACKEND_SERVICES[@]}" "frontend"; do
        echo "  ${REGISTRY}/${service}:${IMAGE_TAG}"
        echo "  ${REGISTRY}/${service}:latest"
    done
    echo "=============================================="
}

# Usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --build-only    Only build images, don't push"
    echo "  --push-only     Only push images, don't build"
    echo "  --service NAME  Build only specific service"
    echo "  --tag TAG       Use specific tag (default: git short hash)"
    echo "  -h, --help      Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  PROJECT_ID      GCP project ID"
    echo "  REGION          GCP region (default: us-central1)"
    echo "  IMAGE_TAG       Image tag (default: git short hash)"
}

# Main
main() {
    local build_only=false
    local push_only=false
    local specific_service=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --build-only)
                build_only=true
                shift
                ;;
            --push-only)
                push_only=true
                shift
                ;;
            --service)
                specific_service="$2"
                shift 2
                ;;
            --tag)
                IMAGE_TAG="$2"
                shift 2
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
    echo "    Instagram Clone - Build & Push Script"
    echo "=============================================="
    echo ""
    echo "Project ID: ${PROJECT_ID}"
    echo "Image Tag:  ${IMAGE_TAG}"
    echo "Registry:   ${REGISTRY}"
    echo ""
    
    check_prerequisites
    
    if [[ "$push_only" == false ]]; then
        build_backend
        build_images
    fi
    
    if [[ "$build_only" == false ]]; then
        push_images
    fi
    
    list_images
    
    log_success "Build and push complete!"
}

main "$@"
