// Convenience outputs for scripts and CLI
output "project_id" {
  description = "GCP project id used for this deployment"
  value       = var.project_id
}

output "region" {
  description = "GCP region used for this deployment"
  value       = var.region
}

# Backwards-compatible alias expected by the Execution Guide
# (media_bucket_name is already defined in main.tf)
output "gcs_bucket_name" {
  description = "GCS bucket name (alias for media_bucket_name)"
  value       = module.gcs.bucket_name
}
