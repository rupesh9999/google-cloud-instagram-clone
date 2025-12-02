# GCS Module - Creates Cloud Storage bucket for media files

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "bucket_name" {
  type = string
}

variable "cors_origins" {
  type    = list(string)
  default = ["*"]
}

# Storage bucket
resource "google_storage_bucket" "media" {
  name          = var.bucket_name
  project       = var.project_id
  location      = var.region
  force_destroy = var.environment != "prod"

  # Storage class
  storage_class = "STANDARD"

  # Uniform bucket-level access
  uniform_bucket_level_access = true

  # Versioning
  versioning {
    enabled = var.environment == "prod"
  }

  # Lifecycle rules
  lifecycle_rule {
    condition {
      age = 365
    }
    action {
      type          = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }

  lifecycle_rule {
    condition {
      age = 730
    }
    action {
      type          = "SetStorageClass"
      storage_class = "COLDLINE"
    }
  }

  # CORS configuration
  cors {
    origin          = var.cors_origins
    method          = ["GET", "HEAD", "PUT", "POST", "DELETE"]
    response_header = ["*"]
    max_age_seconds = 3600
  }

  # Labels
  labels = {
    environment = var.environment
    managed_by  = "terraform"
  }
}

# Make bucket objects publicly readable (for serving images)
resource "google_storage_bucket_iam_member" "public_read" {
  bucket = google_storage_bucket.media.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

# Outputs
output "bucket_name" {
  value = google_storage_bucket.media.name
}

output "bucket_url" {
  value = google_storage_bucket.media.url
}

output "bucket_self_link" {
  value = google_storage_bucket.media.self_link
}
