# Secret Manager Module - Creates and manages secrets

variable "project_id" {
  type = string
}

variable "environment" {
  type = string
}

variable "project_name" {
  type = string
}

variable "secrets" {
  type = map(object({
    description = string
    value       = optional(string)
    generate    = optional(bool, false)
  }))
  default = {}
}

variable "accessor_service_accounts" {
  type    = list(string)
  default = []
}

# Generate random secrets when needed
resource "random_password" "generated" {
  for_each = { for k, v in var.secrets : k => v if v.generate == true }

  length           = 64
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Create secrets
resource "google_secret_manager_secret" "secrets" {
  for_each = var.secrets

  secret_id = "${var.project_name}-${var.environment}-${replace(each.key, "_", "-")}"
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    environment = var.environment
    managed_by  = "terraform"
  }
}

# Secret versions
resource "google_secret_manager_secret_version" "versions" {
  for_each = var.secrets

  secret = google_secret_manager_secret.secrets[each.key].id

  secret_data = each.value.generate ? random_password.generated[each.key].result : each.value.value
}

# IAM bindings for secret access
resource "google_secret_manager_secret_iam_member" "accessors" {
  for_each = {
    for pair in setproduct(keys(var.secrets), var.accessor_service_accounts) :
    "${pair[0]}-${pair[1]}" => {
      secret  = pair[0]
      account = pair[1]
    }
  }

  project   = var.project_id
  secret_id = google_secret_manager_secret.secrets[each.value.secret].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${each.value.account}"
}

# Outputs
output "secret_ids" {
  value = { for k, v in google_secret_manager_secret.secrets : k => v.secret_id }
}

output "secret_names" {
  value = { for k, v in google_secret_manager_secret.secrets : k => v.name }
}
