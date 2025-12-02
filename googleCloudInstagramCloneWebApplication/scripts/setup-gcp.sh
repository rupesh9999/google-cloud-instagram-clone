#!/bin/bash
# =============================================================================
# GCP Project Setup Script
# This script configures the GCP project with required APIs and initial setup
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if gcloud is installed
check_gcloud() {
    if ! command -v gcloud &> /dev/null; then
        log_error "gcloud CLI is not installed. Please install it first."
        exit 1
    fi
    log_success "gcloud CLI found"
}

# Check if user is authenticated
check_auth() {
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -1 &> /dev/null; then
        log_error "Not authenticated with gcloud. Run 'gcloud auth login'"
        exit 1
    fi
    log_success "Authenticated with GCP"
}

# Get or set project ID
get_project() {
    if [[ -z "${PROJECT_ID:-}" ]]; then
        PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
        if [[ -z "$PROJECT_ID" ]]; then
            log_error "No project set. Set PROJECT_ID environment variable or run 'gcloud config set project <PROJECT_ID>'"
            exit 1
        fi
    fi
    log_info "Using project: $PROJECT_ID"
}

# Enable required APIs
enable_apis() {
    log_info "Enabling required GCP APIs..."
    
    APIS=(
        "container.googleapis.com"
        "containerregistry.googleapis.com"
        "artifactregistry.googleapis.com"
        "sqladmin.googleapis.com"
        "redis.googleapis.com"
        "secretmanager.googleapis.com"
        "storage.googleapis.com"
        "compute.googleapis.com"
        "servicenetworking.googleapis.com"
        "cloudresourcemanager.googleapis.com"
        "iam.googleapis.com"
        "iap.googleapis.com"
        "logging.googleapis.com"
        "monitoring.googleapis.com"
        "apigateway.googleapis.com"
        "servicemanagement.googleapis.com"
        "servicecontrol.googleapis.com"
        "dns.googleapis.com"
        "certificatemanager.googleapis.com"
    )
    
    for api in "${APIS[@]}"; do
        log_info "  Enabling $api..."
        gcloud services enable "$api" --project="$PROJECT_ID" --quiet || true
    done
    
    log_success "All APIs enabled"
}

# Create Terraform service account
create_terraform_sa() {
    local SA_NAME="terraform-admin"
    local SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
    
    log_info "Creating Terraform service account..."
    
    # Check if SA exists
    if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" &>/dev/null; then
        log_warning "Service account $SA_EMAIL already exists"
    else
        gcloud iam service-accounts create "$SA_NAME" \
            --display-name="Terraform Admin Service Account" \
            --project="$PROJECT_ID"
        log_success "Created service account: $SA_EMAIL"
    fi
    
    # Grant required roles
    ROLES=(
        "roles/editor"
        "roles/container.admin"
        "roles/compute.admin"
        "roles/iam.serviceAccountAdmin"
        "roles/iam.serviceAccountUser"
        "roles/resourcemanager.projectIamAdmin"
        "roles/secretmanager.admin"
        "roles/cloudsql.admin"
        "roles/redis.admin"
        "roles/storage.admin"
        "roles/servicenetworking.networksAdmin"
        "roles/apigateway.admin"
    )
    
    log_info "Granting IAM roles to Terraform service account..."
    for role in "${ROLES[@]}"; do
        gcloud projects add-iam-policy-binding "$PROJECT_ID" \
            --member="serviceAccount:$SA_EMAIL" \
            --role="$role" \
            --quiet || true
    done
    
    log_success "IAM roles granted"
    
    # Create and download key
    local KEY_FILE="terraform-sa-key.json"
    if [[ ! -f "$KEY_FILE" ]]; then
        log_info "Creating service account key..."
        gcloud iam service-accounts keys create "$KEY_FILE" \
            --iam-account="$SA_EMAIL" \
            --project="$PROJECT_ID"
        log_success "Service account key saved to: $KEY_FILE"
        log_warning "Store this key securely and add to .gitignore!"
    else
        log_warning "Key file $KEY_FILE already exists. Skipping key creation."
    fi
}

# Create GCS bucket for Terraform state
create_tf_state_bucket() {
    local BUCKET_NAME="${PROJECT_ID}-terraform-state"
    
    log_info "Creating Terraform state bucket..."
    
    if gsutil ls "gs://$BUCKET_NAME" &>/dev/null; then
        log_warning "Bucket gs://$BUCKET_NAME already exists"
    else
        gsutil mb -p "$PROJECT_ID" -l "us-central1" "gs://$BUCKET_NAME"
        gsutil versioning set on "gs://$BUCKET_NAME"
        log_success "Created bucket: gs://$BUCKET_NAME"
    fi
}

# Setup Docker authentication
setup_docker_auth() {
    log_info "Configuring Docker authentication for GCR..."
    gcloud auth configure-docker gcr.io --quiet
    log_success "Docker configured for GCR"
}

# Main execution
main() {
    echo "=============================================="
    echo "    Instagram Clone - GCP Setup Script"
    echo "=============================================="
    echo ""
    
    check_gcloud
    check_auth
    get_project
    enable_apis
    create_terraform_sa
    create_tf_state_bucket
    setup_docker_auth
    
    echo ""
    echo "=============================================="
    log_success "GCP setup completed successfully!"
    echo "=============================================="
    echo ""
    echo "Next steps:"
    echo "1. Set GOOGLE_APPLICATION_CREDENTIALS:"
    echo "   export GOOGLE_APPLICATION_CREDENTIALS=\"\$(pwd)/terraform-sa-key.json\""
    echo ""
    echo "2. Update terraform/backend.tf with:"
    echo "   bucket = \"${PROJECT_ID}-terraform-state\""
    echo ""
    echo "3. Initialize Terraform:"
    echo "   cd terraform && terraform init"
    echo ""
}

main "$@"
