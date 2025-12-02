// Convenience outputs for scripts and CLI
output "project_id" {
  description = "GCP project id used for this deployment"
  value       = var.project_id
}

output "region" {
  description = "GCP region used for this deployment"
  value       = var.region
}
