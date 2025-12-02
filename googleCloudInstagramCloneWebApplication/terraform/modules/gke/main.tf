# GKE Module - Creates GKE cluster with Zonal configuration
# Nodes are deployed across zones within the specified region

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "network_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "pods_range_name" {
  type = string
}

variable "services_range_name" {
  type = string
}

variable "node_machine_type" {
  type    = string
  default = "e2-standard-4"
}

variable "min_node_count" {
  type    = number
  default = 1
}

variable "max_node_count" {
  type    = number
  default = 5
}

variable "disk_size_gb" {
  type    = number
  default = 100
}

variable "kubernetes_version" {
  type    = string
  default = "1.32"
}

# Zone configuration - nodes will be distributed across these zones
variable "node_zones" {
  type        = list(string)
  default     = []
  description = "List of zones for node placement. If empty, uses default zones for the region."
}

# Local variable to determine zones based on region
locals {
  # Define default zones per region for node distribution
  default_zones = {
    "us-central1"  = ["us-central1-a", "us-central1-b", "us-central1-c"]
    "us-east1"     = ["us-east1-b", "us-east1-c", "us-east1-d"]
    "us-west1"     = ["us-west1-a", "us-west1-b", "us-west1-c"]
    "europe-west1" = ["europe-west1-b", "europe-west1-c", "europe-west1-d"]
    "asia-east1"   = ["asia-east1-a", "asia-east1-b", "asia-east1-c"]
  }

  # Use provided zones or fall back to defaults
  cluster_zones = length(var.node_zones) > 0 ? var.node_zones : lookup(local.default_zones, var.region, ["${var.region}-a", "${var.region}-b", "${var.region}-c"])

  # Primary zone for cluster master (first zone in the list)
  primary_zone = local.cluster_zones[0]
}

# Get available GKE versions for the primary zone
data "google_container_engine_versions" "gke_versions" {
  project        = var.project_id
  location       = local.primary_zone
  version_prefix = var.kubernetes_version
}

# GKE Cluster - Regional cluster with zonal node distribution
resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  project  = var.project_id
  location = var.region # Regional cluster for HA control plane

  # Use latest version matching the prefix
  min_master_version = data.google_container_engine_versions.gke_versions.latest_master_version

  # Specify node locations (zones) within the region
  node_locations = local.cluster_zones

  # Network configuration
  network    = var.network_id
  subnetwork = var.subnet_id

  # IP allocation policy for VPC-native cluster
  ip_allocation_policy {
    cluster_secondary_range_name  = var.pods_range_name
    services_secondary_range_name = var.services_range_name
  }

  # Remove default node pool
  remove_default_node_pool = true
  initial_node_count       = 1

  # Private cluster configuration
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  # Master authorized networks
  master_authorized_networks_config {
    cidr_blocks {
      cidr_block   = "0.0.0.0/0"
      display_name = "All networks"
    }
  }

  # Workload Identity
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  # Binary authorization
  binary_authorization {
    evaluation_mode = "PROJECT_SINGLETON_POLICY_ENFORCE"
  }

  # Security settings
  enable_shielded_nodes = true

  # Release channel
  release_channel {
    channel = var.environment == "prod" ? "STABLE" : "REGULAR"
  }

  # Addons
  addons_config {
    http_load_balancing {
      disabled = false
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
    network_policy_config {
      disabled = false
    }
    gcs_fuse_csi_driver_config {
      enabled = true
    }
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
  }

  # Network policy
  network_policy {
    enabled  = true
    provider = "CALICO"
  }

  # Logging and monitoring
  logging_config {
    enable_components = ["SYSTEM_COMPONENTS", "WORKLOADS"]
  }

  monitoring_config {
    enable_components = ["SYSTEM_COMPONENTS"]
    managed_prometheus {
      enabled = true
    }
  }

  # Maintenance window
  maintenance_policy {
    recurring_window {
      start_time = "2024-01-01T03:00:00Z"
      end_time   = "2024-01-01T07:00:00Z"
      recurrence = "FREQ=WEEKLY;BYDAY=SA,SU"
    }
  }

  # Resource labels
  resource_labels = {
    environment = var.environment
    managed_by  = "terraform"
  }

  lifecycle {
    ignore_changes = [
      node_pool,
      initial_node_count
    ]
  }
}

# Node Pool - Distributed across zones
resource "google_container_node_pool" "primary_nodes" {
  name     = "${var.cluster_name}-node-pool"
  project  = var.project_id
  location = var.region # Regional node pool
  cluster  = google_container_cluster.primary.name

  # Node locations - nodes will be distributed across these zones
  node_locations = local.cluster_zones

  # Version
  version = data.google_container_engine_versions.gke_versions.latest_node_version

  # Initial node count per zone (will be managed by autoscaler)
  initial_node_count = var.min_node_count

  # Autoscaling - per zone configuration
  autoscaling {
    min_node_count  = var.min_node_count
    max_node_count  = var.max_node_count
    location_policy = "BALANCED" # Distribute nodes evenly across zones
  }

  # Node management
  management {
    auto_repair  = true
    auto_upgrade = true
  }

  # Node configuration
  node_config {
    machine_type = var.node_machine_type
    disk_size_gb = var.disk_size_gb
    disk_type    = "pd-ssd"

    # Service account
    service_account = google_service_account.gke_nodes.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    # Shielded instance config
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }

    # Workload metadata config
    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    # Labels
    labels = {
      environment = var.environment
    }

    # Tags for firewall rules
    tags = ["gke-node", var.cluster_name]

    # Metadata
    metadata = {
      disable-legacy-endpoints = "true"
    }
  }

  lifecycle {
    ignore_changes = [
      node_config[0].labels,
      node_config[0].taint
    ]
  }
}

# Service Account for GKE nodes
resource "google_service_account" "gke_nodes" {
  account_id   = "${var.cluster_name}-nodes"
  display_name = "GKE Node Service Account"
  project      = var.project_id
}

# IAM roles for GKE node service account
resource "google_project_iam_member" "gke_node_roles" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/stackdriver.resourceMetadata.writer",
    "roles/artifactregistry.reader",
    "roles/storage.objectViewer"
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_nodes.email}"
}

# Outputs
output "cluster_name" {
  value = google_container_cluster.primary.name
}

output "cluster_endpoint" {
  value     = google_container_cluster.primary.endpoint
  sensitive = true
}

output "cluster_ca_certificate" {
  value     = google_container_cluster.primary.master_auth[0].cluster_ca_certificate
  sensitive = true
}

output "node_service_account" {
  value = google_service_account.gke_nodes.email
}

output "cluster_id" {
  value = google_container_cluster.primary.id
}

output "cluster_location" {
  value = google_container_cluster.primary.location
}

output "node_locations" {
  value       = local.cluster_zones
  description = "List of zones where nodes are deployed"
}

output "primary_zone" {
  value       = local.primary_zone
  description = "Primary zone for the cluster"
}
