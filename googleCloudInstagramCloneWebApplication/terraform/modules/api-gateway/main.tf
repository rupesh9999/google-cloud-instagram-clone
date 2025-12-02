# API Gateway Module - Creates API Gateway for external API access

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "api_name" {
  type = string
}

variable "gke_endpoint" {
  type = string
}

# API Gateway API
resource "google_api_gateway_api" "api" {
  provider = google-beta
  api_id   = var.api_name
  project  = var.project_id
}

# API Config
resource "google_api_gateway_api_config" "config" {
  provider      = google-beta
  api           = google_api_gateway_api.api.api_id
  api_config_id = "${var.api_name}-config"
  project       = var.project_id

  openapi_documents {
    document {
      path = "spec.yaml"
      contents = base64encode(templatefile("${path.module}/openapi.yaml", {
        project_id   = var.project_id
        api_name     = var.api_name
        gke_endpoint = var.gke_endpoint
      }))
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

# API Gateway
resource "google_api_gateway_gateway" "gateway" {
  provider   = google-beta
  api_config = google_api_gateway_api_config.config.id
  gateway_id = "${var.api_name}-gateway"
  project    = var.project_id
  region     = var.region
}

# Outputs
output "gateway_url" {
  value = google_api_gateway_gateway.gateway.default_hostname
}

output "api_id" {
  value = google_api_gateway_api.api.api_id
}
