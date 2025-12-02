# Prerequisites Guide

This document outlines all the prerequisites needed to deploy the Instagram Clone application on Google Cloud Platform.

## 1. Local Development Environment

### Required Software

| Software | Minimum Version | Installation |
|----------|-----------------|--------------|
| Git | 2.40+ | `sudo apt install git` |
| Docker | 24.0+ | [Docker Install](https://docs.docker.com/engine/install/) |
| Docker Compose | 2.20+ | Included with Docker Desktop |
| Node.js | 20.x LTS | `nvm install 20` |
| npm | 10.x | Included with Node.js |
| Java JDK | 21 LTS | `sdk install java 21-tem` |
| Maven | 3.9+ | `sdk install maven` |
| Terraform | 1.7+ | [Terraform Install](https://developer.hashicorp.com/terraform/downloads) |
| kubectl | 1.32+ | [kubectl Install](https://kubernetes.io/docs/tasks/tools/) |
| Kustomize | 5.0+ | `kubectl kustomize` (built-in) |
| gcloud CLI | Latest | [gcloud Install](https://cloud.google.com/sdk/docs/install) |

### Recommended Tools

| Tool | Purpose |
|------|---------|
| VS Code | IDE with extensions |
| Lens | Kubernetes IDE |
| Postman | API testing |
| jq | JSON processing |

## 2. Google Cloud Platform Setup

### 2.1 GCP Project

```bash
# Create a new project
gcloud projects create instagram-clone-project1 --name="Instagram Clone"

# Set as default project
gcloud config set project instagram-clone-project1

# Link billing account
gcloud billing projects link instagram-clone-project1 --billing-account=YOUR_BILLING_ACCOUNT_ID
```

### 2.2 Enable Required APIs

```bash
gcloud services enable \
  container.googleapis.com \
  containerregistry.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com \
  compute.googleapis.com \
  servicenetworking.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  iap.googleapis.com \
  logging.googleapis.com \
  monitoring.googleapis.com \
  apigateway.googleapis.com \
  servicemanagement.googleapis.com \
  servicecontrol.googleapis.com
```

### 2.3 Service Account for Terraform

```bash
# Create service account
gcloud iam service-accounts create terraform-sa \
  --display-name="Terraform Service Account"

# Grant required roles
ROLES=(
  "roles/container.admin"
  "roles/compute.admin"
  "roles/iam.serviceAccountAdmin"
  "roles/iam.serviceAccountUser"
  "roles/resourcemanager.projectIamAdmin"
  "roles/secretmanager.admin"
  "roles/cloudsql.admin"
  "roles/redis.admin"
  "roles/storage.admin"
  "roles/servicenetworking.networksAdmin"
  "roles/apigateway.admin"
)

for role in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding instagram-clone-project1 \
    --member="serviceAccount:terraform-sa@instagram-clone-project1.iam.gserviceaccount.com" \
    --role="$role"
done

# Create and download key
gcloud iam service-accounts keys create terraform-key.json \
  --iam-account=terraform-sa@instagram-clone-project1.iam.gserviceaccount.com

# Set environment variable
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/terraform-key.json"
```

### 2.4 GCP Quotas

Ensure the following quotas are sufficient:

| Resource | Minimum Required |
|----------|------------------|
| CPUs (all regions) | 24 |
| In-use IP addresses | 10 |
| Persistent Disk SSD (GB) | 500 |
| VPC Networks | 2 |
| Subnetworks | 5 |
| Firewall Rules | 20 |
| Cloud SQL instances | 1 |
| Redis instances | 1 |
| GKE clusters | 1 |

Check quotas:
```bash
gcloud compute project-info describe --format="table(quotas)"
```

## 3. Network Requirements

### 3.1 IP Address Ranges

The following CIDR ranges are used:

| Network | CIDR | Purpose |
|---------|------|---------|
| VPC | 10.0.0.0/16 | Main VPC |
| GKE Nodes | 10.0.0.0/20 | GKE node subnet |
| GKE Pods | 10.4.0.0/14 | Pod IP range |
| GKE Services | 10.8.0.0/20 | Service IP range |
| Cloud SQL | 10.100.0.0/24 | Private SQL connection |
| Redis | 10.101.0.0/24 | Private Redis connection |

### 3.2 Firewall Rules

Required firewall rules (managed by Terraform):
- Allow internal communication within VPC
- Allow GKE master to node communication
- Allow health check probes
- Allow ingress from load balancer

## 4. Domain and SSL

### 4.1 Domain Setup

1. Register a domain or use existing domain
2. Create Cloud DNS zone:
```bash
gcloud dns managed-zones create instagram-zone \
  --dns-name="instagram.example.com." \
  --description="Instagram Clone DNS Zone"
```

3. Configure DNS records after deployment

### 4.2 SSL Certificate

GCP Managed Certificates are used. Configure domain in:
- `k8s/base/ingress/ingress.yaml`

## 5. Secret Management

### 5.1 Create Required Secrets

```bash
# JWT Secret
echo -n "$(openssl rand -base64 64)" | \
  gcloud secrets create jwt-secret --data-file=-

# Database Passwords
for db in auth user post comment like; do
  echo -n "$(openssl rand -base64 32)" | \
    gcloud secrets create ${db}-db-password --data-file=-
done

# Redis Password
echo -n "$(openssl rand -base64 32)" | \
  gcloud secrets create redis-password --data-file=-
```

### 5.2 External Secrets Operator

Install in GKE cluster:
```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  --namespace external-secrets --create-namespace
```

## 6. Container Registry

### 6.1 Configure Docker Authentication

```bash
gcloud auth configure-docker gcr.io
```

### 6.2 Alternative: Artifact Registry

```bash
# Create repository
gcloud artifacts repositories create instagram-clone \
  --repository-format=docker \
  --location=us-central1

# Configure authentication
gcloud auth configure-docker us-central1-docker.pkg.dev
```

## 7. Kubernetes Tools

### 7.1 Install kubectl Plugins

```bash
# Install krew (kubectl plugin manager)
(
  set -x; cd "$(mktemp -d)" &&
  OS="$(uname | tr '[:upper:]' '[:lower:]')" &&
  ARCH="$(uname -m | sed -e 's/x86_64/amd64/' -e 's/arm.*$/arm/')" &&
  curl -fsSLO "https://github.com/kubernetes-sigs/krew/releases/latest/download/krew-${OS}_${ARCH}.tar.gz" &&
  tar zxvf "krew-${OS}_${ARCH}.tar.gz" &&
  KREW=./krew-"${OS}_${ARCH}" &&
  "$KREW" install krew
)

# Install useful plugins
kubectl krew install ctx ns neat
```

### 7.2 Connect to GKE Cluster

After cluster creation:
```bash
gcloud container clusters get-credentials instagram-clone-gke \
  --region us-central1 \
  --project instagram-clone-project1
```

## 8. CI/CD Requirements

### 8.1 Jenkins Setup

Required Jenkins plugins:
- Kubernetes
- Pipeline
- Git
- Docker Pipeline
- Google Cloud SDK
- SonarQube Scanner
- OWASP Dependency-Check
- JaCoCo

### 8.2 Jenkins Credentials

Create the following credentials in Jenkins:
- `gcp-project-id`: Secret text with GCP project ID
- `gcp-service-account-key`: Secret file with service account JSON
- `sonar-host-url`: SonarQube server URL
- `sonar-token`: SonarQube authentication token

### 8.3 ArgoCD Setup

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Get initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

## 9. Monitoring Prerequisites

### 9.1 Prometheus & Grafana

Deployed via Kubernetes manifests in `monitoring/` directory.

### 9.2 GCP Operations Suite

Enable Cloud Monitoring and Logging:
```bash
gcloud services enable monitoring.googleapis.com logging.googleapis.com
```

## 10. Local Development

### 10.1 Environment Variables

Create `.env` file for local development:
```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=localdev

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=local-development-secret-key
JWT_EXPIRATION=86400000

# GCS
GCS_BUCKET_NAME=local-media-bucket
```

### 10.2 Docker Compose for Local Development

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_PASSWORD: localdev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

## Validation Checklist

Before proceeding with deployment, verify:

- [ ] GCP project created and billing enabled
- [ ] All required APIs enabled
- [ ] Terraform service account created with proper roles
- [ ] Sufficient quotas available
- [ ] Docker and gcloud authenticated
- [ ] kubectl installed and configured
- [ ] All secrets created in Secret Manager
- [ ] Domain configured (if using custom domain)
- [ ] CI/CD tools configured (Jenkins/ArgoCD)

## Next Steps

After completing all prerequisites:
1. Review [EXECUTION-GUIDE.md](./docs/EXECUTION-GUIDE.md) for deployment steps
2. Check [TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) for common issues
