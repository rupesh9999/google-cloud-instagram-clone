# Instagram Clone Infrastructure - Main Configuration
# This is the root module that orchestrates all infrastructure components

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.20"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 5.20"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "gcs" {
    # Configure in backend.tf or via -backend-config
  }
}

# Provider configurations
provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

# Data source for GKE cluster credentials
data "google_client_config" "default" {}

# Kubernetes provider configured after GKE cluster is created
provider "kubernetes" {
  host                   = "https://${module.gke.cluster_endpoint}"
  token                  = data.google_client_config.default.access_token
  cluster_ca_certificate = base64decode(module.gke.cluster_ca_certificate)
}

# VPC Network
module "vpc" {
  source = "./modules/vpc"

  project_id   = var.project_id
  region       = var.region
  environment  = var.environment
  network_name = "${var.project_name}-${var.environment}-vpc"

  # Subnet configurations
  gke_subnet_cidr           = var.gke_subnet_cidr
  gke_pods_cidr             = var.gke_pods_cidr
  gke_services_cidr         = var.gke_services_cidr
  cloud_sql_private_ip_cidr = var.cloud_sql_private_ip_cidr
}

# GKE Cluster
module "gke" {
  source = "./modules/gke"

  project_id   = var.project_id
  region       = var.region
  environment  = var.environment
  cluster_name = "${var.project_name}-${var.environment}-gke"

  # Network configuration
  network_id          = module.vpc.network_id
  subnet_id           = module.vpc.gke_subnet_id
  pods_range_name     = module.vpc.pods_range_name
  services_range_name = module.vpc.services_range_name

  # Node pool configuration
  node_machine_type = var.gke_node_machine_type
  min_node_count    = var.gke_min_node_count
  max_node_count    = var.gke_max_node_count
  disk_size_gb      = var.gke_disk_size_gb
  node_zones        = var.gke_node_zones

  # Kubernetes version
  kubernetes_version = var.kubernetes_version

  # Deletion protection
  deletion_protection = var.gke_deletion_protection

  depends_on = [module.vpc]
}

# Cloud SQL PostgreSQL
module "cloud_sql" {
  source = "./modules/cloud-sql"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  # Instance configuration
  instance_name    = "${var.project_name}-${var.environment}-postgres"
  database_version = var.postgres_version
  tier             = var.cloud_sql_tier
  disk_size        = var.cloud_sql_disk_size

  # Network configuration
  network_id                 = module.vpc.network_id
  private_network_connection = module.vpc.private_services_connection

  # Database configurations for each microservice
  databases = [
    "instagram_auth",
    "instagram_user",
    "instagram_post",
    "instagram_comment",
    "instagram_like"
  ]

  # High availability
  availability_type = var.environment == "prod" ? "REGIONAL" : "ZONAL"

  depends_on = [module.vpc]
}

# Cloud Memorystore (Redis)
module "memorystore" {
  source = "./modules/memorystore"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  instance_name  = "${var.project_name}-${var.environment}-redis"
  tier           = var.redis_tier
  memory_size_gb = var.redis_memory_size_gb
  redis_version  = var.redis_version

  network_id = module.vpc.network_id

  depends_on = [module.vpc]
}

# Cloud Storage
module "gcs" {
  source = "./modules/gcs"

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  bucket_name = "${var.project_name}-${var.environment}-media"

  # CORS configuration for frontend uploads
  cors_origins = var.cors_allowed_origins
}

# IAM and Service Accounts
module "iam" {
  source = "./modules/iam"

  project_id   = var.project_id
  environment  = var.environment
  project_name = var.project_name

  # GKE service account
  gke_service_account = module.gke.node_service_account

  # GCS bucket for media
  media_bucket_name = module.gcs.bucket_name

  # Workload Identity configuration - depends on GKE cluster
  enable_workload_identity = true
  workload_identity_pool   = module.gke.workload_identity_pool

  depends_on = [module.gke]
}

# Secret Manager
module "secret_manager" {
  source = "./modules/secret-manager"

  project_id   = var.project_id
  environment  = var.environment
  project_name = var.project_name

  # Secrets to create
  secrets = {
    jwt_secret = {
      description = "JWT signing secret"
      generate    = true
    }
    db_password = {
      description = "Cloud SQL database password"
      value       = module.cloud_sql.db_password
    }
    redis_auth = {
      description = "Redis AUTH string"
      value       = module.memorystore.auth_string
    }
  }

  # Service accounts that can access secrets
  accessor_service_accounts = [
    module.iam.app_service_account_email
  ]
}

# API Gateway (Optional - for external API access)
module "api_gateway" {
  source = "./modules/api-gateway"
  count  = var.enable_api_gateway ? 1 : 0

  project_id  = var.project_id
  region      = var.region
  environment = var.environment

  api_name = "${var.project_name}-${var.environment}-api"

  # Backend configuration
  gke_endpoint = module.gke.cluster_endpoint
}

# Outputs
output "gke_cluster_name" {
  description = "GKE cluster name"
  value       = module.gke.cluster_name
}

output "gke_cluster_endpoint" {
  description = "GKE cluster endpoint"
  value       = module.gke.cluster_endpoint
  sensitive   = true
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL connection name"
  value       = module.cloud_sql.connection_name
}

output "cloud_sql_private_ip" {
  description = "Cloud SQL private IP"
  value       = module.cloud_sql.private_ip
  sensitive   = true
}

output "redis_host" {
  description = "Redis instance host"
  value       = module.memorystore.host
  sensitive   = true
}

output "media_bucket_name" {
  description = "GCS media bucket name"
  value       = module.gcs.bucket_name
}

output "app_service_account" {
  description = "Application service account email"
  value       = module.iam.app_service_account_email
}
