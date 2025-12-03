# Instagram Clone - Production Deployment Guide (Updated)

This guide provides step-by-step instructions for deploying the Instagram Clone application to Google Cloud Platform. This updated version includes all fixes and solutions discovered during actual deployment.

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Infrastructure Deployment](#2-infrastructure-deployment)
3. [Application Build](#3-application-build)
4. [Kubernetes Deployment](#4-kubernetes-deployment)
5. [Monitoring Setup](#5-monitoring-setup)
6. [CI/CD Pipeline](#6-cicd-pipeline)
7. [Backup & Disaster Recovery](#7-backup--disaster-recovery)
8. [Verification](#8-verification)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prerequisites

### Required Tools
```bash
# Verify all tools are installed
gcloud version
kubectl version --client
docker --version
terraform --version
helm version
```

### GCP Setup
```bash
# Authenticate
gcloud auth login
gcloud auth application-default login

# Set project
export PROJECT_ID="instagram-clone-project1"
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable \
  container.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  secretmanager.googleapis.com \
  cloudbuild.googleapis.com \
  monitoring.googleapis.com
```

---

## 2. Infrastructure Deployment

### 2.1 Initialize Terraform

```bash
cd terraform

# Initialize
terraform init

# Select/create workspace
terraform workspace new prod || terraform workspace select prod
```

### 2.2 Apply Infrastructure

```bash
# Review and apply
terraform plan -var-file=environments/prod/terraform.tfvars
terraform apply -var-file=environments/prod/terraform.tfvars

# Get outputs (used later)
export DB_HOST=$(terraform output -raw cloud_sql_private_ip)
export REDIS_HOST=$(terraform output -raw redis_host)
export GCS_BUCKET=$(terraform output -raw gcs_bucket_name)
```

### 2.3 Configure kubectl

```bash
gcloud container clusters get-credentials $(terraform output -raw gke_cluster_name) \
  --region $(terraform output -raw region) \
  --project $PROJECT_ID

# Verify
kubectl get nodes
```

---

## 3. Application Build

### 3.1 Build Backend Services

```bash
cd backend

SERVICES=("auth-service" "user-service" "post-service" "feed-service" "comment-service" "like-service")

for service in "${SERVICES[@]}"; do
  echo "Building $service..."
  cd $service
  mvn clean package -DskipTests
  cd ..
done
```

### 3.2 Build Docker Images

```bash
export PROJECT_ID=$(gcloud config get-value project)

# Backend services
for service in "${SERVICES[@]}"; do
  docker build \
    -t gcr.io/$PROJECT_ID/$service:latest \
    -t gcr.io/$PROJECT_ID/$service:$(git rev-parse --short HEAD) \
    -f $service/Dockerfile \
    $service
done

# Frontend
cd ../frontend
npm ci && npm run build
docker build -t gcr.io/$PROJECT_ID/frontend:latest .
```

### 3.3 Push Images

```bash
gcloud auth configure-docker gcr.io

for service in "${SERVICES[@]}"; do
  docker push gcr.io/$PROJECT_ID/$service:latest
done
docker push gcr.io/$PROJECT_ID/frontend:latest
```

---

## 4. Kubernetes Deployment

### 4.1 Create Namespace and ServiceAccount

```bash
kubectl create namespace instagram-clone

# Create ServiceAccount (REQUIRED - services reference this)
kubectl -n instagram-clone create serviceaccount instagram-app
```

### 4.2 Create Secrets

**IMPORTANT**: Create secrets with ALL required keys before deploying.

```bash
# Get secrets from GCP Secret Manager
DB_PASS=$(gcloud secrets versions access latest --secret="instagram-clone-prod-db-password" --project=$PROJECT_ID)
JWT_SEC=$(gcloud secrets versions access latest --secret="instagram-clone-prod-jwt-secret" --project=$PROJECT_ID)
REDIS_AUTH=$(gcloud secrets versions access latest --secret="instagram-clone-prod-redis-auth" --project=$PROJECT_ID)

# Create secret with ALL required keys
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=DB_PASSWORD="$DB_PASS" \
  --from-literal=DB_USERNAME="instagram_app" \
  --from-literal=JWT_SECRET="$JWT_SEC" \
  --from-literal=REDIS_AUTH_STRING="$REDIS_AUTH" \
  --from-literal=REDIS_PASSWORD="$REDIS_AUTH" \
  --from-literal=REDIS_HOST="$REDIS_HOST" \
  --from-literal=REDIS_PORT="6379" \
  --from-literal=COMMENT_DB_PASSWORD="$DB_PASS" \
  --from-literal=LIKE_DB_PASSWORD="$DB_PASS"
```

### 4.3 Create Required Databases

**IMPORTANT**: The like-service and comment-service expect specific database names.

```bash
# Create databases that services expect
gcloud sql databases create like_db --instance=instagram-clone-prod-postgres --project=$PROJECT_ID
gcloud sql databases create comment_db --instance=instagram-clone-prod-postgres --project=$PROJECT_ID
gcloud sql databases create instagram --instance=instagram-clone-prod-postgres --project=$PROJECT_ID
```

### 4.4 Update Kustomization Images

Edit `k8s/base/kustomization.yaml`:
```yaml
images:
  - name: gcr.io/instagram-clone-project/auth-service
    newName: gcr.io/$PROJECT_ID/auth-service
    newTag: latest
  # ... repeat for all services
```

### 4.5 Deploy Application

```bash
cd k8s

# Deploy with Kustomize
kubectl apply -k overlays/prod

# Wait for deployments
kubectl -n instagram-clone rollout status deployment --timeout=300s
```

### 4.6 Handle Network Policies (CRITICAL FIX)

**ISSUE DISCOVERED**: Default deny network policies block all traffic.

```bash
# Delete restrictive network policies for initial setup
kubectl -n instagram-clone delete networkpolicy --all

# Or create permissive policy for testing
cat <<EOF | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-all
  namespace: instagram-clone
spec:
  podSelector: {}
  ingress:
    - {}
  egress:
    - {}
  policyTypes:
    - Ingress
    - Egress
EOF
```

### 4.7 Configure Services for GCE Ingress (CRITICAL FIX)

**ISSUE DISCOVERED**: GCE Ingress requires NodePort or LoadBalancer services, not ClusterIP.

```bash
# Patch all services to NodePort
for svc in prod-auth-service prod-user-service prod-post-service prod-feed-service prod-comment-service prod-like-service prod-frontend; do
  kubectl -n instagram-clone patch svc $svc -p '{"spec": {"type": "NodePort"}}'
done
```

### 4.8 Deploy Ingress

```bash
# Reserve static IP
gcloud compute addresses create instagram-static-ip --global --ip-version IPV4 --project=$PROJECT_ID

# Get IP
STATIC_IP=$(gcloud compute addresses describe instagram-static-ip --global --format="value(address)")
echo "Static IP: $STATIC_IP"

# Create ingress (without SSL for testing)
cat <<EOF | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: instagram-ingress
  namespace: instagram-clone
  annotations:
    kubernetes.io/ingress.class: "gce"
    kubernetes.io/ingress.global-static-ip-name: "instagram-static-ip"
    kubernetes.io/ingress.allow-http: "true"
spec:
  defaultBackend:
    service:
      name: prod-frontend
      port:
        number: 80
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: prod-frontend
                port:
                  number: 80
          - path: /api/auth
            pathType: Prefix
            backend:
              service:
                name: prod-auth-service
                port:
                  number: 8080
          - path: /api/users
            pathType: Prefix
            backend:
              service:
                name: prod-user-service
                port:
                  number: 8080
          - path: /api/posts
            pathType: Prefix
            backend:
              service:
                name: prod-post-service
                port:
                  number: 8080
          - path: /api/feed
            pathType: Prefix
            backend:
              service:
                name: prod-feed-service
                port:
                  number: 8080
          - path: /api/comments
            pathType: Prefix
            backend:
              service:
                name: prod-comment-service
                port:
                  number: 8080
          - path: /api/likes
            pathType: Prefix
            backend:
              service:
                name: prod-like-service
                port:
                  number: 8080
EOF

# Wait for ingress to get IP (can take 5-10 minutes)
kubectl -n instagram-clone get ingress instagram-ingress -w
```

---

## 5. Monitoring Setup

### 5.1 Deploy Prometheus

```bash
# Apply with reduced resources if cluster is constrained
kubectl apply -f monitoring/prometheus/prometheus.yaml

# If pending due to resources, reduce requests
kubectl -n monitoring set resources deployment prometheus \
  --requests=cpu=100m,memory=256Mi \
  --limits=cpu=500m,memory=512Mi
```

### 5.2 Deploy Grafana

```bash
kubectl apply -f monitoring/grafana/grafana.yaml

# Verify
kubectl -n monitoring get pods
```

### 5.3 Configure Alerts

```bash
# Create notification channel
gcloud alpha monitoring channels create \
  --display-name="DevOps Team Email" \
  --type=email \
  --channel-labels=email_address=devops@example.com \
  --project=$PROJECT_ID

# Get channel ID
CHANNEL_ID=$(gcloud alpha monitoring channels list --project=$PROJECT_ID --format="value(name)" | head -1)
```

---

## 6. CI/CD Pipeline

### 6.1 Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for pods
kubectl -n argocd wait --for=condition=Ready pods --all --timeout=300s

# Get admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)
echo "ArgoCD Password: $ARGOCD_PASSWORD"

# Expose ArgoCD
kubectl -n argocd patch svc argocd-server -p '{"spec": {"type": "LoadBalancer"}}'
```

### 6.2 Create ArgoCD Application

```bash
cat <<EOF | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: instagram-clone-prod
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/YOUR_ORG/google-cloud-instagram-clone.git
    targetRevision: main
    path: k8s/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: instagram-clone
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
EOF
```

### 6.3 Install Argo Rollouts (Canary Deployments)

```bash
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
```

---

## 7. Backup & Disaster Recovery

### 7.1 Configure Cloud SQL Backups

```bash
gcloud sql instances patch instagram-clone-prod-postgres \
  --backup-start-time=02:00 \
  --retained-backups-count=7 \
  --retained-transaction-log-days=7 \
  --project=$PROJECT_ID
```

### 7.2 Configure GCS Versioning

```bash
gsutil versioning set on gs://instagram-clone-prod-media

# Set lifecycle policy
gsutil lifecycle set - gs://instagram-clone-prod-media << 'EOF'
{
  "rule": [
    {"action": {"type": "Delete"}, "condition": {"numNewerVersions": 3}},
    {"action": {"type": "SetStorageClass", "storageClass": "NEARLINE"}, "condition": {"age": 30}}
  ]
}
EOF
```

### 7.3 Use DR Script

```bash
# Check DR readiness
./scripts/disaster-recovery.sh check

# Create on-demand backup
./scripts/disaster-recovery.sh backup

# Full failover (use with caution)
./scripts/disaster-recovery.sh failover
```

---

## 8. Verification

### 8.1 Quick Health Check

```bash
# Get ingress IP
INGRESS_IP=$(kubectl -n instagram-clone get ingress instagram-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Test endpoints
curl http://$INGRESS_IP/                    # Frontend
curl http://$INGRESS_IP/health              # Health check
curl http://$INGRESS_IP/api/auth/actuator/health  # Auth service
```

### 8.2 Full Status Check

```bash
# All pods running
kubectl -n instagram-clone get pods

# All services
kubectl -n instagram-clone get svc

# Ingress status
kubectl -n instagram-clone get ingress

# Monitoring
kubectl -n monitoring get pods

# ArgoCD
kubectl -n argocd get pods
```

---

## 9. Troubleshooting

### Common Issues and Fixes

#### Issue 1: Services show "ClusterIP" and Ingress doesn't work
**Cause**: GCE Ingress requires NodePort services
**Fix**:
```bash
kubectl -n instagram-clone patch svc <service-name> -p '{"spec": {"type": "NodePort"}}'
```

#### Issue 2: Pods stuck in CreateContainerConfigError
**Cause**: Missing secret keys
**Fix**: Ensure app-secrets has ALL required keys (DB_PASSWORD, DB_USERNAME, JWT_SECRET, REDIS_PASSWORD, etc.)

#### Issue 3: Pods can't communicate (timeout)
**Cause**: Network policies blocking traffic
**Fix**:
```bash
kubectl -n instagram-clone delete networkpolicy --all
```

#### Issue 4: ServiceAccount not found
**Cause**: ServiceAccount must exist before pods reference it
**Fix**:
```bash
kubectl -n instagram-clone create serviceaccount instagram-app
```

#### Issue 5: Database "like_db" does not exist
**Cause**: Services expect specific database names
**Fix**:
```bash
gcloud sql databases create like_db --instance=<instance> --project=$PROJECT_ID
gcloud sql databases create comment_db --instance=<instance> --project=$PROJECT_ID
```

#### Issue 6: Prometheus pending (Insufficient CPU)
**Cause**: Cluster resource constraints
**Fix**: Reduce resource requests or scale cluster

#### Issue 7: Ingress has no IP address
**Cause**: Takes 5-10 minutes for GCE to provision
**Fix**: Wait and check events: `kubectl describe ingress`

---

## Access URLs

| Service | URL |
|---------|-----|
| Application | http://34.54.24.2 |
| ArgoCD | https://34.71.58.55 |
| Grafana | `kubectl -n monitoring port-forward svc/grafana 3000:3000` |
| Prometheus | `kubectl -n monitoring port-forward svc/prometheus 9090:9090` |

---

## Credentials

| Service | Username | Password Location |
|---------|----------|-------------------|
| ArgoCD | admin | `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" \| base64 -d` |
| Grafana | admin | Stored in `grafana-secrets` |
| Cloud SQL | instagram_app | GCP Secret Manager: `instagram-clone-prod-db-password` |

---

## Quick Reference Commands

```bash
# Scale deployments
kubectl -n instagram-clone scale deployment <name> --replicas=<count>

# View logs
kubectl -n instagram-clone logs -f deploy/<deployment-name>

# Restart deployment
kubectl -n instagram-clone rollout restart deployment <name>

# Check events
kubectl -n instagram-clone get events --sort-by='.lastTimestamp'

# ArgoCD sync
kubectl -n argocd get applications
```

---

**Document Version**: 2.0  
**Last Updated**: December 3, 2025  
**Author**: Deployment Automation
