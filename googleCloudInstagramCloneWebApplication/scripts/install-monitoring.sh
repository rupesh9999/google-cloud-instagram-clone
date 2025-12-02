#!/bin/bash
# =============================================================================
# Install Monitoring Stack Script
# Installs Prometheus, Grafana, and related monitoring components using Helm
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
NAMESPACE="${MONITORING_NAMESPACE:-monitoring}"
RELEASE_NAME="${RELEASE_NAME:-kube-prometheus-stack}"
GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-admin123}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MONITORING_DIR="${PROJECT_ROOT}/infrastructure/monitoring"
HELM_DIR="${PROJECT_ROOT}/infrastructure/helm"

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    if ! command -v helm &> /dev/null; then
        log_error "Helm is not installed. Please install Helm first."
        exit 1
    fi
    
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Add Helm repositories
add_helm_repos() {
    log_info "Adding Helm repositories..."
    
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo add grafana https://grafana.github.io/helm-charts
    helm repo update
    
    log_success "Helm repositories updated"
}

# Create monitoring namespace
create_namespace() {
    log_info "Creating monitoring namespace..."
    
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    log_success "Namespace $NAMESPACE ready"
}

# Install kube-prometheus-stack
install_prometheus_stack() {
    log_info "Installing kube-prometheus-stack..."
    
    if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
        log_warning "kube-prometheus-stack already installed. Upgrading..."
        HELM_CMD="upgrade"
    else
        HELM_CMD="install"
    fi
    
    helm $HELM_CMD "$RELEASE_NAME" prometheus-community/kube-prometheus-stack \
        --namespace "$NAMESPACE" \
        --values "${HELM_DIR}/monitoring-values.yaml" \
        --set grafana.adminPassword="${GRAFANA_ADMIN_PASSWORD}" \
        --wait \
        --timeout 10m
    
    log_success "kube-prometheus-stack installed"
}

# Install Loki for log aggregation
install_loki() {
    log_info "Installing Loki stack..."
    
    if helm list -n "$NAMESPACE" | grep -q loki-stack; then
        log_warning "Loki stack already installed. Upgrading..."
        HELM_CMD="upgrade"
    else
        HELM_CMD="install"
    fi
    
    helm $HELM_CMD loki-stack grafana/loki-stack \
        --namespace "$NAMESPACE" \
        --set promtail.enabled=true \
        --set loki.persistence.enabled=false \
        --wait
    
    log_success "Loki stack installed"
}

# Apply ServiceMonitors for application
apply_servicemonitors() {
    log_info "Applying ServiceMonitors for Instagram Clone services..."
    
    kubectl apply -f "${MONITORING_DIR}/servicemonitors/"
    
    log_success "ServiceMonitors applied"
}

# Apply Prometheus rules
apply_prometheus_rules() {
    log_info "Applying PrometheusRules..."
    
    kubectl apply -f "${MONITORING_DIR}/prometheus-rules/"
    
    log_success "PrometheusRules applied"
}

# Import Grafana dashboards
import_dashboards() {
    log_info "Importing Grafana dashboards..."
    
    # Create ConfigMaps for dashboards
    for dashboard in "${MONITORING_DIR}/grafana/dashboards/"*.json; do
        if [[ -f "$dashboard" ]]; then
            name=$(basename "$dashboard" .json)
            kubectl create configmap "grafana-dashboard-${name}" \
                --from-file="$dashboard" \
                -n "$NAMESPACE" \
                --dry-run=client -o yaml | \
                kubectl label --local -f - grafana_dashboard=1 -o yaml | \
                kubectl apply -f -
        fi
    done
    
    log_success "Dashboards imported"
}

# Show access information
show_access_info() {
    echo ""
    log_info "Monitoring Stack Access Information:"
    echo "=============================================="
    
    # Get Grafana URL
    echo ""
    echo "Grafana:"
    echo "  Port-forward: kubectl port-forward svc/${RELEASE_NAME}-grafana 3000:80 -n ${NAMESPACE}"
    echo "  URL: http://localhost:3000"
    echo "  Username: admin"
    echo "  Password: ${GRAFANA_ADMIN_PASSWORD}"
    
    # Get Prometheus URL
    echo ""
    echo "Prometheus:"
    echo "  Port-forward: kubectl port-forward svc/${RELEASE_NAME}-prometheus 9090:9090 -n ${NAMESPACE}"
    echo "  URL: http://localhost:9090"
    
    # Get Alertmanager URL
    echo ""
    echo "Alertmanager:"
    echo "  Port-forward: kubectl port-forward svc/${RELEASE_NAME}-alertmanager 9093:9093 -n ${NAMESPACE}"
    echo "  URL: http://localhost:9093"
    
    echo ""
    echo "=============================================="
}

# Show status
show_status() {
    log_info "Monitoring stack status:"
    echo ""
    kubectl get pods -n "$NAMESPACE"
    echo ""
    kubectl get services -n "$NAMESPACE"
}

# Usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --namespace NAMESPACE     Kubernetes namespace (default: monitoring)"
    echo "  --grafana-password PASS   Grafana admin password"
    echo "  --skip-loki               Skip Loki installation"
    echo "  --skip-dashboards         Skip dashboard import"
    echo "  -h, --help                Show this help message"
}

# Main
main() {
    local skip_loki=false
    local skip_dashboards=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            --grafana-password)
                GRAFANA_ADMIN_PASSWORD="$2"
                shift 2
                ;;
            --skip-loki)
                skip_loki=true
                shift
                ;;
            --skip-dashboards)
                skip_dashboards=true
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
    echo "    Instagram Clone - Monitoring Stack Setup"
    echo "=============================================="
    echo ""
    
    check_prerequisites
    add_helm_repos
    create_namespace
    install_prometheus_stack
    
    if [[ "$skip_loki" == false ]]; then
        install_loki
    fi
    
    apply_servicemonitors
    apply_prometheus_rules
    
    if [[ "$skip_dashboards" == false ]]; then
        import_dashboards
    fi
    
    show_status
    show_access_info
    
    log_success "Monitoring stack installation complete!"
}

main "$@"
