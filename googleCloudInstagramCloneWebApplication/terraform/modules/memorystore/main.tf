# Memorystore Module - Creates Redis instance

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "instance_name" {
  type = string
}

variable "tier" {
  type    = string
  default = "BASIC"
}

variable "memory_size_gb" {
  type    = number
  default = 1
}

variable "redis_version" {
  type    = string
  default = "REDIS_7_0"
}

variable "network_id" {
  type = string
}

# Redis Instance
resource "google_redis_instance" "cache" {
  name               = var.instance_name
  project            = var.project_id
  region             = var.region
  tier               = var.tier
  memory_size_gb     = var.memory_size_gb
  redis_version      = var.redis_version
  authorized_network = var.network_id

  # Auth enabled
  auth_enabled = true

  # Transit encryption
  transit_encryption_mode = "SERVER_AUTHENTICATION"

  # Connect mode
  connect_mode = "PRIVATE_SERVICE_ACCESS"

  # Maintenance policy
  maintenance_policy {
    weekly_maintenance_window {
      day = "SUNDAY"
      start_time {
        hours   = 3
        minutes = 0
      }
    }
  }

  # Redis configs
  redis_configs = {
    maxmemory-policy       = "allkeys-lru"
    notify-keyspace-events = "Ex"
  }

  # Labels
  labels = {
    environment = var.environment
    managed_by  = "terraform"
  }
}

# Outputs
output "host" {
  value     = google_redis_instance.cache.host
  sensitive = true
}

output "port" {
  value = google_redis_instance.cache.port
}

output "auth_string" {
  value     = google_redis_instance.cache.auth_string
  sensitive = true
}

output "instance_name" {
  value = google_redis_instance.cache.name
}

output "current_location_id" {
  value = google_redis_instance.cache.current_location_id
}
