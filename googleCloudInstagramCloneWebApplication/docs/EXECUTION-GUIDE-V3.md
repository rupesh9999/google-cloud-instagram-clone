# Instagram Clone - Production Deployment Guide v3.0

This comprehensive guide documents the complete deployment process for the Instagram Clone application on Google Cloud Platform, including all critical fixes discovered during production deployment.

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Infrastructure Deployment](#2-infrastructure-deployment)
3. [Application Build](#3-application-build)
4. [Kubernetes Deployment](#4-kubernetes-deployment)
5. [Critical Configuration Fixes](#5-critical-configuration-fixes)
6. [Monitoring Setup](#6-monitoring-setup)
7. [CI/CD Pipeline](#7-cicd-pipeline)
8. [Backup & Disaster Recovery](#8-backup--disaster-recovery)
9. [Feature Status](#9-feature-status)
10. [Verification](#10-verification)
11. [Troubleshooting](#11-troubleshooting)
12. [Access Information](#12-access-information)

---

## 1. Prerequisites

### Required Tools
```bash
# Verify all tools are installed
gcloud version          # Google Cloud SDK
kubectl version --client # Kubernetes CLI
docker --version        # Container runtime
terraform --version     # Infrastructure as Code (1.7+)
helm version            # Kubernetes package manager
java -version          # Java 21 LTS
mvn -version           # Maven 3.9+
node --version         # Node.js 20+
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
  monitoring.googleapis.com \
  servicenetworking.googleapis.com
```

---

## 2. Infrastructure Deployment

### 2.1 Initialize Terraform

```bash
cd terraform

# Initialize Terraform
terraform init

# Select or create workspace
terraform workspace new prod || terraform workspace select prod
```

### 2.2 Configure Production Variables

Edit `terraform/environments/prod/terraform.tfvars`:
```hcl
# GKE Configuration
gke_min_node_count   = 2
gke_max_node_count   = 10
gke_machine_type     = "e2-standard-4"
gke_disk_size_gb     = 100

# Cloud SQL Configuration
cloudsql_tier        = "db-custom-2-4096"
cloudsql_disk_size   = 50

# Redis Configuration
redis_memory_size_gb = 10
redis_version        = "REDIS_7_0"

# GCS Configuration
gcs_location         = "US"
```

### 2.3 Apply Infrastructure

```bash
# Review changes
terraform plan -var-file=environments/prod/terraform.tfvars

# Apply infrastructure
terraform apply -var-file=environments/prod/terraform.tfvars

# Store outputs for later use
export DB_HOST=$(terraform output -raw cloud_sql_private_ip)
export REDIS_HOST=$(terraform output -raw redis_host)
export GCS_BUCKET=$(terraform output -raw gcs_bucket_name)
export GKE_CLUSTER=$(terraform output -raw gke_cluster_name)
export REGION=$(terraform output -raw region)
```

### 2.4 Configure kubectl

```bash
gcloud container clusters get-credentials $GKE_CLUSTER \
  --region $REGION \
  --project $PROJECT_ID

# Verify cluster access
kubectl get nodes
```

---

## 3. Application Build

### 3.1 Build Backend Services

```bash
cd backend

# Build all services
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

### 4.2 Create Required Databases

**IMPORTANT**: Create databases before deploying services.

```bash
INSTANCE_NAME="instagram-clone-prod-postgres"

gcloud sql databases create instagram --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create like_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create comment_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create auth_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create user_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create post_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
gcloud sql databases create feed_db --instance=$INSTANCE_NAME --project=$PROJECT_ID
```

### 4.3 Create Secrets (CRITICAL)

**⚠️ IMPORTANT: JWT_SECRET must be properly base64-encoded!**

```bash
# Generate proper JWT secret (base64-encoded 512-bit key for HS384)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')

# Get Cloud SQL password
DB_PASS=$(gcloud secrets versions access latest --secret="instagram-clone-prod-db-password" --project=$PROJECT_ID)

# Get Redis password
REDIS_AUTH=$(gcloud secrets versions access latest --secret="instagram-clone-prod-redis-auth" --project=$PROJECT_ID)

# Create secret with ALL required keys
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=DB_PASSWORD="$DB_PASS" \
  --from-literal=DB_USERNAME="instagram_app" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=REDIS_AUTH_STRING="$REDIS_AUTH" \
  --from-literal=REDIS_PASSWORD="$REDIS_AUTH" \
  --from-literal=REDIS_HOST="$REDIS_HOST" \
  --from-literal=REDIS_PORT="6379" \
  --from-literal=COMMENT_DB_PASSWORD="$DB_PASS" \
  --from-literal=LIKE_DB_PASSWORD="$DB_PASS"
```

### 4.4 Set Cloud SQL User Password

```bash
gcloud sql users set-password instagram_app \
  --instance=$INSTANCE_NAME \
  --password="$DB_PASS" \
  --project=$PROJECT_ID
```

### 4.5 Deploy Application

```bash
cd k8s

# Apply Kustomize overlay for production
kubectl apply -k overlays/prod

# Wait for deployments
kubectl -n instagram-clone rollout status deployment --timeout=300s
```

### 4.6 Configure Network Policies

```bash
# Delete restrictive default policies
kubectl -n instagram-clone delete networkpolicy --all

# Create permissive policy
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

### 4.7 Patch Services for Ingress

```bash
# GCE Ingress requires NodePort services
for svc in prod-auth-service prod-user-service prod-post-service prod-feed-service prod-comment-service prod-like-service prod-frontend; do
  kubectl -n instagram-clone patch svc $svc -p '{"spec": {"type": "NodePort"}}'
done
```

---

## 5. Critical Configuration Fixes

### 5.1 API Gateway Configuration (CRITICAL)

The frontend calls `/api/v1/*` endpoints but the API gateway must route them correctly to backend services.

**Create/Update API Gateway ConfigMap:**

```bash
cat <<'EOF' | kubectl -n instagram-clone apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-gateway-nginx-config
  namespace: instagram-clone
data:
  nginx.conf: |
    events {
        worker_connections 1024;
    }

    http {
        include       /etc/nginx/mime.types;
        default_type  application/octet-stream;
        
        # Logging
        log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                        '$status $body_bytes_sent "$http_referer" '
                        '"$http_user_agent"';
        access_log /var/log/nginx/access.log main;
        error_log /var/log/nginx/error.log debug;

        # Upstream definitions
        upstream auth_backend {
            server prod-auth-service:8080;
        }
        
        upstream user_backend {
            server prod-user-service:8080;
        }
        
        upstream post_backend {
            server prod-post-service:8080;
        }
        
        upstream feed_backend {
            server prod-feed-service:8080;
        }
        
        upstream comment_backend {
            server prod-comment-service:8080;
        }
        
        upstream like_backend {
            server prod-like-service:8080;
        }

        server {
            listen 80;
            server_name _;
            
            # Health check
            location /health {
                return 200 'OK';
                add_header Content-Type text/plain;
            }
            
            # API v1 Routes - CRITICAL: Preserve /api/v1 prefix
            location /api/v1/auth/ {
                proxy_pass http://auth_backend/api/v1/auth/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            location /api/v1/users/ {
                proxy_pass http://user_backend/api/v1/users/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            location /api/v1/posts/ {
                proxy_pass http://post_backend/api/v1/posts/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            location /api/v1/feed/ {
                proxy_pass http://feed_backend/api/v1/feed/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            location /api/v1/comments/ {
                proxy_pass http://comment_backend/api/v1/comments/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            location /api/v1/likes/ {
                proxy_pass http://like_backend/api/v1/likes/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
            }
            
            # Fallback API routes (without v1)
            location /api/auth/ {
                proxy_pass http://auth_backend/api/v1/auth/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            location /api/users/ {
                proxy_pass http://user_backend/api/v1/users/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            location /api/posts/ {
                proxy_pass http://post_backend/api/v1/posts/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            location /api/feed/ {
                proxy_pass http://feed_backend/api/v1/feed/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            location /api/comments/ {
                proxy_pass http://comment_backend/api/v1/comments/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            location /api/likes/ {
                proxy_pass http://like_backend/api/v1/likes/;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
            
            # Frontend static files
            location / {
                proxy_pass http://prod-frontend:80;
                proxy_http_version 1.1;
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
            }
        }
    }
EOF

# Restart API gateway to pick up changes
kubectl -n instagram-clone rollout restart deployment prod-api-gateway
```

### 5.2 JWT Secret Format (CRITICAL)

**Issue**: The `JwtTokenProvider.java` uses `Decoders.BASE64.decode(jwtSecret)` which requires a valid base64-encoded secret.

**Invalid JWT secrets will contain characters like**: `<`, `>`, `:`, `[`, `]`, etc.

**Solution**: Always generate JWT secrets with:
```bash
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
```

### 5.3 Database Credentials Sync

**Ensure Cloud SQL user password matches Kubernetes secret:**

```bash
# Get password from secret
DB_PASS=$(kubectl -n instagram-clone get secret app-secrets -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)

# Set in Cloud SQL
gcloud sql users set-password instagram_app \
  --instance=instagram-clone-prod-postgres \
  --password="$DB_PASS" \
  --project=$PROJECT_ID
```

### 5.4 Recreate Secrets if Corrupted

```bash
# Delete existing secret
kubectl -n instagram-clone delete secret app-secrets

# Generate new JWT secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')

# Get other credentials
DB_PASS="your_secure_password"
REDIS_AUTH="your_redis_password"

# Recreate secret
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=DB_PASSWORD="$DB_PASS" \
  --from-literal=DB_USERNAME="instagram_app" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=REDIS_AUTH_STRING="$REDIS_AUTH" \
  --from-literal=REDIS_PASSWORD="$REDIS_AUTH" \
  --from-literal=REDIS_HOST="10.133.0.4" \
  --from-literal=REDIS_PORT="6379" \
  --from-literal=COMMENT_DB_PASSWORD="$DB_PASS" \
  --from-literal=LIKE_DB_PASSWORD="$DB_PASS"

# Sync Cloud SQL password
gcloud sql users set-password instagram_app \
  --instance=instagram-clone-prod-postgres \
  --password="$DB_PASS" \
  --project=$PROJECT_ID

# Restart all services to pick up new secrets
kubectl -n instagram-clone rollout restart deployment --all
```

---

## 6. Monitoring Setup

### 6.1 Deploy Prometheus

```bash
kubectl apply -f monitoring/prometheus/prometheus.yaml

# If pending due to resources, reduce requests
kubectl -n monitoring set resources deployment prometheus \
  --requests=cpu=100m,memory=256Mi \
  --limits=cpu=500m,memory=512Mi
```

### 6.2 Deploy Grafana

```bash
kubectl apply -f monitoring/grafana/grafana.yaml

# Verify
kubectl -n monitoring get pods
```

---

## 7. CI/CD Pipeline

### 7.1 Install ArgoCD

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

### 7.2 Create ArgoCD Application

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

---

## 8. Backup & Disaster Recovery

### 8.1 Configure Cloud SQL Backups

```bash
gcloud sql instances patch instagram-clone-prod-postgres \
  --backup-start-time=02:00 \
  --retained-backups-count=7 \
  --retained-transaction-log-days=7 \
  --project=$PROJECT_ID
```

### 8.2 Configure GCS Versioning

```bash
gsutil versioning set on gs://instagram-clone-prod-media

gsutil lifecycle set - gs://instagram-clone-prod-media << 'EOF'
{
  "rule": [
    {"action": {"type": "Delete"}, "condition": {"numNewerVersions": 3}},
    {"action": {"type": "SetStorageClass", "storageClass": "NEARLINE"}, "condition": {"age": 30}}
  ]
}
EOF
```

---

## 9. Feature Status

### 9.1 Implemented Features ✅

| Feature | Service | Status |
|---------|---------|--------|
| User Registration | auth-service | ✅ Working |
| User Login | auth-service | ✅ Working |
| JWT Authentication | common | ✅ Working |
| Create Posts | post-service | ✅ Working |
| View Feed | feed-service | ✅ Working |
| Like Posts | like-service | ✅ Working |
| Comments | comment-service | ✅ Working |
| User Profiles | user-service | ✅ Working |
| Follow/Unfollow | user-service | ✅ Working |

### 9.2 Partially Implemented Features ⚠️

| Feature | Status | Notes |
|---------|--------|-------|
| Image Upload | ⚠️ Backend ready | Frontend UI needs enhancement |
| Video Upload | ⚠️ Backend ready | Frontend UI needs enhancement |
| Search | ⚠️ Basic | No algorithm-based suggestions |
| Explore | ⚠️ Basic | No personalization |
| Notifications | ⚠️ Database ready | Real-time not implemented |

### 9.3 Missing Features ❌

| Feature | Priority | Complexity |
|---------|----------|------------|
| Stories | High | Medium |
| Reels | High | High |
| Direct Messages | High | High |
| Story Highlights | Medium | Low |
| Live Streaming | Low | Very High |
| Shopping/Tags | Low | Medium |

---

## 10. Verification

### 10.1 Test API Endpoints

```bash
# Get application URL
APP_URL="http://34.54.24.2"

# Test health check
curl $APP_URL/health

# Test user registration
curl -X POST $APP_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!",
    "username": "testuser",
    "fullName": "Test User"
  }'

# Test login
curl -X POST $APP_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }'

# Use returned token for authenticated requests
TOKEN="eyJhbGciOiJIUzM4NCJ9..."

# Get user profile
curl $APP_URL/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"

# Create a post
curl -X POST $APP_URL/api/v1/posts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "caption": "Hello Instagram Clone!",
    "imageUrl": "https://example.com/image.jpg"
  }'
```

### 10.2 Check Cluster Status

```bash
# All pods should be Running
kubectl -n instagram-clone get pods

# All services
kubectl -n instagram-clone get svc

# Ingress status
kubectl -n instagram-clone get ingress

# View logs for troubleshooting
kubectl -n instagram-clone logs -f deploy/prod-auth-service
```

---

## 11. Troubleshooting

### Issue 1: 500 Error on Signup

**Symptoms**: Frontend shows "Request failed with status code 500"

**Root Causes & Fixes**:

1. **API Gateway routing mismatch**
   - Frontend calls `/api/v1/auth/*` but gateway routes `/api/auth/*`
   - Fix: Update nginx ConfigMap to handle `/api/v1/*` paths

2. **Invalid JWT_SECRET format**
   - Error: `io.jsonwebtoken.io.DecodingException: Illegal base64 character`
   - Fix: Regenerate with `openssl rand -base64 64`

3. **Database credentials mismatch**
   - Kubernetes secret has different password than Cloud SQL user
   - Fix: Sync passwords using `gcloud sql users set-password`

### Issue 2: 403 Forbidden

**Cause**: Request not reaching correct backend service

**Fix**: Check API gateway configuration and ensure routes are correct

### Issue 3: Pods CrashLoopBackOff

**Check logs**:
```bash
kubectl -n instagram-clone logs <pod-name> --previous
```

**Common causes**:
- Missing environment variables
- Database connection issues
- Invalid secrets

### Issue 4: Services Can't Communicate

**Fix**: Delete restrictive network policies
```bash
kubectl -n instagram-clone delete networkpolicy --all
```

---

## 12. Access Information

### Current Production URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://34.54.24.2 | Create account via signup |
| ArgoCD | http://34.71.58.55 | admin / (see below) |
| Grafana | Port-forward required | admin / (stored in secret) |

### Get ArgoCD Password
```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

### Infrastructure Details

| Resource | Value |
|----------|-------|
| GKE Cluster | instagram-clone-prod-gke |
| Region | us-central1 |
| Cloud SQL IP | 10.133.1.2 |
| Redis IP | 10.133.0.4:6379 |
| Node Pool | 2-10 nodes (e2-standard-4) |

### Database Credentials

| Field | Value |
|-------|-------|
| Username | instagram_app |
| Password | (stored in app-secrets) |
| Databases | instagram, auth_db, user_db, post_db, feed_db, like_db, comment_db |

---

## Quick Commands Reference

```bash
# Scale cluster nodes
gcloud container clusters resize instagram-clone-prod-gke \
  --region=us-central1 \
  --node-pool=instagram-clone-prod-gke-node-pool \
  --num-nodes=3

# Restart all services
kubectl -n instagram-clone rollout restart deployment --all

# View all logs
kubectl -n instagram-clone logs -f deploy/prod-auth-service

# Check secret contents
kubectl -n instagram-clone get secret app-secrets -o yaml

# Port forward for local access
kubectl -n monitoring port-forward svc/grafana 3000:3000
kubectl -n monitoring port-forward svc/prometheus 9090:9090
```

---

**Document Version**: 3.0  
**Last Updated**: $(date +"%B %d, %Y")  
**Changes in v3.0**:
- Added critical API Gateway /api/v1 routing configuration
- Added JWT secret format requirements (base64-encoded)
- Added database credential synchronization steps
- Updated troubleshooting with specific error solutions
- Added feature status matrix
- Added comprehensive verification tests
