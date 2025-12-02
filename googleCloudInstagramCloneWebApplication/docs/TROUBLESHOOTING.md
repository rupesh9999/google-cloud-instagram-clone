# Troubleshooting Guide

This guide provides solutions for common issues encountered when deploying and operating the Instagram Clone application.

## Table of Contents

1. [Infrastructure Issues](#1-infrastructure-issues)
2. [Kubernetes Issues](#2-kubernetes-issues)
3. [Application Issues](#3-application-issues)
4. [Database Issues](#4-database-issues)
5. [Networking Issues](#5-networking-issues)
6. [Monitoring Issues](#6-monitoring-issues)
7. [CI/CD Issues](#7-cicd-issues)

---

## 1. Infrastructure Issues

### 1.1 Terraform Apply Fails

**Symptom**: Terraform apply fails with API errors

**Solutions**:

```bash
# Check if APIs are enabled
gcloud services list --enabled

# Enable missing API
gcloud services enable container.googleapis.com

# Check service account permissions
gcloud projects get-iam-policy $PROJECT_ID \
  --flatten="bindings[].members" \
  --format='table(bindings.role)' \
  --filter="bindings.members:terraform-sa@"

# Add missing role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:terraform-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/container.admin"
```

### 1.2 Quota Exceeded

**Symptom**: Terraform fails with quota exceeded error

**Solutions**:

```bash
# Check current quota usage
gcloud compute project-info describe --format="table(quotas)"

# Request quota increase
gcloud compute project-info update \
  --project=$PROJECT_ID \
  --quota-metric=CPUS \
  --quota-region=us-central1 \
  --quota-value=48
```

### 1.3 GKE Cluster Creation Fails

**Symptom**: GKE cluster fails to create

**Solutions**:

```bash
# Check cluster status
gcloud container clusters describe instagram-clone-gke \
  --region us-central1

# Check operation logs
gcloud container operations list --filter="status!=DONE"

# Delete and recreate if stuck
gcloud container clusters delete instagram-clone-gke \
  --region us-central1 --quiet

terraform apply -target=module.gke
```

---

## 2. Kubernetes Issues

### 2.1 Pods in CrashLoopBackOff

**Symptom**: Pods keep restarting

**Diagnosis**:

```bash
# Check pod status
kubectl -n instagram-clone get pods

# View pod logs
kubectl -n instagram-clone logs <pod-name> --previous

# Describe pod for events
kubectl -n instagram-clone describe pod <pod-name>

# Check resource limits
kubectl -n instagram-clone top pods
```

**Common Causes & Solutions**:

1. **OOM Killed**:
```yaml
# Increase memory limits in deployment
resources:
  limits:
    memory: "2Gi"
```

2. **Application Error**:
```bash
# Check application logs
kubectl -n instagram-clone logs <pod-name> -f
```

3. **Failed Health Checks**:
```yaml
# Adjust probe settings
livenessProbe:
  initialDelaySeconds: 120  # Increase if app takes time to start
  periodSeconds: 30
```

### 2.2 Pods in ImagePullBackOff

**Symptom**: Cannot pull container images

**Solutions**:

```bash
# Verify image exists
gcloud container images list-tags gcr.io/$PROJECT_ID/auth-service

# Check image pull secret
kubectl -n instagram-clone get secrets

# Verify service account
kubectl -n instagram-clone get serviceaccount instagram-app -o yaml

# Create image pull secret if needed
kubectl -n instagram-clone create secret docker-registry gcr-secret \
  --docker-server=gcr.io \
  --docker-username=_json_key \
  --docker-password="$(cat key.json)"
```

### 2.3 Pods Pending

**Symptom**: Pods stuck in Pending state

**Diagnosis**:

```bash
# Check pod events
kubectl -n instagram-clone describe pod <pod-name>

# Check node resources
kubectl describe nodes

# Check PVC status
kubectl -n instagram-clone get pvc
```

**Solutions**:

1. **Insufficient Resources**:
```bash
# Scale down other pods or increase node pool
gcloud container clusters resize instagram-clone-gke \
  --num-nodes=5 --region=us-central1
```

2. **PVC Pending**:
```bash
# Check storage class
kubectl get storageclass

# Verify PV availability
kubectl get pv
```

### 2.4 HPA Not Scaling

**Symptom**: HPA shows `<unknown>` for metrics

**Solutions**:

```bash
# Check metrics server
kubectl -n kube-system get pods | grep metrics-server

# Verify metrics availability
kubectl top pods -n instagram-clone

# Check HPA status
kubectl -n instagram-clone describe hpa auth-service-hpa

# Verify resource requests are set
kubectl -n instagram-clone get deployment auth-service -o yaml | grep -A5 resources
```

---

## 3. Application Issues

### 3.1 Authentication Failures

**Symptom**: 401 Unauthorized errors

**Diagnosis**:

```bash
# Check auth-service logs
kubectl -n instagram-clone logs -l app=auth-service -f

# Verify JWT secret
kubectl -n instagram-clone get secret app-secrets -o jsonpath='{.data.JWT_SECRET}' | base64 -d
```

**Solutions**:

1. **JWT Secret Mismatch**:
```bash
# Sync JWT secret across services
kubectl -n instagram-clone rollout restart deployment
```

2. **Token Expired**:
```bash
# Check token expiration configuration
kubectl -n instagram-clone get configmap app-config -o yaml | grep JWT
```

### 3.2 Database Connection Failures

**Symptom**: Unable to connect to database

**Diagnosis**:

```bash
# Check database connectivity from pod
kubectl -n instagram-clone exec -it deploy/auth-service -- \
  nc -zv $DB_HOST 5432

# Verify database credentials
kubectl -n instagram-clone get secret app-secrets -o yaml

# Check Cloud SQL status
gcloud sql instances describe instagram-db
```

**Solutions**:

1. **Network Connectivity**:
```bash
# Verify VPC peering
gcloud compute networks peerings list

# Check firewall rules
gcloud compute firewall-rules list
```

2. **Credential Issues**:
```bash
# Update database password
gcloud sql users set-password postgres \
  --instance=instagram-db \
  --password=new-password

# Update Kubernetes secret
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=DB_PASSWORD=new-password \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 3.3 Redis Connection Failures

**Symptom**: Feed service cannot connect to Redis

**Diagnosis**:

```bash
# Check Redis connectivity
kubectl -n instagram-clone exec -it deploy/feed-service -- \
  nc -zv $REDIS_HOST 6379

# Verify Redis instance status
gcloud redis instances describe instagram-redis --region=us-central1
```

**Solutions**:

```bash
# Verify VPC connector
gcloud compute networks vpc-access connectors describe instagram-connector

# Check Redis auth
gcloud redis instances describe instagram-redis --format="value(authEnabled)"
```

### 3.4 GCS Upload Failures

**Symptom**: Image uploads fail

**Diagnosis**:

```bash
# Check GCS permissions
kubectl -n instagram-clone exec -it deploy/post-service -- \
  curl -H "Metadata-Flavor: Google" \
  http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/scopes

# Verify workload identity
gcloud iam service-accounts get-iam-policy \
  post-service@$PROJECT_ID.iam.gserviceaccount.com
```

**Solutions**:

```bash
# Grant storage permissions
gcloud storage buckets add-iam-policy-binding gs://instagram-media-bucket \
  --member="serviceAccount:post-service@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"
```

---

## 4. Database Issues

### 4.1 High Connection Count

**Symptom**: Database connection limit reached

**Diagnosis**:

```bash
# Check connection count
gcloud sql instances describe instagram-db \
  --format="value(settings.userLabels)"

# View connection metrics
gcloud monitoring metrics list --filter="metric.type:cloudsql"
```

**Solutions**:

```bash
# Increase max connections
gcloud sql instances patch instagram-db \
  --database-flags=max_connections=200

# Optimize connection pool in Spring
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

### 4.2 Slow Queries

**Symptom**: High latency on database operations

**Diagnosis**:

```bash
# Enable slow query logging
gcloud sql instances patch instagram-db \
  --database-flags=log_min_duration_statement=1000

# Check slow query log
gcloud sql operations list --instance=instagram-db
```

**Solutions**:

1. **Add indexes**:
```sql
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_comments_post_id ON comments(post_id);
```

2. **Optimize queries in code**

### 4.3 Migration Failures

**Symptom**: Flyway migrations fail

**Diagnosis**:

```bash
# Check migration status
kubectl -n instagram-clone exec -it deploy/auth-service -- \
  java -jar app.jar --spring.profiles.active=migrate

# View Flyway history
kubectl -n instagram-clone exec -it deploy/auth-service -- \
  psql -h $DB_HOST -U postgres -d auth_db -c "SELECT * FROM flyway_schema_history"
```

**Solutions**:

```bash
# Repair failed migration
kubectl -n instagram-clone exec -it deploy/auth-service -- \
  java -jar app.jar --spring.flyway.repair=true

# Skip problematic migration (use with caution)
kubectl -n instagram-clone exec -it deploy/auth-service -- \
  java -jar app.jar --spring.flyway.baseline-on-migrate=true
```

---

## 5. Networking Issues

### 5.1 Ingress Not Working

**Symptom**: Cannot access application via ingress

**Diagnosis**:

```bash
# Check ingress status
kubectl -n instagram-clone describe ingress instagram-ingress

# Verify backend health
gcloud compute backend-services list

# Check load balancer
gcloud compute forwarding-rules list
```

**Solutions**:

1. **Backend unhealthy**:
```bash
# Check health check configuration
gcloud compute health-checks list

# Verify pod health endpoints
kubectl -n instagram-clone exec -it deploy/frontend -- \
  curl localhost:8080/health
```

2. **Certificate pending**:
```bash
# Check managed certificate status
kubectl -n instagram-clone get managedcertificate

# Verify domain ownership
gcloud domains registrations describe instagram.example.com
```

### 5.2 Network Policy Blocking Traffic

**Symptom**: Services cannot communicate

**Diagnosis**:

```bash
# List network policies
kubectl -n instagram-clone get networkpolicies

# Check policy details
kubectl -n instagram-clone describe networkpolicy <policy-name>

# Test connectivity
kubectl -n instagram-clone exec -it deploy/frontend -- \
  curl -v http://auth-service:8080/health
```

**Solutions**:

```bash
# Temporarily disable policies for testing
kubectl -n instagram-clone delete networkpolicy --all

# Fix policy rules
kubectl apply -f k8s/base/network-policies/network-policies.yaml
```

### 5.3 DNS Resolution Failures

**Symptom**: Cannot resolve service names

**Diagnosis**:

```bash
# Check CoreDNS
kubectl -n kube-system get pods -l k8s-app=kube-dns

# Test DNS resolution
kubectl -n instagram-clone exec -it deploy/frontend -- \
  nslookup auth-service.instagram-clone.svc.cluster.local
```

**Solutions**:

```bash
# Restart CoreDNS
kubectl -n kube-system rollout restart deployment/coredns

# Check DNS config
kubectl -n instagram-clone get configmap kube-dns-autoscaler -o yaml
```

---

## 6. Monitoring Issues

### 6.1 Prometheus Not Scraping Targets

**Symptom**: Missing metrics in Prometheus

**Diagnosis**:

```bash
# Check Prometheus targets
kubectl -n monitoring port-forward svc/prometheus 9090:9090
# Visit http://localhost:9090/targets

# Verify service annotations
kubectl -n instagram-clone get service auth-service -o yaml | grep prometheus
```

**Solutions**:

```yaml
# Add annotations to service
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
```

### 6.2 Grafana Dashboard Empty

**Symptom**: No data in Grafana dashboards

**Diagnosis**:

```bash
# Check datasource
kubectl -n monitoring get configmap grafana-datasources -o yaml

# Verify Prometheus connectivity
kubectl -n monitoring exec -it deploy/grafana -- \
  curl http://prometheus:9090/api/v1/query?query=up
```

**Solutions**:

```bash
# Recreate datasource
kubectl -n monitoring delete configmap grafana-datasources
kubectl apply -f monitoring/grafana/grafana.yaml
```

---

## 7. CI/CD Issues

### 7.1 Jenkins Pipeline Fails

**Symptom**: Build or deploy stage fails

**Diagnosis**:

```bash
# Check Jenkins agent pods
kubectl -n jenkins get pods

# View agent logs
kubectl -n jenkins logs <jenkins-agent-pod>
```

**Solutions**:

1. **Docker build fails**:
```bash
# Increase agent resources
# Update Jenkinsfile pod template
resources:
  limits:
    memory: "4Gi"
    cpu: "2"
```

2. **Push fails**:
```bash
# Verify credentials
kubectl -n jenkins get secret gcp-service-account-key
```

### 7.2 ArgoCD Sync Fails

**Symptom**: Application out of sync

**Diagnosis**:

```bash
# Check application status
argocd app get instagram-clone-dev

# View sync details
argocd app sync instagram-clone-dev --dry-run
```

**Solutions**:

```bash
# Force sync
argocd app sync instagram-clone-dev --force

# Hard refresh
argocd app get instagram-clone-dev --hard-refresh
```

---

## Quick Diagnostic Commands

```bash
# Overall cluster health
kubectl get nodes
kubectl top nodes
kubectl get pods --all-namespaces | grep -v Running

# Application health
kubectl -n instagram-clone get all
kubectl -n instagram-clone top pods
kubectl -n instagram-clone get events --sort-by='.lastTimestamp'

# Logs
kubectl -n instagram-clone logs -l app.kubernetes.io/part-of=instagram-clone --tail=100

# Network
kubectl -n instagram-clone get ingress,svc,endpoints
kubectl -n instagram-clone get networkpolicies

# Storage
kubectl -n instagram-clone get pvc
kubectl get pv
```

---

## Getting Help

1. **Check logs first** - Most issues are visible in application/pod logs
2. **Use kubectl describe** - Provides detailed information about resources
3. **Check GCP Console** - For infrastructure-level issues
4. **Review recent changes** - Most issues follow recent deployments

For additional support:
- GCP Support: https://cloud.google.com/support
- Kubernetes Docs: https://kubernetes.io/docs
- Spring Boot Docs: https://docs.spring.io/spring-boot
