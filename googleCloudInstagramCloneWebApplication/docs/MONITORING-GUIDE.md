# Monitoring Stack - Installation & Configuration Guide

## Instagram Clone - Comprehensive Monitoring Setup

This guide covers the complete monitoring infrastructure for the Instagram Clone application using Prometheus, Grafana, and related components.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Installation](#installation)
4. [Configuration](#configuration)
5. [Accessing Dashboards](#accessing-dashboards)
6. [Alert Configuration](#alert-configuration)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Tools

Before installing the monitoring stack, ensure you have the following installed:

| Tool | Minimum Version | Purpose |
|------|-----------------|---------|
| `kubectl` | v1.27+ | Kubernetes CLI |
| `helm` | v3.12+ | Kubernetes package manager |
| `gcloud` | Latest | GCP CLI (for GKE) |

### Kubernetes Cluster Requirements

- **GKE Cluster**: Running and accessible via `kubectl`
- **Namespace**: `monitoring` (created automatically)
- **Storage Class**: `standard` or `premium-rwo` for persistence
- **Resources**: Minimum 4 CPU cores and 8GB RAM available

### Required Helm Repositories

```bash
# Add required repositories
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

### Verify Cluster Access

```bash
# Verify kubectl connection
kubectl cluster-info

# Check available nodes
kubectl get nodes

# Check available storage classes
kubectl get storageclasses
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Monitoring Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐     │
│   │   Auth Service   │    │   User Service   │    │   Post Service   │     │
│   │   :8080/metrics  │    │   :8080/metrics  │    │   :8080/metrics  │     │
│   └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘     │
│            │                       │                       │                │
│   ┌────────┴─────────┐    ┌────────┴─────────┐    ┌────────┴─────────┐     │
│   │   Feed Service   │    │ Comment Service  │    │   Like Service   │     │
│   │   :8080/metrics  │    │   :8080/metrics  │    │   :8080/metrics  │     │
│   └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘     │
│            │                       │                       │                │
│            └───────────────────────┼───────────────────────┘                │
│                                    │                                         │
│                            ┌───────▼───────┐                                │
│                            │ ServiceMonitor│                                │
│                            │   (for each)  │                                │
│                            └───────┬───────┘                                │
│                                    │                                         │
│            ┌───────────────────────▼───────────────────────┐                │
│            │                  Prometheus                    │                │
│            │  - Metric Collection (30s scrape)             │                │
│            │  - PrometheusRules (alerting)                 │                │
│            │  - 15 day retention                           │                │
│            └───────────────────────┬───────────────────────┘                │
│                                    │                                         │
│            ┌───────────────────────┼───────────────────────┐                │
│            │                       │                       │                │
│     ┌──────▼──────┐         ┌──────▼──────┐         ┌──────▼──────┐        │
│     │  Grafana    │         │Alertmanager │         │    Loki     │        │
│     │ Dashboards  │         │   Alerts    │         │    Logs     │        │
│     └─────────────┘         └─────────────┘         └─────────────┘        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Components

| Component | Purpose | Port |
|-----------|---------|------|
| **Prometheus** | Metrics collection and storage | 9090 |
| **Grafana** | Visualization and dashboards | 3000 |
| **Alertmanager** | Alert routing and management | 9093 |
| **Loki** | Log aggregation | 3100 |
| **Promtail** | Log shipping agent | - |
| **kube-state-metrics** | Kubernetes object metrics | 8080 |
| **node-exporter** | Node-level metrics | 9100 |

---

## Installation

### Step 1: Create Monitoring Namespace

```bash
kubectl create namespace monitoring
```

### Step 2: Install kube-prometheus-stack

Using the automated script:

```bash
cd scripts
chmod +x install-monitoring.sh
./install-monitoring.sh --grafana-password "your-secure-password"
```

Or manually with Helm:

```bash
# Install kube-prometheus-stack
helm install instagram-monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values infrastructure/helm/monitoring-values.yaml \
  --set grafana.adminPassword="your-secure-password" \
  --wait \
  --timeout 10m
```

### Step 3: Install Loki Stack (Optional)

```bash
helm install loki-stack grafana/loki-stack \
  --namespace monitoring \
  --set promtail.enabled=true \
  --set loki.persistence.enabled=false \
  --wait
```

### Step 4: Apply ServiceMonitors

```bash
kubectl apply -f infrastructure/monitoring/servicemonitors/
```

### Step 5: Apply PrometheusRules

```bash
kubectl apply -f infrastructure/monitoring/prometheus-rules/
```

### Step 6: Import Grafana Dashboards

```bash
# Create ConfigMaps for dashboards
for dashboard in infrastructure/monitoring/grafana/dashboards/*.json; do
  name=$(basename "$dashboard" .json)
  kubectl create configmap "grafana-dashboard-${name}" \
    --from-file="$dashboard" \
    -n monitoring \
    --dry-run=client -o yaml | \
    kubectl label --local -f - grafana_dashboard=1 -o yaml | \
    kubectl apply -f -
done
```

### Verify Installation

```bash
# Check all pods are running
kubectl get pods -n monitoring

# Expected output:
# NAME                                                     READY   STATUS    RESTARTS
# instagram-monitoring-grafana-xxx                         3/3     Running   0
# instagram-monitoring-kube-state-metrics-xxx              1/1     Running   0
# instagram-monitoring-operator-xxx                        1/1     Running   0
# instagram-monitoring-prometheus-xxx                      2/2     Running   0
# alertmanager-instagram-monitoring-alertmanager-0         2/2     Running   0
# prometheus-instagram-monitoring-prometheus-0             2/2     Running   0
```

---

## Configuration

### Helm Values Overview

The main configuration is in `infrastructure/helm/monitoring-values.yaml`:

```yaml
# Key configurations:
prometheus:
  prometheusSpec:
    scrapeInterval: 30s        # Metric collection interval
    retention: 15d             # Data retention period
    retentionSize: "45GB"      # Maximum storage size

grafana:
  persistence:
    enabled: true
    size: 10Gi
  
alertmanager:
  alertmanagerSpec:
    retention: 120h            # Alert history retention
```

### ServiceMonitor Configuration

Each service has a ServiceMonitor that tells Prometheus how to scrape metrics:

```yaml
# Example: servicemonitor-auth.yaml
spec:
  endpoints:
    - port: http
      path: /actuator/prometheus    # Spring Boot Actuator endpoint
      interval: 30s                  # Scrape interval
```

### PrometheusRule Configuration

Custom alerts are defined in `prometheus-rules/app-alerts.yaml`:

| Alert | Severity | Trigger |
|-------|----------|---------|
| `ServiceDown` | Critical | Service unavailable for 1m |
| `HighErrorRate` | Critical | >5% error rate for 5m |
| `HighLatency` | Warning | P95 latency >1s for 5m |
| `DatabasePoolExhausted` | Critical | >90% pool utilization |
| `JVMHeapCritical` | Critical | >95% heap usage |
| `PodCrashLooping` | Critical | >3 restarts in 15m |

---

## Accessing Dashboards

### Port Forwarding (Development)

```bash
# Grafana
kubectl port-forward svc/instagram-monitoring-grafana 3000:80 -n monitoring
# Access: http://localhost:3000

# Prometheus
kubectl port-forward svc/instagram-monitoring-prometheus 9090:9090 -n monitoring
# Access: http://localhost:9090

# Alertmanager
kubectl port-forward svc/instagram-monitoring-alertmanager 9093:9093 -n monitoring
# Access: http://localhost:9093
```

### Grafana Login

- **URL**: http://localhost:3000
- **Username**: `admin`
- **Password**: (set during installation or check secret)

```bash
# Get Grafana admin password
kubectl get secret instagram-monitoring-grafana -n monitoring \
  -o jsonpath="{.data.admin-password}" | base64 --decode
```

### Available Dashboards

| Dashboard | Description |
|-----------|-------------|
| **Instagram Clone - Application Overview** | All services health, request rates, latencies |
| **Spring Boot Services - Detailed Metrics** | Per-service JVM, HTTP, HikariCP metrics |
| **Kubernetes Cluster** | Node resources, deployments, pods |
| **Node Exporter** | Node-level CPU, memory, disk, network |

---

## Alert Configuration

### Alert Routes

Alerts are routed based on severity:

```yaml
# Default route
route:
  receiver: 'default-receiver'
  routes:
    - receiver: 'critical-receiver'
      matchers:
        - severity = critical
    - receiver: 'warning-receiver'
      matchers:
        - severity = warning
```

### Configure Slack Notifications

Edit `monitoring-values.yaml` to add Slack integration:

```yaml
alertmanager:
  config:
    receivers:
      - name: 'critical-receiver'
        slack_configs:
          - channel: '#critical-alerts'
            api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
            send_resolved: true
            title: '{{ .Status | toUpper }}: {{ .CommonLabels.alertname }}'
            text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

### Configure PagerDuty Notifications

```yaml
alertmanager:
  config:
    receivers:
      - name: 'critical-receiver'
        pagerduty_configs:
          - service_key: 'YOUR_PAGERDUTY_KEY'
            send_resolved: true
```

---

## Troubleshooting

### Common Issues

#### 1. Prometheus Not Scraping Services

```bash
# Check ServiceMonitor discovery
kubectl get servicemonitor -n monitoring

# Check Prometheus targets
kubectl port-forward svc/instagram-monitoring-prometheus 9090:9090 -n monitoring
# Navigate to http://localhost:9090/targets
```

**Solution**: Ensure services have correct labels matching ServiceMonitor selectors.

#### 2. Grafana Dashboards Not Loading

```bash
# Check dashboard ConfigMaps
kubectl get configmap -n monitoring -l grafana_dashboard=1

# Check Grafana sidecar logs
kubectl logs -n monitoring -l app.kubernetes.io/name=grafana -c grafana-sc-dashboard
```

#### 3. Alerts Not Firing

```bash
# Check PrometheusRules
kubectl get prometheusrule -n monitoring

# Check alert rules in Prometheus
kubectl port-forward svc/instagram-monitoring-prometheus 9090:9090 -n monitoring
# Navigate to http://localhost:9090/rules
```

#### 4. High Memory Usage

```bash
# Check Prometheus memory
kubectl top pod -n monitoring -l app.kubernetes.io/name=prometheus

# Reduce retention or add memory limits
helm upgrade instagram-monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --reuse-values \
  --set prometheus.prometheusSpec.retention=7d
```

### Useful Commands

```bash
# View Prometheus configuration
kubectl get secret prometheus-instagram-monitoring-prometheus \
  -n monitoring -o jsonpath='{.data.prometheus\.yaml\.gz}' | \
  base64 -d | gunzip

# Restart Prometheus
kubectl rollout restart statefulset prometheus-instagram-monitoring-prometheus -n monitoring

# View Grafana logs
kubectl logs -n monitoring -l app.kubernetes.io/name=grafana

# Check resource usage
kubectl top pods -n monitoring
```

### Metrics Verification

Verify Spring Boot services are exposing metrics:

```bash
# Port-forward to a service
kubectl port-forward svc/auth-service 8080:8080 -n instagram-clone

# Check metrics endpoint
curl http://localhost:8080/actuator/prometheus | head -50
```

---

## Next Steps

1. **Configure Alerting**: Set up Slack/PagerDuty for critical alerts
2. **Add Custom Dashboards**: Create team-specific visualization panels
3. **Set Up SLOs**: Define Service Level Objectives with recording rules
4. **Enable Tracing**: Integrate with Jaeger/Tempo for distributed tracing
5. **Configure RBAC**: Set up role-based access for Grafana users

---

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [kube-prometheus-stack Chart](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
