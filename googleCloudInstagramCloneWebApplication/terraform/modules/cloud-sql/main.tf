# Cloud SQL Module - Creates PostgreSQL instance with multiple databases

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

variable "database_version" {
  type    = string
  default = "POSTGRES_15"
}

variable "tier" {
  type    = string
  default = "db-custom-2-8192"
}

variable "disk_size" {
  type    = number
  default = 50
}

variable "network_id" {
  type = string
}

variable "private_network_connection" {
  type = string
}

variable "databases" {
  type    = list(string)
  default = []
}

variable "availability_type" {
  type    = string
  default = "ZONAL"
}

# Random password for database
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Cloud SQL Instance
resource "google_sql_database_instance" "postgres" {
  name                = var.instance_name
  project             = var.project_id
  region              = var.region
  database_version    = var.database_version
  deletion_protection = var.environment == "prod" ? true : false

  settings {
    tier              = var.tier
    availability_type = var.availability_type
    disk_size         = var.disk_size
    disk_type         = "PD_SSD"
    disk_autoresize   = true

    # IP configuration - Private only
    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = var.network_id
      enable_private_path_for_google_cloud_services = true
    }

    # Backup configuration
    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = var.environment == "prod"
      backup_retention_settings {
        retained_backups = var.environment == "prod" ? 30 : 7
      }
    }

    # Maintenance window
    maintenance_window {
      day          = 7 # Sunday
      hour         = 3
      update_track = var.environment == "prod" ? "stable" : "canary"
    }

    # Database flags
    database_flags {
      name  = "log_checkpoints"
      value = "on"
    }

    database_flags {
      name  = "log_connections"
      value = "on"
    }

    database_flags {
      name  = "log_disconnections"
      value = "on"
    }

    database_flags {
      name  = "log_lock_waits"
      value = "on"
    }

    database_flags {
      name  = "log_temp_files"
      value = "0"
    }

    database_flags {
      name  = "max_connections"
      value = "500"
    }

    # Insights configuration
    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = true
    }

    # Labels
    user_labels = {
      environment = var.environment
      managed_by  = "terraform"
    }
  }

  depends_on = [var.private_network_connection]
}

# Default user
resource "google_sql_user" "default" {
  name     = "instagram_app"
  instance = google_sql_database_instance.postgres.name
  project  = var.project_id
  password = random_password.db_password.result
}

# Databases
resource "google_sql_database" "databases" {
  for_each = toset(var.databases)

  name     = each.value
  instance = google_sql_database_instance.postgres.name
  project  = var.project_id
}

# Outputs
output "instance_name" {
  value = google_sql_database_instance.postgres.name
}

output "connection_name" {
  value = google_sql_database_instance.postgres.connection_name
}

output "private_ip" {
  value     = google_sql_database_instance.postgres.private_ip_address
  sensitive = true
}

output "db_user" {
  value = google_sql_user.default.name
}

output "db_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "databases" {
  value = [for db in google_sql_database.databases : db.name]
}
