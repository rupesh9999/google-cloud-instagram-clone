# Instagram Clone - Cloud-Native Social Media Application

A fully modern, secure, multi-tier Instagram-like social media web application built on Google Cloud Platform using the latest stable technologies.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Google Cloud Platform                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        Cloud Load Balancer                           │    │
│  │                    (Global HTTP(S) Load Balancer)                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      GKE Autopilot Cluster (v1.32+)                  │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                    Ingress Controller                          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  │                              │                                       │    │
│  │  ┌───────────┐  ┌───────────────────────────────────────────────┐  │    │
│  │  │ Frontend  │  │              Backend Services                  │  │    │
│  │  │ (React)   │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐         │  │    │
│  │  │           │  │  │  Auth   │ │  User   │ │  Post   │         │  │    │
│  │  │  Nginx    │  │  │ Service │ │ Service │ │ Service │         │  │    │
│  │  │           │  │  └─────────┘ └─────────┘ └─────────┘         │  │    │
│  │  │           │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐         │  │    │
│  │  │           │  │  │  Feed   │ │ Comment │ │  Like   │         │  │    │
│  │  │           │  │  │ Service │ │ Service │ │ Service │         │  │    │
│  │  └───────────┘  │  └─────────┘ └─────────┘ └─────────┘         │  │    │
│  │                  └───────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                      │                                       │
│  ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────────┐    │
│  │     Cloud SQL        │  │   Memorystore    │  │   Cloud Storage    │    │
│  │   PostgreSQL 15      │  │    Redis 7.0     │  │     (Media)        │    │
│  │   (5 databases)      │  │    (Caching)     │  │                    │    │
│  └──────────────────────┘  └──────────────────┘  └────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────────┐    │
│  │   Secret Manager     │  │       IAM        │  │   Cloud Logging    │    │
│  │   (Credentials)      │  │ (Workload Identity)│  │  & Monitoring     │    │
│  └──────────────────────┘  └──────────────────┘  └────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 🛠️ Technology Stack

### Frontend
| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18.3.1 | UI Framework |
| TypeScript | 5.6.3 | Type Safety |
| Vite | 5.4.9 | Build Tool |
| TailwindCSS | 3.4.14 | Styling |
| React Router | 6.27.0 | Routing |
| Zustand | 5.0.0 | State Management |
| Axios | 1.7.7 | HTTP Client |

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 LTS | Runtime |
| Spring Boot | 3.3.5 | Application Framework |
| Spring Cloud | 2023.0.3 | Microservices |
| JJWT | 0.12.6 | JWT Authentication |
| MapStruct | 1.6.2 | Object Mapping |
| Flyway | Latest | Database Migrations |

### Infrastructure
| Technology | Version | Purpose |
|------------|---------|---------|
| GKE Autopilot | 1.32+ | Kubernetes Orchestration |
| Cloud SQL | PostgreSQL 15 | Relational Database |
| Memorystore | Redis 7.0 | Caching |
| Cloud Storage | - | Media Storage |
| Terraform | 1.7+ | Infrastructure as Code |
| Kustomize | Latest | K8s Configuration |

### Observability
| Technology | Purpose |
|------------|---------|
| Prometheus | Metrics Collection |
| Grafana | Visualization |
| Cloud Logging | Log Aggregation |
| Cloud Monitoring | Alerting |

## 📁 Project Structure

```
├── frontend/                    # React Frontend Application
│   ├── src/
│   │   ├── components/         # Reusable UI Components
│   │   ├── pages/              # Page Components
│   │   ├── services/           # API Services
│   │   ├── store/              # Zustand Store
│   │   ├── hooks/              # Custom Hooks
│   │   └── types/              # TypeScript Types
│   ├── Dockerfile
│   └── nginx.conf
│
├── backend/                     # Spring Boot Microservices
│   ├── common/                  # Shared DTOs, Security, Exceptions
│   ├── auth-service/            # Authentication & Authorization
│   ├── user-service/            # User Management
│   ├── post-service/            # Posts & Images
│   ├── feed-service/            # Feed Generation
│   ├── comment-service/         # Comments
│   └── like-service/            # Likes
│
├── terraform/                   # Infrastructure as Code
│   ├── main.tf                  # Root Module
│   ├── variables.tf             # Variables
│   ├── modules/
│   │   ├── vpc/                 # VPC & Networking
│   │   ├── gke/                 # GKE Autopilot
│   │   ├── cloud-sql/           # PostgreSQL
│   │   ├── memorystore/         # Redis
│   │   ├── gcs/                 # Cloud Storage
│   │   ├── iam/                 # IAM & Workload Identity
│   │   ├── secret-manager/      # Secrets
│   │   └── api-gateway/         # API Gateway
│   └── environments/
│       ├── dev/
│       ├── staging/
│       └── prod/
│
├── k8s/                         # Kubernetes Manifests
│   ├── base/                    # Base Kustomize
│   │   ├── deployments/
│   │   ├── services/
│   │   ├── configmaps/
│   │   ├── secrets/
│   │   ├── ingress/
│   │   ├── hpa/
│   │   └── network-policies/
│   └── overlays/
│       ├── dev/
│       ├── staging/
│       └── prod/
│
├── cicd/                        # CI/CD Configuration
│   ├── Jenkinsfile
│   └── argocd/
│
├── infrastructure/              # Infrastructure Configuration
│   ├── helm/                    # Helm values (monitoring)
│   └── monitoring/              # Prometheus/Grafana configs
│       ├── servicemonitors/
│       ├── prometheus-rules/
│       └── grafana/
│
├── scripts/                     # Automation Scripts
│   ├── setup-gcp.sh            # GCP setup & configuration
│   ├── build-push-images.sh    # Docker build & push
│   ├── deploy-gke.sh           # K8s deployment
│   ├── install-monitoring.sh   # Monitoring stack
│   └── validate-project.sh     # Project validation
│
├── docs/                        # Documentation
│   ├── PREREQUISITES.md
│   ├── EXECUTION-GUIDE.md
│   ├── TROUBLESHOOTING.md
│   └── MONITORING-GUIDE.md
│
└── monitoring/                  # Legacy Observability Stack
    ├── prometheus/
    └── grafana/
```

## 🚀 Quick Start

### Prerequisites

See [docs/PREREQUISITES.md](docs/PREREQUISITES.md) for detailed prerequisites.

Required tools:
- Google Cloud SDK (gcloud) installed and configured
- Terraform 1.7+
- kubectl 1.27+
- Helm 3.12+
- Docker 24+
- Node.js 20+
- Java 21+
- Maven 3.9+

### 1. Clone and Validate

```bash
git clone https://github.com/your-org/instagram-clone.git
cd instagram-clone

# Validate project structure
./scripts/validate-project.sh
```

### 2. Configure GCP Project

```bash
# Update project ID in terraform.tfvars
export PROJECT_ID=your-gcp-project-id

# Run GCP setup script
./scripts/setup-gcp.sh
```

### 3. Deploy Infrastructure

```bash
cd terraform
terraform init -backend-config="bucket=${PROJECT_ID}-tfstate"
terraform plan -var-file="environments/dev/terraform.tfvars"
terraform apply -var-file="environments/dev/terraform.tfvars"
```

### 4. Build and Push Images

```bash
# Using automation script
./scripts/build-push-images.sh
```

### 5. Deploy to Kubernetes

```bash
# Get cluster credentials
gcloud container clusters get-credentials instagram-clone-dev-gke --region us-central1

# Deploy using automation script
./scripts/deploy-gke.sh --environment dev
```

### 6. Install Monitoring Stack

```bash
./scripts/install-monitoring.sh --grafana-password "your-secure-password"
```

For detailed instructions, see [docs/EXECUTION-GUIDE.md](docs/EXECUTION-GUIDE.md).

## 🔐 Security Features

- **Workload Identity**: Service accounts mapped to GCP IAM
- **External Secrets Operator**: Secrets managed via GCP Secret Manager
- **Network Policies**: Micro-segmentation between services
- **Non-root Containers**: All containers run as non-root user (UID 1001)
- **Read-only Root Filesystem**: Enhanced container security
- **JWT Authentication**: Stateless token-based auth with JJWT 0.12.6
- **HTTPS Only**: TLS termination at load balancer
- **CORS Configuration**: Strict origin validation

## 📊 Monitoring & Observability

### Prometheus Metrics

All backend services expose metrics at `/actuator/prometheus`:
- HTTP request rates and latencies
- JVM memory and GC metrics
- Database connection pool stats
- Redis cache hit/miss rates

### Grafana Dashboards

Pre-configured dashboards for:
- Application Overview
- Service Health
- Resource Utilization
- Error Rates

### Alerting

Configured alerts for:
- High CPU/Memory usage
- Pod crash loops
- High error rates
- High latency
- Database connection pool exhaustion

## 🔄 CI/CD Pipeline

### Jenkins Pipeline Stages

1. **Checkout**: Clone source code
2. **Build & Test**: Parallel build of frontend and backend
3. **Security Scan**: SAST, dependency check, Trivy scan
4. **Build Docker Images**: Multi-stage builds
5. **Scan Docker Images**: Trivy vulnerability scan
6. **Push Images**: Push to GCR
7. **Deploy**: Environment-specific deployment

### ArgoCD GitOps

- Automatic sync for dev/staging
- Manual approval for production
- Rollback capabilities
- Health monitoring

## 📈 Scaling

### Horizontal Pod Autoscaler

All services configured with HPA:
- **Target CPU**: 70%
- **Target Memory**: 80%
- **Min Replicas**: 2 (dev: 1)
- **Max Replicas**: 10-20 (varies by service)

### Database Scaling

- Cloud SQL with read replicas
- Connection pooling via HikariCP
- Redis caching for feed service

## 🧪 Testing

```bash
# Backend unit tests
cd backend
mvn test

# Frontend tests
cd frontend
npm run test

# Integration tests
mvn verify -DskipUTs
```

## 📝 API Documentation

API documentation available at:
- Swagger UI: `https://api.instagram.example.com/swagger-ui.html`
- OpenAPI Spec: `https://api.instagram.example.com/v3/api-docs`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Google Cloud Platform
- Spring Boot Team
- React Team
- Kubernetes Community
