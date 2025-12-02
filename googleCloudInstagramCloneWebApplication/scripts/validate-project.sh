#!/bin/bash
# =============================================================================
# Validate Project Configuration
# Validates the Instagram Clone project structure and configurations
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[✓]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[!]${NC} $1"; }
log_error() { echo -e "${RED}[✗]${NC} $1"; }

ERRORS=0
WARNINGS=0

# Check if file exists
check_file() {
    local file="$1"
    local desc="$2"
    if [[ -f "$file" ]]; then
        log_success "$desc exists"
    else
        log_error "$desc NOT FOUND: $file"
        ((ERRORS++))
    fi
}

# Check if directory exists
check_dir() {
    local dir="$1"
    local desc="$2"
    if [[ -d "$dir" ]]; then
        log_success "$desc exists"
    else
        log_error "$desc NOT FOUND: $dir"
        ((ERRORS++))
    fi
}

echo "=============================================="
echo "    Instagram Clone - Project Validator"
echo "=============================================="
echo ""

log_info "Project root: $PROJECT_ROOT"
echo ""

# ==============================================================================
# Frontend Validation
# ==============================================================================
log_info "Validating Frontend..."
check_file "$PROJECT_ROOT/frontend/package.json" "Frontend package.json"
check_file "$PROJECT_ROOT/frontend/tsconfig.json" "Frontend tsconfig.json"
check_file "$PROJECT_ROOT/frontend/vite.config.ts" "Frontend vite.config.ts"
check_file "$PROJECT_ROOT/frontend/Dockerfile" "Frontend Dockerfile"
check_dir "$PROJECT_ROOT/frontend/src" "Frontend src directory"
echo ""

# ==============================================================================
# Backend Services Validation
# ==============================================================================
log_info "Validating Backend Services..."
check_file "$PROJECT_ROOT/backend/pom.xml" "Backend parent pom.xml"

SERVICES=("auth-service" "user-service" "post-service" "feed-service" "comment-service" "like-service")
for service in "${SERVICES[@]}"; do
    check_dir "$PROJECT_ROOT/backend/$service" "Backend $service directory"
    check_file "$PROJECT_ROOT/backend/$service/pom.xml" "Backend $service pom.xml"
    check_file "$PROJECT_ROOT/backend/$service/Dockerfile" "Backend $service Dockerfile"
done
echo ""

# ==============================================================================
# Terraform Validation
# ==============================================================================
log_info "Validating Terraform..."
check_file "$PROJECT_ROOT/terraform/main.tf" "Terraform main.tf"
check_file "$PROJECT_ROOT/terraform/variables.tf" "Terraform variables.tf"
check_dir "$PROJECT_ROOT/terraform/modules/vpc" "Terraform VPC module"
check_dir "$PROJECT_ROOT/terraform/modules/gke" "Terraform GKE module"
check_dir "$PROJECT_ROOT/terraform/modules/cloud-sql" "Terraform Cloud SQL module"
check_dir "$PROJECT_ROOT/terraform/modules/memorystore" "Terraform Memorystore (Redis) module"
check_dir "$PROJECT_ROOT/terraform/modules/gcs" "Terraform GCS module"
check_dir "$PROJECT_ROOT/terraform/modules/iam" "Terraform IAM module"
check_dir "$PROJECT_ROOT/terraform/modules/secret-manager" "Terraform Secret Manager module"
check_file "$PROJECT_ROOT/terraform/environments/dev/terraform.tfvars" "Dev environment tfvars"
check_file "$PROJECT_ROOT/terraform/environments/staging/terraform.tfvars" "Staging environment tfvars"
check_file "$PROJECT_ROOT/terraform/environments/prod/terraform.tfvars" "Prod environment tfvars"
echo ""

# ==============================================================================
# Kubernetes Manifests Validation
# ==============================================================================
log_info "Validating Kubernetes Manifests..."
check_file "$PROJECT_ROOT/k8s/base/kustomization.yaml" "K8s base kustomization.yaml"
check_file "$PROJECT_ROOT/k8s/base/namespace.yaml" "K8s namespace.yaml"

for service in "${SERVICES[@]}"; do
    check_file "$PROJECT_ROOT/k8s/base/deployments/$service.yaml" "K8s $service deployment"
done
check_file "$PROJECT_ROOT/k8s/base/deployments/frontend.yaml" "K8s frontend deployment"

check_file "$PROJECT_ROOT/k8s/base/services/services.yaml" "K8s services configuration"

check_dir "$PROJECT_ROOT/k8s/overlays/dev" "K8s dev overlay"
check_dir "$PROJECT_ROOT/k8s/overlays/staging" "K8s staging overlay"
check_dir "$PROJECT_ROOT/k8s/overlays/prod" "K8s prod overlay"
echo ""

# ==============================================================================
# Monitoring Validation
# ==============================================================================
log_info "Validating Monitoring Configuration..."
check_file "$PROJECT_ROOT/infrastructure/helm/monitoring-values.yaml" "Helm monitoring values"

for service in "${SERVICES[@]}"; do
    svc_name=$(echo "$service" | sed 's/-service//')
    check_file "$PROJECT_ROOT/infrastructure/monitoring/servicemonitors/servicemonitor-$svc_name.yaml" "ServiceMonitor $svc_name"
done

check_file "$PROJECT_ROOT/infrastructure/monitoring/prometheus-rules/app-alerts.yaml" "PrometheusRule app-alerts"
check_file "$PROJECT_ROOT/infrastructure/monitoring/prometheus-rules/infra-alerts.yaml" "PrometheusRule infra-alerts"
check_dir "$PROJECT_ROOT/infrastructure/monitoring/grafana/dashboards" "Grafana dashboards directory"
check_file "$PROJECT_ROOT/infrastructure/monitoring/grafana/provisioning/datasources.yaml" "Grafana datasources"
check_file "$PROJECT_ROOT/infrastructure/monitoring/grafana/provisioning/dashboards.yaml" "Grafana dashboard provisioning"
echo ""

# ==============================================================================
# CI/CD Validation
# ==============================================================================
log_info "Validating CI/CD Configuration..."
check_file "$PROJECT_ROOT/cicd/Jenkinsfile" "Jenkinsfile"
check_file "$PROJECT_ROOT/cicd/argocd/applications.yaml" "ArgoCD applications"
echo ""

# ==============================================================================
# Scripts Validation
# ==============================================================================
log_info "Validating Scripts..."
check_file "$PROJECT_ROOT/scripts/setup-gcp.sh" "GCP setup script"
check_file "$PROJECT_ROOT/scripts/build-push-images.sh" "Build/push images script"
check_file "$PROJECT_ROOT/scripts/deploy-gke.sh" "GKE deploy script"
check_file "$PROJECT_ROOT/scripts/install-monitoring.sh" "Install monitoring script"
echo ""

# ==============================================================================
# Documentation Validation
# ==============================================================================
log_info "Validating Documentation..."
check_file "$PROJECT_ROOT/README.md" "Project README.md"
check_file "$PROJECT_ROOT/docs/PREREQUISITES.md" "Prerequisites documentation"
check_file "$PROJECT_ROOT/docs/EXECUTION-GUIDE.md" "Execution guide"
check_file "$PROJECT_ROOT/docs/TROUBLESHOOTING.md" "Troubleshooting guide"
check_file "$PROJECT_ROOT/docs/MONITORING-GUIDE.md" "Monitoring guide"
echo ""

# ==============================================================================
# Summary
# ==============================================================================
echo "=============================================="
echo "               Validation Summary"
echo "=============================================="

if [[ $ERRORS -gt 0 ]]; then
    log_error "Validation FAILED with $ERRORS error(s) and $WARNINGS warning(s)"
    exit 1
else
    if [[ $WARNINGS -gt 0 ]]; then
        log_warning "Validation PASSED with $WARNINGS warning(s)"
    else
        log_success "Validation PASSED - All checks successful!"
    fi
    echo ""
    echo "Project is ready for deployment!"
    echo ""
    echo "Next steps:"
    echo "  1. Update terraform.tfvars with your GCP project ID"
    echo "  2. Run: ./scripts/setup-gcp.sh"
    echo "  3. Run: terraform init && terraform apply"
    echo "  4. Run: ./scripts/build-push-images.sh"
    echo "  5. Run: ./scripts/deploy-gke.sh"
    echo "  6. Run: ./scripts/install-monitoring.sh"
fi
