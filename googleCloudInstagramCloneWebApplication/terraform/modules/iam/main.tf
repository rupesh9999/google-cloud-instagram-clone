# IAM Module - Creates service accounts and IAM bindings

variable "project_id" {
  type = string
}

variable "environment" {
  type = string
}

variable "project_name" {
  type = string
}

variable "gke_service_account" {
  type = string
}

variable "media_bucket_name" {
  type = string
}

# Application Service Account (for microservices)
resource "google_service_account" "app" {
  account_id   = "${var.project_name}-${var.environment}-app"
  display_name = "Instagram Clone Application Service Account"
  project      = var.project_id
}

# IAM roles for application service account
resource "google_project_iam_member" "app_roles" {
  for_each = toset([
    "roles/cloudsql.client",
    "roles/secretmanager.secretAccessor",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/cloudtrace.agent",
    "roles/redis.editor"
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.app.email}"
}

# GCS permissions for app service account
resource "google_storage_bucket_iam_member" "app_gcs" {
  bucket = var.media_bucket_name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.app.email}"
}

# Workload Identity binding
resource "google_service_account_iam_member" "workload_identity" {
  service_account_id = google_service_account.app.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[instagram-clone/instagram-app]"
}

# CI/CD Service Account (for Jenkins/ArgoCD)
resource "google_service_account" "cicd" {
  account_id   = "${var.project_name}-${var.environment}-cicd"
  display_name = "Instagram Clone CI/CD Service Account"
  project      = var.project_id
}

# IAM roles for CI/CD service account
resource "google_project_iam_member" "cicd_roles" {
  for_each = toset([
    "roles/container.developer",
    "roles/artifactregistry.writer",
    "roles/storage.admin",
    "roles/cloudbuild.builds.builder"
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.cicd.email}"
}

# Outputs
output "app_service_account_email" {
  value = google_service_account.app.email
}

output "app_service_account_name" {
  value = google_service_account.app.name
}

output "cicd_service_account_email" {
  value = google_service_account.cicd.email
}
