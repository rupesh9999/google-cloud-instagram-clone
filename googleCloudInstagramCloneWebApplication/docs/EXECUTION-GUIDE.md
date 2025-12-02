# Execution Guide

This guide provides step-by-step instructions for deploying the Instagram Clone application to Google Cloud Platform.

## Table of Contents

1. [Infrastructure Deployment](#1-infrastructure-deployment)
2. [Application Build](#2-application-build)
3. [Kubernetes Deployment](#3-kubernetes-deployment)
4. [Post-Deployment Configuration](#4-post-deployment-configuration)
5. [Verification](#5-verification)

---

## 1. Infrastructure Deployment

### 1.1 Initialize Terraform

```bash
cd terraform

# Initialize Terraform
terraform init

# Select workspace (dev/staging/prod)
terraform workspace new dev
# or
terraform workspace select dev
```

### 1.2 Configure Variables

Edit `environments/dev/terraform.tfvars`:

```hcl
project_id = "your-gcp-project-id"
region     = "us-central1"
environment = "dev"

# Network
vpc_cidr = "10.0.0.0/16"

# GKE
gke_node_count = 3
gke_machine_type = "e2-standard-4"

# Cloud SQL
db_tier = "db-custom-2-4096"
db_disk_size = 20

# Redis
redis_memory_size_gb = 1
```

### 1.3 Plan and Apply

```bash
# Review the plan
terraform plan -var-file=environments/dev/terraform.tfvars

# Apply infrastructure
terraform apply -var-file=environments/dev/terraform.tfvars

# Save outputs
terraform output -json > outputs.json
```

### 1.4 Verify Infrastructure

```bash
# Get GKE credentials
gcloud container clusters get-credentials $(terraform output -raw gke_cluster_name) \
  --region $(terraform output -raw region) \
  --project $(terraform output -raw project_id)

# Verify cluster
kubectl get nodes
kubectl cluster-info
```

---

## 2. Application Build

### 2.1 Build Backend Services

```bash
cd backend

# Build all services
Yes, you are correct; the `mvn clean package -DskipTests` command needs to be executed in each backend service directory to build all services.

```bash
SERVICES=("auth-service" "user-service" "post-service" "feed-service" "comment-service" "like-service")

for service in "${SERVICES[@]}"; do
  cd $service
  mvn clean package -DskipTests
  cd ..
done
```

# Run tests (optional but recommended)
mvn test

# Verify JARs created
ls -la */target/*.jar
```

### 2.2 Build Docker Images

```bash
export PROJECT_ID=$(gcloud config get-value project)
export REGION=us-central1

# Backend services
SERVICES=("auth-service" "user-service" "post-service" "feed-service" "comment-service" "like-service")

for service in "${SERVICES[@]}"; do
  echo "Building $service..."
  docker build \
    -t gcr.io/$PROJECT_ID/$service:latest \
    -t gcr.io/$PROJECT_ID/$service:$(git rev-parse --short HEAD) \
    -f $service/Dockerfile \
    $service
done

# Frontend
cd ../frontend
npm ci
npm run build

docker build \
  -t gcr.io/$PROJECT_ID/frontend:latest \
  -t gcr.io/$PROJECT_ID/frontend:$(git rev-parse --short HEAD) \
  .
```

### 2.3 Push Images to Container Registry

```bash
# Configure Docker for GCR
gcloud auth configure-docker gcr.io

# Push all images
for service in "${SERVICES[@]}"; do
  docker push gcr.io/$PROJECT_ID/$service:latest
  docker push gcr.io/$PROJECT_ID/$service:$(git rev-parse --short HEAD)
done

docker push gcr.io/$PROJECT_ID/frontend:latest
docker push gcr.io/$PROJECT_ID/frontend:$(git rev-parse --short HEAD)
```

---

## 3. Kubernetes Deployment

### 3.1 Install External Secrets Operator

```bash
# Add Helm repository
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

# Install External Secrets Operator
helm install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --create-namespace \
  --set installCRDs=true

# Wait for deployment
kubectl -n external-secrets rollout status deployment/external-secrets
```

### 3.2 Configure Kustomize

Update image references in the appropriate overlay:

```bash
cd k8s/overlays/dev

# Update image tags
kustomize edit set image \
  gcr.io/instagram-clone-project/auth-service=gcr.io/$PROJECT_ID/auth-service:latest \
  gcr.io/instagram-clone-project/user-service=gcr.io/$PROJECT_ID/user-service:latest \
  gcr.io/instagram-clone-project/post-service=gcr.io/$PROJECT_ID/post-service:latest \
  gcr.io/instagram-clone-project/feed-service=gcr.io/$PROJECT_ID/feed-service:latest \
  gcr.io/instagram-clone-project/comment-service=gcr.io/$PROJECT_ID/comment-service:latest \
  gcr.io/instagram-clone-project/like-service=gcr.io/$PROJECT_ID/like-service:latest \
  gcr.io/instagram-clone-project/frontend=gcr.io/$PROJECT_ID/frontend:latest

kustomize edit set image \
  gcr.io/instagram-clone-project1/auth-service=gcr.io/$PROJECT_ID/auth-service:latest \
  gcr.io/instagram-clone-project1/user-service=gcr.io/$PROJECT_ID/user-service:latest \
  gcr.io/instagram-clone-project1/post-service=gcr.io/$PROJECT_ID/post-service:latest \
  gcr.io/instagram-clone-project1/feed-service=gcr.io/$PROJECT_ID/feed-service:latest \
  gcr.io/instagram-clone-project1/comment-service=gcr.io/$PROJECT_ID/comment-service:latest \
  gcr.io/instagram-clone-project1/like-service=gcr.io/$PROJECT_ID/like-service:latest \
  gcr.io/instagram-clone-project1/frontend=gcr.io/$PROJECT_ID/frontend:latest
```

### 3.3 Update ConfigMaps

Edit `k8s/base/configmaps/app-config.yaml` with Terraform outputs:

```bash
# Get Terraform outputs
cd ../../../terraform

DB_HOST=$(terraform output -raw cloud_sql_private_ip)
REDIS_HOST=$(terraform output -raw redis_host)
GCS_BUCKET=$(terraform output -raw gcs_bucket_name)

# Update ConfigMap
cd ../k8s/base/configmaps

cat > app-config.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: instagram-clone
data:
  DB_HOST: "${DB_HOST}"
  REDIS_HOST: "${REDIS_HOST}"
  REDIS_PORT: "6379"
  GCS_BUCKET_NAME: "${GCS_BUCKET}"
  # ... rest of config
EOF
```

### 3.4 Deploy Application

```bash
cd k8s

# Deploy to dev environment
kubectl apply -k overlays/dev

# Monitor deployment
kubectl -n instagram-clone get pods -w

# Wait for all deployments to be ready
kubectl -n instagram-clone rollout status deployment/dev-auth-service
kubectl -n instagram-clone rollout status deployment/dev-user-service
kubectl -n instagram-clone rollout status deployment/dev-post-service
kubectl -n instagram-clone rollout status deployment/dev-feed-service
kubectl -n instagram-clone rollout status deployment/dev-comment-service
kubectl -n instagram-clone rollout status deployment/dev-like-service
kubectl -n instagram-clone rollout status deployment/dev-frontend
```

### 3.5 Deploy Ingress

```bash
# Reserve static IP
gcloud compute addresses create instagram-static-ip \
  --global \
  --ip-version IPV4

# Get the IP address
gcloud compute addresses describe instagram-static-ip --global

# Apply ingress
kubectl apply -f base/ingress/ingress.yaml

# Check ingress status
kubectl -n instagram-clone get ingress
```

---

## 4. Post-Deployment Configuration

### 4.1 Configure DNS

```bash
INGRESS_IP=$(gcloud compute addresses describe instagram-static-ip --global --format="value(address)")

# Create DNS record
gcloud dns record-sets create instagram.example.com. \
  --zone=instagram-zone \
  --type=A \
  --ttl=300 \
  --rrdatas=$INGRESS_IP
```

### 4.2 Wait for SSL Certificate

```bash
# Check managed certificate status
kubectl -n instagram-clone get managedcertificate instagram-managed-cert -w

# Certificate provisioning can take 10-30 minutes
```

### 4.3 Deploy Monitoring Stack

```bash
# Deploy Prometheus
kubectl apply -f monitoring/prometheus/prometheus.yaml

# Deploy Grafana
kubectl apply -f monitoring/grafana/grafana.yaml

# Access Grafana
kubectl -n monitoring port-forward svc/grafana 3000:3000
```

### 4.4 Configure Alerts

```bash
# Create notification channel
gcloud alpha monitoring channels create \
  --display-name="DevOps Team" \
  --type=email \
  --channel-labels=email_address=devops@example.com

# Import alerting policies
gcloud alpha monitoring policies create \
  --policy-from-file=monitoring/alerting-policies.yaml
```

---

## 5. Verification

### 5.1 Health Checks

```bash
# Check all pods are running
kubectl -n instagram-clone get pods

# Check all services
kubectl -n instagram-clone get services

# Check HPA status
kubectl -n instagram-clone get hpa

# Check ingress
kubectl -n instagram-clone describe ingress instagram-ingress
```

### 5.2 API Health

```bash
# Port-forward to test locally
kubectl -n instagram-clone port-forward svc/dev-auth-service 8080:8080 &

# Test health endpoints
curl http://localhost:8080/actuator/health

# Test via ingress (after DNS propagation)
curl https://instagram.example.com/api/auth/health
```

### 5.3 Database Connectivity

```bash
# Check database connection from auth-service
kubectl -n instagram-clone exec -it deploy/dev-auth-service -- \
  curl localhost:8080/actuator/health/db
```

### 5.4 Redis Connectivity

```bash
# Check Redis connection from feed-service
kubectl -n instagram-clone exec -it deploy/dev-feed-service -- \
  curl localhost:8080/actuator/health/redis
```

### 5.5 Metrics

```bash
# Port-forward Prometheus
kubectl -n monitoring port-forward svc/prometheus 9090:9090 &

# Open in browser
# http://localhost:9090

# Check targets
# http://localhost:9090/targets
```

### 5.6 Logs

```bash
# View logs for all pods
kubectl -n instagram-clone logs -l app.kubernetes.io/part-of=instagram-clone --tail=100

# View logs for specific service
kubectl -n instagram-clone logs -l app=auth-service -f

# Check Cloud Logging
gcloud logging read 'resource.type="k8s_container" AND resource.labels.namespace_name="instagram-clone"' \
  --limit=50
```

---

## Deployment Checklist

### Pre-Deployment
- [ ] All prerequisites completed
- [ ] Terraform variables configured
- [ ] Secrets created in Secret Manager
- [ ] Docker authenticated to GCR

### Infrastructure
- [ ] VPC created
- [ ] GKE cluster running
- [ ] Cloud SQL instance available
- [ ] Memorystore Redis available
- [ ] GCS bucket created
- [ ] IAM service accounts configured

### Application
- [ ] Backend services built
- [ ] Frontend built
- [ ] All Docker images pushed
- [ ] External Secrets Operator installed
- [ ] ConfigMaps updated
- [ ] Deployments running
- [ ] Services exposed
- [ ] Ingress configured
- [ ] SSL certificate provisioned

### Post-Deployment
- [ ] DNS configured
- [ ] Health checks passing
- [ ] Metrics being collected
- [ ] Logs accessible
- [ ] Alerts configured

---

## Rollback Procedure

### Application Rollback

```bash
# Rollback to previous revision
kubectl -n instagram-clone rollout undo deployment/dev-auth-service

# Rollback to specific revision
kubectl -n instagram-clone rollout undo deployment/dev-auth-service --to-revision=2

# Check rollout history
kubectl -n instagram-clone rollout history deployment/dev-auth-service
```

### Infrastructure Rollback

```bash
cd terraform

# Show previous state
terraform state list

# Rollback to previous state
terraform apply -target=module.gke -var-file=environments/dev/terraform.tfvars

# For complete rollback
terraform destroy -var-file=environments/dev/terraform.tfvars
```

---

## Next Steps

1. Set up CI/CD pipeline with Jenkins/ArgoCD
2. Configure additional environments (staging, prod)
3. Implement canary deployments
4. Set up backup procedures
5. Configure disaster recovery

For troubleshooting, see [TROUBLESHOOTING.md](./TROUBLESHOOTING.md).
