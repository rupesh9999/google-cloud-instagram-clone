# Core variables
variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "instagram-clone"
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

# VPC Variables
variable "gke_subnet_cidr" {
  description = "CIDR range for GKE subnet"
  type        = string
  default     = "10.0.0.0/20"
}

variable "gke_pods_cidr" {
  description = "CIDR range for GKE pods"
  type        = string
  default     = "10.4.0.0/14"
}

variable "gke_services_cidr" {
  description = "CIDR range for GKE services"
  type        = string
  default     = "10.8.0.0/20"
}

variable "cloud_sql_private_ip_cidr" {
  description = "CIDR range for Cloud SQL private IP allocation"
  type        = string
  default     = "10.10.0.0/24"
}

# GKE Variables
variable "kubernetes_version" {
  description = "Kubernetes version for GKE cluster (must be 1.32+)"
  type        = string
  default     = "1.32"
}

variable "gke_node_machine_type" {
  description = "Machine type for GKE nodes"
  type        = string
  default     = "e2-standard-4"
}

variable "gke_min_node_count" {
  description = "Minimum number of nodes per zone"
  type        = number
  default     = 1
}

variable "gke_max_node_count" {
  description = "Maximum number of nodes per zone"
  type        = number
  default     = 5
}

variable "gke_disk_size_gb" {
  description = "Disk size for GKE nodes in GB"
  type        = number
  default     = 100
}

variable "gke_node_zones" {
  description = "List of zones within the region for GKE node placement"
  type        = list(string)
  default     = []
}

# Cloud SQL Variables
variable "postgres_version" {
  description = "PostgreSQL version"
  type        = string
  default     = "POSTGRES_15"
}

variable "cloud_sql_tier" {
  description = "Cloud SQL machine tier"
  type        = string
  default     = "db-custom-2-8192"
}

variable "cloud_sql_disk_size" {
  description = "Cloud SQL disk size in GB"
  type        = number
  default     = 50
}

# Redis Variables
variable "redis_tier" {
  description = "Redis tier (BASIC or STANDARD_HA)"
  type        = string
  default     = "BASIC"
}

variable "redis_memory_size_gb" {
  description = "Redis memory size in GB"
  type        = number
  default     = 1
}

variable "redis_version" {
  description = "Redis version"
  type        = string
  default     = "REDIS_7_0"
}

# Application Variables
variable "cors_allowed_origins" {
  description = "CORS allowed origins for GCS bucket"
  type        = list(string)
  default     = ["*"]
}

variable "enable_api_gateway" {
  description = "Enable API Gateway"
  type        = bool
  default     = false
}

# Tags
variable "labels" {
  description = "Labels to apply to all resources"
  type        = map(string)
  default     = {}
}
