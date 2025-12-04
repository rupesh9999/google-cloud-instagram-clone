# Instagram Clone - Comprehensive Theoretical Documentation

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Repo Snapshot](#2-repo-snapshot)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Frontend — Conceptual Explanation](#4-frontend--conceptual-explanation)
5. [Backend Microservices — Conceptual Descriptions](#5-backend-microservices--conceptual-descriptions)
6. [Data Tier — Conceptual](#6-data-tier--conceptual)
7. [Integration & Messaging — Conceptual](#7-integration--messaging--conceptual)
8. [Infrastructure & DevOps — Conceptual](#8-infrastructure--devops--conceptual)
9. [CI/CD & GitOps — Conceptual](#9-cicd--gitops--conceptual)
10. [Observability & Logging — Conceptual](#10-observability--logging--conceptual)
11. [Execution Guide](#11-execution-guide)
12. [Example Workflows](#12-example-workflows)
13. [Troubleshooting — Conceptual](#13-troubleshooting--conceptual)
14. [Quick Start Runbook](#14-quick-start-runbook)
15. [Repository Hotspots](#15-repository-hotspots)

---

## 1) Executive Summary

The Instagram Clone is a production-grade, cloud-native social media application designed to demonstrate enterprise-level microservices architecture on Google Cloud Platform. The system enables users to register accounts, share photo-based posts, follow other users, interact through likes and comments, and consume personalized content feeds. Built for horizontal scalability and high availability, the application showcases modern DevOps practices including infrastructure-as-code, containerized deployments, GitOps-based continuous delivery, and comprehensive observability.

The architecture follows a classic three-tier pattern: the **Presentation Tier** consists of a React single-page application served via Nginx, the **Application Tier** comprises six Java Spring Boot microservices running on Google Kubernetes Engine (GKE), and the **Data Tier** leverages Cloud SQL PostgreSQL for transactional data, Cloud Memorystore Redis for caching and session management, and Google Cloud Storage for media assets. Each tier is independently scalable, secured through network policies and Workload Identity, and managed declaratively through Terraform and Kubernetes manifests. The CI/CD pipeline orchestrates builds through Jenkins while Argo CD handles GitOps-style deployments, ensuring that the desired state in Git always matches the running infrastructure.

---

## 2) Repo Snapshot

### Top-Level Directory Structure

```
googleCloudInstagramCloneWebApplication/
├── argocd/              # Argo CD application manifests for GitOps
├── backend/             # Java Spring Boot microservices (Maven multi-module)
├── cicd/                # Jenkins pipeline definitions and CI/CD configs
├── docs/                # Project documentation
├── frontend/            # React TypeScript SPA with Vite build tooling
├── infrastructure/      # Additional infrastructure scripts
├── k8s/                 # Kubernetes manifests (Kustomize-based)
├── monitoring/          # Prometheus and Grafana configurations
├── scripts/             # Helper scripts for deployment and operations
└── terraform/           # Infrastructure as Code for GCP resources
```

### Detected Microservices (from `pom.xml` files)

| Service | Location | Primary Role |
|---------|----------|--------------|
| **auth-service** | `backend/auth-service/` | User authentication, JWT token management |
| **user-service** | `backend/user-service/` | User profiles, follow relationships |
| **post-service** | `backend/post-service/` | Post CRUD, media upload to GCS |
| **feed-service** | `backend/feed-service/` | Timeline aggregation, Redis caching |
| **comment-service** | `backend/comment-service/` | Comment management on posts |
| **like-service** | `backend/like-service/` | Like/unlike functionality |
| **common** | `backend/common/` | Shared DTOs, utilities, security configs |

### Key File Locations

| Category | File/Directory |
|----------|----------------|
| Frontend Package | `frontend/package.json` |
| Frontend Dockerfile | `frontend/Dockerfile` |
| Nginx Configuration | `frontend/nginx.conf` |
| Parent Maven POM | `backend/pom.xml` |
| Terraform Root | `terraform/main.tf` |
| Terraform Modules | `terraform/modules/{vpc,gke,cloud-sql,gcs,memorystore,iam,secret-manager,api-gateway}/` |
| K8s Base Manifests | `k8s/base/` |
| K8s Deployments | `k8s/base/deployments/` |
| K8s Ingress | `k8s/base/ingress/ingress.yaml` |
| Kustomization | `k8s/base/kustomization.yaml` |
| External Secrets | `k8s/base/secrets/external-secrets.yaml` |
| Jenkins Pipeline | `cicd/Jenkinsfile` |
| Argo CD Application | `argocd/application.yaml` |
| Prometheus Config | `monitoring/prometheus/prometheus.yaml` |
| Grafana Config | `monitoring/grafana/grafana.yaml` |

---

## 3) High-Level Architecture (Conceptual)

### Architectural Overview

The Instagram Clone follows a distributed microservices architecture where each service owns a specific domain and communicates through well-defined APIs. Think of the architecture as a restaurant: the **frontend** is the waiter taking customer orders, the **microservices** are specialized kitchen stations (one for appetizers, one for main courses), the **databases** are the pantry and refrigerator storing ingredients, and the **load balancer/ingress** is the maître d' directing customers to available tables.

### Request Flow Diagram (Text-Based)

```
┌─────────────┐     HTTPS      ┌──────────────────┐
│   Browser   │ ─────────────► │  GCP Load        │
│   (React)   │                │  Balancer        │
└─────────────┘                └────────┬─────────┘
                                        │
                                        ▼
                               ┌──────────────────┐
                               │  GKE Ingress     │
                               │  Controller      │
                               └────────┬─────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              │                         │                         │
              ▼                         ▼                         ▼
      ┌───────────────┐        ┌───────────────┐        ┌───────────────┐
      │   Frontend    │        │ auth-service  │        │ post-service  │
      │   (Nginx)     │        └───────┬───────┘        └───────┬───────┘
      └───────────────┘                │                        │
                                       ▼                        ▼
                               ┌───────────────┐        ┌───────────────┐
                               │  Cloud SQL    │        │  Cloud SQL    │
                               │  (instagram_  │        │  (instagram_  │
                               │   auth DB)    │        │   post DB)    │
                               └───────────────┘        └───────┬───────┘
                                                                │
                                                                ▼
                                                        ┌───────────────┐
                                                        │  GCS Bucket   │
                                                        │  (Media)      │
                                                        └───────────────┘
```

### Example Flow: User Uploads a Photo

Imagine a user named Alice wants to share a photo of her morning coffee. Here is the journey that request takes through the system:

1. **Alice opens the Create Post page** in her browser. The React frontend renders an upload form with a file picker and caption input field.

2. **Alice selects her photo and clicks "Share"**. The frontend packages the image as `multipart/form-data` and sends a POST request to `/api/posts`. The request includes Alice's JWT token in the `Authorization` header.

3. **The GKE Ingress receives the request** and inspects the path. Since it matches `/api/posts`, the ingress routes it to the `post-service` Kubernetes Service, which load-balances across available pods.

4. **The post-service validates Alice's JWT** by decoding it and verifying the signature against the secret stored in Secret Manager (injected via External Secrets). Once authenticated, the service knows the request belongs to user ID `alice123`.

5. **The post-service uploads the image to GCS**. Using the Google Cloud Storage client library and Workload Identity (the pod automatically has credentials through its Kubernetes service account), the service uploads the image to `gs://instagram-clone-prod-media/posts/alice123/uuid.jpg` and receives back a public URL.

6. **The post-service persists the post metadata** to Cloud SQL's `instagram_post` database, including the GCS URL, caption, timestamp, and Alice's user ID. Flyway migrations ensure the schema is up to date.

7. **The service returns a 201 Created response** with the post object. The frontend receives the response, displays a success toast, and navigates Alice to her new post's detail page.

8. **Meanwhile, an event could trigger feed updates** for Alice's followers. Although not implemented with Pub/Sub in the current codebase, the architecture supports adding an event like `post-created` to a Pub/Sub topic, which the `feed-service` would consume to invalidate cached timelines.

This flow demonstrates separation of concerns: authentication logic lives in `auth-service`, media storage is handled by `post-service` with GCS, and the database only stores metadata pointers—not binary image data.

---

## 4) Frontend — Conceptual Explanation

### Technology Choices and Rationale

The frontend is built with **React 18** and **TypeScript**, using **Vite** as the build tool. This combination was chosen for several reasons:

- **React** provides a component-based architecture that mirrors the modularity of the backend microservices. Each UI feature (Post Card, Navbar, User Profile) is a self-contained component, making the codebase navigable and testable.

- **TypeScript** adds static typing, catching bugs at compile time that would otherwise surface as runtime errors. When the backend API changes, TypeScript interfaces fail to compile, alerting developers immediately.

- **Vite** offers near-instant hot module replacement during development and optimized production builds. Compared to Create React App or Webpack, Vite's esbuild-powered compilation is significantly faster, improving developer experience.

- **Tailwind CSS** handles styling through utility classes, eliminating the need for separate CSS files and reducing context-switching between markup and styles. The resulting bundle is smaller because Tailwind purges unused classes during production builds.

### Authentication Flow

The frontend manages authentication state using **Zustand**, a lightweight state management library. When a user logs in:

1. The `authService.login()` function sends credentials to `/api/v1/auth/login`.
2. The backend returns a JWT token and user details.
3. Zustand's `useAuthStore` persists the token in memory (or optionally localStorage).
4. An **Axios interceptor** automatically attaches the token to every subsequent request as `Authorization: Bearer <token>`.
5. If any API returns a 401 Unauthorized, another interceptor triggers logout and redirects to `/login`.

This approach is stateless from the server's perspective—no session cookies are needed. The JWT contains the user's identity, and the backend verifies it cryptographically on each request.

### API Interactions

The `services/` directory contains thin wrappers around Axios calls, organized by domain:

- `authService.ts` — login, register, logout, token validation
- `userService.ts` — profile retrieval, follow/unfollow
- `postService.ts` — CRUD for posts, multipart image upload
- `feedService.ts` — timeline and explore feeds
- `commentService.ts` — comment CRUD
- `likeService.ts` — like/unlike toggle

**React Query** handles server state caching, automatic refetching, and optimistic updates. For example, when a user likes a post, React Query immediately updates the UI while the actual API call happens in the background. If the call fails, React Query rolls back to the previous state.

### Image Uploads

When creating a post, the frontend uses `FormData` to send the image as `multipart/form-data`:

```
POST /api/v1/posts
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="image"; filename="coffee.jpg"
Content-Type: image/jpeg

<binary data>
--boundary
Content-Disposition: form-data; name="caption"

Morning coffee ☕
--boundary--
```

The post-service receives this multipart request, streams the binary data directly to GCS, and stores only the resulting URL in the database. This design keeps the backend stateless and leverages GCS's CDN capabilities for serving images.

### Nginx Configuration

In production, Nginx serves the static React bundle on port 8080 (a non-privileged port, allowing the container to run as non-root). Key configuration aspects include:

- **Gzip compression** for text-based assets (JS, CSS, JSON), reducing transfer sizes by 60-80%.
- **Security headers** (X-Frame-Options, X-Content-Type-Options) to prevent clickjacking and MIME sniffing attacks.
- **SPA routing fallback**: Any request that doesn't match a static file returns `index.html`, allowing React Router to handle client-side navigation.
- **API proxying**: Requests to `/api/` are proxied to backend services, avoiding CORS issues.

### User Journey: Login → Feed → Like → Comment → Upload

1. **Login**: Alice navigates to `/login`, enters her credentials, and clicks Submit. The auth-service validates her password against the bcrypt hash in Cloud SQL, issues a JWT, and the frontend stores it. She is redirected to the home feed.

2. **View Feed**: The Home page calls `feedService.getFeed()`, which hits `/api/v1/feed`. The feed-service checks Redis for a cached timeline; if missing, it queries the post-service for posts from users Alice follows, caches the result, and returns it. React Query caches this on the client.

3. **Like a Post**: Alice double-taps a post. The UI optimistically adds a heart animation while `likeService.likePost(postId)` sends a request to the like-service. The like-service records the like in Cloud SQL and may publish an event for notifications.

4. **Comment**: Alice types "Love this!" and submits. `commentService.createComment()` POSTs to the comment-service, which persists the comment and returns the saved entity with a generated ID. React Query invalidates the comment list, triggering a refetch.

5. **Upload**: Alice clicks the Create button, selects a photo, adds a caption, and shares. The post-service streams the image to GCS, saves metadata to Cloud SQL, and returns the new post. The frontend navigates to the post detail page.

---

## 5) Backend Microservices — Conceptual Descriptions

All backend services share a common parent Maven POM that standardizes dependency versions (Spring Boot 3.3.5, Java 21, Google Cloud BOM 26.49.0). They also share a `common` module containing DTOs, security filters, and utility classes.

### 5.1 Auth Service

**Purpose**: The auth-service is the gatekeeper. It handles user registration, login, and JWT token issuance/validation. No other service directly manages credentials.

**Data Ownership**: Owns the `instagram_auth` database containing the `users` table with columns for username, email, password hash, and account status.

**Data Store Choice**: Cloud SQL PostgreSQL was chosen because authentication data requires ACID transactions (e.g., ensuring a username isn't registered twice) and relational queries (e.g., lookup by email or username).

**Key APIs**:
- `POST /api/v1/auth/register` — Creates a new user, hashes the password with bcrypt, and returns a JWT.
- `POST /api/v1/auth/login` — Validates credentials, returns a JWT if successful.
- `GET /api/v1/auth/validate` — Checks if a given token is valid (used by other services or API gateways for token introspection).
- `POST /api/v1/auth/refresh` — Issues a new token for an authenticated user.

**Example Interaction**: When Bob registers, the auth-service receives his username, email, and password. It first checks if the username or email already exists (query against Cloud SQL). If not, it hashes the password, inserts the row, generates a JWT containing Bob's user ID and expiration, and returns the token. Bob's frontend stores this token and includes it in all future requests.

**Scaling Considerations**: The auth-service is stateless—any instance can validate any JWT because the signing secret is shared (via Secret Manager). This allows horizontal scaling without session affinity.

### 5.2 User Service

**Purpose**: Manages user profiles, avatars, bios, and follow relationships. Think of it as the "social graph" service.

**Data Ownership**: Owns the `instagram_user` database with tables for `user_profiles` (display name, bio, avatar URL) and `follows` (follower_id, following_id).

**Data Store Choice**: PostgreSQL suits relational queries like "list all users Bob follows" or "count Bob's followers." These are foreign-key relationships best handled by SQL.

**Key APIs**:
- `GET /api/v1/users/{username}` — Returns profile details.
- `PUT /api/v1/users/{username}` — Updates profile (authenticated user only).
- `POST /api/v1/users/{username}/follow` — Creates a follow relationship.
- `DELETE /api/v1/users/{username}/follow` — Unfollows a user.
- `GET /api/v1/users/{username}/followers` — Paginated list of followers.
- `GET /api/v1/users/{username}/following` — Paginated list of following.

**Example Interaction**: Alice wants to follow Bob. The frontend calls `POST /api/v1/users/bob/follow`. The user-service verifies Alice's JWT, checks that Alice isn't already following Bob, inserts a row into the `follows` table, and returns 201 Created. Now when Alice fetches her feed, the feed-service will include Bob's posts.

**Scaling Considerations**: The user-service can scale horizontally. Follow counts can be cached in Redis to avoid expensive COUNT queries.

### 5.3 Post Service

**Purpose**: Handles post creation, retrieval, deletion, and media management. This is the "content" service.

**Data Ownership**: Owns the `instagram_post` database with the `posts` table (post_id, user_id, caption, image_url, hashtags, created_at).

**Data Store Choice**: PostgreSQL for transactional integrity (e.g., ensuring a post is saved before returning a URL). Media files (images, videos) go to GCS because databases are not optimized for binary blob storage, and GCS provides CDN-backed delivery.

**Key APIs**:
- `POST /api/v1/posts` — Creates a post with image upload.
- `GET /api/v1/posts/{postId}` — Retrieves a single post.
- `DELETE /api/v1/posts/{postId}` — Deletes a post (owner only).
- `GET /api/v1/posts/user/{username}` — Paginated posts by user.
- `GET /api/v1/posts/hashtag/{tag}` — Posts matching a hashtag.

**Example Interaction**: When Alice uploads a photo:
1. The post-service receives multipart data.
2. It streams the image to GCS: `Storage.create(BlobInfo, inputStream)`.
3. GCS returns a public URL like `https://storage.googleapis.com/instagram-clone-prod-media/posts/uuid.jpg`.
4. The service inserts a row into `posts` with the URL.
5. Returns the post JSON to the client.

**Scaling Considerations**: Image uploads are I/O bound (streaming to GCS), so the service benefits from async processing or thread pool tuning. The post-service is stateless.

### 5.4 Feed Service

**Purpose**: Aggregates posts into personalized timelines. This is the "assembly line" that combines content from followed users.

**Data Ownership**: The feed-service doesn't own persistent data in PostgreSQL. Instead, it owns cached timelines in Redis.

**Data Store Choice**: Redis (Memorystore) is ideal for feed caching because:
1. Timelines are read-heavy (every time a user opens the app).
2. Data is ephemeral—losing a cached feed is inconvenient but not catastrophic.
3. Redis's sorted sets allow efficient "top N posts by timestamp" queries.

**Key APIs**:
- `GET /api/v1/feed` — Returns the authenticated user's timeline.
- `GET /api/v1/feed/explore` — Returns trending or discovery posts.

**Example Interaction**: Alice opens the app. The feed-service checks Redis for `feed:alice`. If present, it returns the cached list. If not:
1. Query user-service for Alice's following list.
2. Query post-service for recent posts from those users.
3. Sort by timestamp.
4. Cache the result in Redis with a TTL of 5 minutes.
5. Return the posts.

**Scaling Considerations**: Feed generation is CPU and network intensive. Caching dramatically reduces load. For write-heavy scenarios (many users posting simultaneously), consider "fan-out on write" (pushing posts to followers' caches) versus "fan-out on read" (aggregating at read time). This architecture uses fan-out on read with caching.

### 5.5 Comment Service

**Purpose**: Manages comments on posts. Isolated from post-service to allow independent scaling—comments might be edited/deleted more frequently than posts.

**Data Ownership**: Owns the `instagram_comment` database with the `comments` table (comment_id, post_id, user_id, content, created_at).

**Data Store Choice**: PostgreSQL for relational queries (all comments on a post, all comments by a user).

**Key APIs**:
- `POST /api/v1/comments` — Creates a comment.
- `GET /api/v1/comments/post/{postId}` — Paginated comments for a post.
- `DELETE /api/v1/comments/{commentId}` — Deletes a comment (owner only).

**Example Interaction**: Bob comments on Alice's post. The comment-service validates Bob's JWT, verifies the post exists (optional call to post-service or trusted client), inserts the comment, and returns it. The frontend adds the comment to the UI optimistically.

### 5.6 Like Service

**Purpose**: Tracks likes on posts. Separated for scalability—likes can generate high write throughput during viral posts.

**Data Ownership**: Owns the `instagram_like` database with the `likes` table (like_id, post_id, user_id, created_at).

**Data Store Choice**: PostgreSQL with a unique constraint on (post_id, user_id) to prevent duplicate likes.

**Key APIs**:
- `POST /api/v1/likes/post/{postId}` — Likes a post.
- `DELETE /api/v1/likes/post/{postId}` — Unlikes a post.
- `GET /api/v1/likes/post/{postId}` — Users who liked a post.
- `GET /api/v1/likes/post/{postId}/status` — Whether the authenticated user has liked the post.

**Example Interaction**: Alice likes Bob's post. The like-service checks if she's already liked it (unique constraint check), inserts the row, and returns 201. The frontend updates the heart icon to filled.

---

## 6) Data Tier — Conceptual

### Cloud SQL (PostgreSQL)

**Role**: Primary transactional data store for structured, relational data.

**Why PostgreSQL**: Strong ACID guarantees, mature tooling, and native support for JSON columns if semi-structured data is needed. Cloud SQL manages backups, patching, and replication.

**Configuration Highlights**:
- **Private IP only**: The database is not exposed to the internet. GKE pods connect through private VPC peering.
- **Regional availability** (production): The instance has a standby in another zone for automatic failover.
- **Point-in-time recovery**: Enabled for production, allowing restoration to any second within the retention window.
- **Connection pooling**: Applications use HikariCP; consider PgBouncer for high connection counts.

**Database per Service**: Each microservice has its own database (`instagram_auth`, `instagram_user`, `instagram_post`, `instagram_comment`, `instagram_like`). This enforces service isolation—services cannot directly query another service's tables, forcing them to go through APIs.

### Cloud Memorystore (Redis)

**Role**: Caching layer and ephemeral data store.

**Use Cases**:
- **Feed caching**: Store serialized timeline data as sorted sets.
- **Session data**: If sticky sessions are needed (though JWTs are stateless).
- **Rate limiting**: Track request counts per user/IP using INCR with TTL.
- **Pub/Sub**: Redis supports pub/sub for lightweight, in-cluster messaging (different from Google Pub/Sub).

**Configuration Highlights**:
- **Standard HA tier** for production: Automatic failover to replica.
- **AUTH enabled**: Clients must provide a password (stored in Secret Manager).
- **maxmemory-policy: allkeys-lru**: When memory is full, Redis evicts least-recently-used keys. This is safe for a cache but not for durable data.

### Google Cloud Storage (GCS)

**Role**: Object storage for media files (images, videos, avatars).

**Why GCS over database BLOBs**:
- **Scalability**: GCS handles petabytes; databases struggle with large binaries.
- **CDN integration**: GCS can be fronted by Cloud CDN for global edge caching.
- **Cost**: Object storage is cheaper per GB than database storage.
- **Decoupling**: Media retrieval doesn't hit the application; clients fetch directly from GCS URLs.

**Configuration Highlights**:
- **Uniform bucket-level access**: Simplified permissions (no object-level ACLs).
- **Public read**: Images are publicly accessible via URL. For private media, use signed URLs.
- **Lifecycle rules**: After 365 days, move to Nearline (cheaper); after 730 days, move to Coldline.
- **CORS configured**: Allows browser-based uploads (if implementing direct-to-GCS uploads).

### Data Flow Analogy

Think of the data tier as a library:
- **Cloud SQL** is the card catalog—structured, indexed, queryable.
- **Redis** is the "quick reference" shelf near the entrance—frequently accessed materials kept handy.
- **GCS** is the archive warehouse—bulk storage with excellent retrieval but not for complex queries.

---

## 7) Integration & Messaging — Conceptual

### Kubernetes Ingress (GCE)

**Role**: The ingress controller is the single entry point for external traffic. It terminates TLS, routes requests based on URL path, and load-balances across pod replicas.

**What It Provides**:
- **TLS termination**: Managed certificates (via ManagedCertificate CRD) automatically provision Let's Encrypt certificates.
- **Path-based routing**: `/api/auth/*` → auth-service, `/api/posts/*` → post-service, etc.
- **Health checks**: The GCE load balancer probes `/actuator/health` on each pod to determine readiness.
- **Cloud Armor**: Security policies can block malicious traffic (SQL injection attempts, DDoS).

### Backend Configuration

The `BackendConfig` CRD customizes how GKE's load balancer interacts with services:
- **Session affinity**: Generated cookies ensure a user's requests hit the same pod (useful for debugging, though services are stateless).
- **CDN**: Enable Cloud CDN for static assets (frontend).
- **Custom health checks**: Override the default probe path if services use non-standard endpoints.

### Event-Driven Architecture (Future Enhancement)

While the current implementation uses synchronous REST calls, the architecture is designed to support asynchronous event-driven patterns via Google Cloud Pub/Sub:

**Example Flow**: `post-created` Event

1. **Alice creates a post**: The post-service publishes a message to the `post-created` topic:
   ```json
   {
     "postId": "abc123",
     "userId": "alice",
     "timestamp": "2025-12-04T10:00:00Z"
   }
   ```

2. **The feed-service subscribes** to this topic. When it receives the event, it invalidates cached feeds for Alice's followers.

3. **A notification-service subscribes** (if implemented). It sends push notifications to followers who have enabled alerts.

4. **An analytics-service subscribes** to record engagement metrics.

This pattern decouples producers from consumers—the post-service doesn't know or care who processes the event. Services can be added or removed without modifying the publisher.

---

## 8) Infrastructure & DevOps — Conceptual

### Terraform Modules

Terraform defines all GCP resources in a modular, reusable structure. Each module encapsulates a logical component:

| Module | Purpose |
|--------|---------|
| **vpc** | Creates the Virtual Private Cloud, subnets for GKE, private service access for Cloud SQL, Cloud NAT for outbound traffic from private nodes. |
| **gke** | Provisions the Kubernetes cluster with node pools, autoscaling, Workload Identity, and network policies. |
| **cloud-sql** | Creates the PostgreSQL instance, databases for each microservice, and the application user. |
| **memorystore** | Provisions the Redis instance with authentication and HA configuration. |
| **gcs** | Creates the media bucket with lifecycle rules, CORS, and public read access. |
| **iam** | Creates service accounts (application, CI/CD) and binds IAM roles. Sets up Workload Identity for GKE pods. |
| **secret-manager** | Stores sensitive values (DB password, JWT secret, Redis auth) in Secret Manager. |
| **api-gateway** | (If needed) Configures Cloud Endpoints or API Gateway for rate limiting and API management. |

**Why Modules?**: Modules promote reuse and separation of concerns. The `gke` module can be instantiated for dev, staging, and prod environments with different variable values.

### GKE Cluster Topology

**Regional Cluster**: The control plane runs in multiple zones for high availability. Even if one zone fails, kubectl commands and pod scheduling continue.

**Node Pools**: A single node pool with autoscaling (min 2, max 10 nodes). Nodes use `e2-standard-4` machines (4 vCPUs, 16 GB RAM).

**Autoscaling Behavior**:
- **Cluster Autoscaler**: Adds nodes when pods are pending due to insufficient resources; removes nodes when utilization drops.
- **Horizontal Pod Autoscaler (HPA)**: Scales pod replicas based on CPU/memory utilization (configured in `k8s/base/hpa/`).

### Workload Identity

**Problem**: How do pods authenticate to GCP services (GCS, Secret Manager)?

**Old Approach**: Mount a service account key JSON file into the pod. This is insecure (keys can be leaked) and hard to manage (keys expire).

**Workload Identity Solution**: Kubernetes service accounts are linked to GCP service accounts via IAM binding. When a pod runs with `serviceAccountName: instagram-app`, it automatically receives a GCP identity token without any secrets.

**Flow**:
1. Terraform creates a GCP service account `instagram-clone-prod-app@project.iam.gserviceaccount.com`.
2. IAM binding: Allow `instagram-clone-project1.svc.id.goog[instagram-clone/instagram-app]` to impersonate the GCP SA.
3. The Kubernetes service account `instagram-app` in namespace `instagram-clone` is annotated with the GCP SA email.
4. Pods using this KSA can call `google.auth.default()` and automatically get credentials.

### Artifact Flow

1. **Developer commits code** to GitHub.
2. **Jenkins builds** the Docker image using multi-stage Dockerfile.
3. **Jenkins pushes** the image to Google Container Registry (GCR): `gcr.io/project/auth-service:main-abc123-42`.
4. **Argo CD detects** the new image tag (via Kustomize image override or image updater).
5. **Argo CD applies** the updated manifests to GKE.
6. **Kubernetes pulls** the image from GCR and starts new pods.

---

## 9) CI/CD & GitOps — Conceptual

### Jenkins CI Pipeline

**Why Jenkins?**: Jenkins is a battle-tested CI server with extensive plugin ecosystem, Kubernetes agent support, and flexible scripting. The `Jenkinsfile` defines a declarative pipeline.

**Pipeline Stages**:

1. **Checkout**: Clone the repository, extract commit SHA for image tagging.

2. **Build & Test** (Parallel):
   - **Backend**: `mvn clean verify` compiles code, runs unit tests, and generates coverage reports with JaCoCo.
   - **Frontend**: `npm ci && npm run lint && npm run test && npm run build` installs dependencies, lints, tests, and builds.

3. **Security Scan** (Parallel):
   - **SonarQube (SAST)**: Static analysis for code smells, bugs, and vulnerabilities.
   - **OWASP Dependency Check**: Scans dependencies for known CVEs.
   - **Trivy**: Scans filesystem and Docker images for vulnerabilities.

4. **Build Docker Images**: Multi-stage builds for each service and frontend.

5. **Scan Docker Images**: Trivy scans the built images before pushing.

6. **Push Docker Images**: Authenticated push to GCR using Workload Identity or service account key.

7. **Deploy to Dev** (on `develop` branch): Kustomize updates image tags, kubectl applies manifests, waits for rollout.

8. **Deploy to Staging** (on `release/*` branches): Similar to dev, different overlay.

9. **Deploy to Production** (on `main` branch): **Manual approval gate** before deployment. This prevents accidental production releases.

**Multi-Stage Docker Builds**:

Backend services use a two-stage build:
1. **Build stage** (Maven 3.9, JDK 21): Compiles source, runs tests, produces JAR.
2. **Runtime stage** (JRE 21 Alpine): Copies only the JAR, minimizing image size and attack surface.

Frontend uses a similar pattern:
1. **Build stage** (Node 20): Installs dependencies, runs build.
2. **Runtime stage** (Nginx Alpine): Copies static assets, runs Nginx.

### Argo CD (GitOps)

**Concept**: GitOps means "Git is the source of truth." The desired state of the cluster is defined in Git (Kubernetes manifests). Argo CD continuously compares Git to the live cluster and reconciles differences.

**Benefits**:
- **Auditability**: Every change is a Git commit with author, timestamp, and message.
- **Rollback**: Revert to a previous Git commit to restore previous state.
- **Consistency**: Multiple environments (dev, staging, prod) are defined in Git, eliminating snowflake configurations.

**Application Manifest** (`argocd/application.yaml`):
- **Source**: Git repo URL and path to manifests (`k8s/overlays/prod`).
- **Destination**: Target Kubernetes cluster and namespace.
- **Sync Policy**: Automated pruning, self-healing, retry on failure.

**Sync Strategies**:
- **Auto-sync (dev/staging)**: Any Git change triggers immediate deployment.
- **Manual sync (prod)**: Requires explicit approval in Argo CD UI or CLI.

**Flow**:
1. Jenkins builds and pushes image `auth-service:main-abc123-42`.
2. Jenkins (or a human) updates Kustomize overlay: `kustomize edit set image ...`.
3. Git push triggers Argo CD webhook.
4. Argo CD detects drift: live cluster has `:main-abc123-41`, Git specifies `:main-abc123-42`.
5. Argo CD applies the new manifest, Kubernetes performs rolling update.

---

## 10) Observability & Logging — Conceptual

### Prometheus (Metrics)

**Role**: Time-series database that scrapes metrics from application endpoints.

**How It Works**: Each microservice exposes `/actuator/prometheus` (via Spring Boot Actuator + Micrometer). Prometheus periodically scrapes these endpoints and stores metrics like:
- `http_server_requests_seconds` — Request latency histogram.
- `jvm_memory_used_bytes` — Heap and non-heap memory.
- `hikaricp_connections_active` — Database connection pool utilization.

**Kubernetes Discovery**: Prometheus uses Kubernetes service discovery (`kubernetes_sd_configs`) to find pods with `prometheus.io/scrape: "true"` annotations.

### Grafana (Dashboards)

**Role**: Visualization layer for Prometheus metrics.

**Typical Dashboards**:
- **JVM Overview**: Heap usage, garbage collection pauses, thread counts.
- **HTTP Metrics**: Request rate, error rate, latency percentiles (p50, p95, p99).
- **Database**: Connection pool usage, query latency.
- **Kubernetes**: Pod CPU/memory, node health, HPA scaling events.

### Alertmanager (Alerting)

**Role**: Receives alerts from Prometheus and routes them to notification channels (Slack, email, PagerDuty).

**Example Alert**:
```yaml
- alert: HighLatency
  expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{service="post-service"}[5m])) > 2
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Post-service 95th percentile latency exceeds 2 seconds"
```

If `post-service` responds slowly for 5 minutes, Alertmanager sends a Slack notification.

### Logging

**Options**:

1. **FluentBit → Elasticsearch/Kibana**: FluentBit runs as a DaemonSet, tails container logs, parses JSON, and ships to Elasticsearch. Kibana provides search and visualization.

2. **FluentBit → Loki/Grafana**: Loki is a log aggregation system optimized for Kubernetes. Logs are indexed by labels (pod, namespace), not full-text, making it cheaper than Elasticsearch.

3. **Cloud Logging**: GKE integrates natively with Google Cloud Logging. Logs are automatically shipped without additional agents.

**Best Practice**: Structure logs as JSON with consistent fields (`timestamp`, `level`, `service`, `traceId`, `message`). This enables efficient searching and correlation.

### Distributed Tracing (OpenTelemetry/Jaeger)

**Problem**: A single user request touches multiple services. How do you trace the entire journey?

**Solution**: Each request receives a unique `traceId`. As the request propagates through services, each service logs spans with timing information. Jaeger aggregates these spans into a visual trace.

**Example Trace**:
```
[Frontend] POST /api/posts (150ms)
  └─ [post-service] uploadToGCS (80ms)
  └─ [post-service] saveToDatabase (30ms)
  └─ [post-service] respondToClient (5ms)
```

This reveals that GCS upload is the bottleneck, guiding optimization efforts.

### What to Monitor (Examples)

| Metric | Why It Matters |
|--------|----------------|
| Request latency (p95) | User experience degrades with slow responses. |
| Error rate (5xx) | Indicates application bugs or infrastructure issues. |
| JVM heap usage | Memory leaks cause OOM kills. |
| Database connection pool | Exhausted connections cause request queuing. |
| GCS upload latency | Slow uploads frustrate users. |
| Redis hit ratio | Low ratio means cache is ineffective. |
| Pod restart count | Frequent restarts indicate crashes or OOMs. |

---

## 11) Execution Guide (Conceptual Step-by-Step)

### Step 1: Prepare GCP Project

**What Happens**: You create a GCP project, enable billing, and activate required APIs.

**Why**: GCP services are disabled by default. Terraform will fail if APIs aren't enabled.

**APIs to Enable**:
- Compute Engine (VMs, networks)
- Kubernetes Engine (GKE)
- Cloud SQL Admin
- Secret Manager
- Pub/Sub
- Artifact Registry or Container Registry
- Cloud Storage

**Validation**:
```bash
gcloud services list --enabled | grep -E "compute|container|sqladmin|secretmanager"
```
All required APIs should appear.

### Step 2: Terraform Init, Plan, Apply

**What Happens**: Terraform reads configuration, creates a plan, and provisions resources.

**Why**: Infrastructure-as-code ensures reproducibility. Running `terraform plan` shows what will be created, modified, or destroyed before committing.

**Steps**:
1. Navigate to `terraform/` directory.
2. Create `backend.tf` or configure backend via `-backend-config`.
3. Create `environments/prod/terraform.tfvars` with project-specific values.
4. Run `terraform init` to download providers.
5. Run `terraform plan -var-file=environments/prod/terraform.tfvars` to preview.
6. Run `terraform apply -var-file=environments/prod/terraform.tfvars` to execute.

**Validation**:
- Check GCP Console: VPC, GKE cluster, Cloud SQL instance should appear.
- Terraform outputs show cluster endpoint, database IP, bucket name.

### Step 3: Configure kubectl Access

**What Happens**: You authenticate your local machine to the GKE cluster.

**Why**: kubectl commands need credentials to communicate with the Kubernetes API server.

**Steps**:
```bash
gcloud container clusters get-credentials instagram-clone-prod-gke \
  --region us-central1 \
  --project instagram-clone-project1
```

**Validation**:
```bash
kubectl get nodes
```
Should list GKE nodes in `Ready` state.

### Step 4: Secret Synchronization

**What Happens**: External Secrets Operator (ESO) reads secrets from Secret Manager and creates Kubernetes Secrets.

**Why**: Sensitive values (DB password, JWT secret) shouldn't be committed to Git. Secret Manager is the source of truth.

**Steps**:
1. Install External Secrets Operator (Helm or YAML).
2. Apply `SecretStore` (references GCP Secret Manager).
3. Apply `ExternalSecret` (maps remote secrets to Kubernetes secrets).

**Validation**:
```bash
kubectl get secrets -n instagram-clone
kubectl describe secret app-secrets -n instagram-clone
```
Secrets should have `JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD` keys.

### Step 5: Build and Push Images

**What Happens**: Docker builds container images, and they're pushed to Artifact Registry.

**Why**: Kubernetes pulls images from a registry. Local images don't exist in the cluster.

**Steps**:
1. Authenticate Docker: `gcloud auth configure-docker gcr.io`.
2. Build: `docker build -t gcr.io/project/auth-service:latest backend/auth-service`.
3. Push: `docker push gcr.io/project/auth-service:latest`.
4. Repeat for all services and frontend.

**Why Multi-Stage Builds**: The build stage includes compilers and dependencies (hundreds of MB). The runtime stage includes only the application (tens of MB). Smaller images mean faster pulls and smaller attack surface.

**Validation**:
```bash
gcloud container images list --repository=gcr.io/instagram-clone-project1
```
Images should appear.

### Step 6: Deploy Kubernetes Manifests

**What Happens**: Kubernetes creates namespaces, ConfigMaps, Secrets, Deployments, Services, and Ingress.

**Why**: Manifests define the desired state. Kubernetes reconciles actual state to match.

**Order Matters**:
1. Namespace (`kubectl apply -f k8s/base/namespace.yaml`)
2. External Secrets (`kubectl apply -f k8s/base/secrets/`)
3. ConfigMaps (`kubectl apply -f k8s/base/configmaps/`)
4. Deployments (`kubectl apply -f k8s/base/deployments/`)
5. Services (`kubectl apply -f k8s/base/services/`)
6. Ingress (`kubectl apply -f k8s/base/ingress/`)
7. HPA (`kubectl apply -f k8s/base/hpa/`)

Or use Kustomize: `kubectl apply -k k8s/overlays/prod`.

**Validation**:
```bash
kubectl get pods -n instagram-clone
kubectl get svc -n instagram-clone
kubectl get ingress -n instagram-clone
```
Pods should be `Running`, Services should have ClusterIPs, Ingress should show an external IP after a few minutes.

### Step 7: End-to-End Validation

**What Happens**: You test the application as a real user.

**Checklist**:
1. **DNS/Ingress**: Access `https://instagram.example.com` (or the ingress IP). Frontend should load.
2. **Registration**: Create an account. Check auth-service logs for success.
3. **Login**: Log in, verify JWT is received.
4. **Create Post**: Upload an image. Check GCS bucket for the file.
5. **Feed**: Posts from followed users should appear.
6. **Like/Comment**: Interactions should persist.
7. **Database**: Connect to Cloud SQL and verify tables have data.

### Step 8: Enable Monitoring

**What Happens**: Deploy Prometheus and Grafana to collect and visualize metrics.

**Steps**:
1. Apply Prometheus ConfigMap and Deployment (`monitoring/prometheus/`).
2. Apply Grafana ConfigMap and Deployment (`monitoring/grafana/`).
3. Port-forward to access: `kubectl port-forward svc/grafana 3000:3000 -n monitoring`.
4. Open http://localhost:3000, import dashboards.

**Validation**:
- Prometheus targets page (`/targets`) shows all services as UP.
- Grafana dashboards display JVM and HTTP metrics.

### Step 9: Rollback Strategy

**If Something Goes Wrong**:

1. **Failed Deployment**: `kubectl rollout undo deployment/auth-service -n instagram-clone` reverts to previous ReplicaSet.

2. **Bad Code in Production**: Revert the Git commit, push to main, let Argo CD sync the previous version.

3. **Terraform Disaster**: Use `terraform state` commands to import or remove resources manually. Point-in-time recovery for Cloud SQL.

**Common Troubleshooting**:
- **Pods CrashLoopBackOff**: Check logs with `kubectl logs <pod>`. Often database connection failures or missing secrets.
- **ImagePullBackOff**: Registry authentication issue. Verify Workload Identity or image pull secrets.
- **Ingress No IP**: Wait 5-10 minutes. Check `kubectl describe ingress`.

---

## 12) Example Workflows (Narrative Form)

### Creating a Post (Full Flow)

Alice opens the Instagram Clone app on her phone. She taps the "+" button and is taken to the Create Post page. The React component renders a file picker and a caption text area. Alice selects a photo of her cat from her gallery. The frontend reads the file, generates a preview using `URL.createObjectURL()`, and displays it on screen.

Alice types "Meet my cat Whiskers! 🐱" as the caption and adds hashtags #cat and #cute. She taps "Share." The frontend creates a `FormData` object, appends the image file, caption, and hashtags, and calls `postService.createPost()`. The Axios client sends a POST request to `/api/v1/posts` with `Content-Type: multipart/form-data` and Alice's JWT in the Authorization header.

The GKE Ingress receives the request and routes it to one of the post-service pods. The post-service first validates Alice's JWT by verifying the signature against the secret from Secret Manager. Satisfied that Alice is who she claims to be, the service proceeds to handle the upload.

The service uses the Google Cloud Storage client library to stream the image bytes directly to the media bucket. Because the pod runs with Workload Identity, it doesn't need an explicit key file—the GCP client library automatically obtains credentials. The image lands at `gs://instagram-clone-prod-media/posts/alice123/uuid.jpg`. GCS returns the public URL.

Next, the post-service constructs a `Post` entity with the image URL, caption, hashtags, Alice's user ID, and current timestamp. It calls `postRepository.save(post)`, which triggers Hibernate to issue an INSERT statement to the Cloud SQL `instagram_post` database. The database assigns an auto-generated ID.

The service returns a 201 Created response with the post JSON. The frontend receives it, displays a "Post created successfully!" toast, and navigates Alice to her new post's detail page, where she can see her cat photo with likes and comments sections ready.

### Following/Unfollowing and Feed Updates

Bob discovers Alice's profile through the Explore page. He taps her username and lands on her profile, which displays her bio, post count, and a grid of her photos. Bob is intrigued and taps the "Follow" button.

The frontend sends `POST /api/v1/users/alice/follow`. The user-service receives the request, decodes Bob's JWT, and verifies he isn't already following Alice by querying the `follows` table. Finding no existing relationship, it inserts a new row: `{ follower_id: 'bob123', following_id: 'alice456' }`. The service returns 201 Created.

The frontend updates Alice's follower count (incrementing optimistically via React Query) and changes the button to "Following" (now styled differently).

Later, Bob opens his home feed. The frontend calls `GET /api/v1/feed`. The feed-service checks Redis for `feed:bob`. The cache misses because Bob's follow list changed. The service queries the user-service for Bob's following list (now including Alice), then queries the post-service for recent posts from those users. It receives posts from Carol, Dave, and now Alice. The feed-service sorts them by timestamp, caches the result in Redis with a 5-minute TTL, and returns the list.

Bob scrolls and sees Alice's cat photo in his timeline! He double-taps to like it.

If Bob decides to unfollow Alice later, he taps "Following," confirms the unfollow, and the frontend calls `DELETE /api/v1/users/alice/follow`. The user-service deletes the row. The next time Bob's feed is fetched, the cache will miss (or the feed-service invalidates it proactively), and Alice's posts will no longer appear.

### Real-Time Chat Message Flow (Conceptual)

Note: The current codebase doesn't include a chat-service, but here's how it would work architecturally.

Carol wants to send a message to Dave. She opens the Direct Messages section and selects Dave's conversation. The frontend establishes a WebSocket connection to the chat-service.

Carol types "Hey, great photo!" and hits send. The frontend sends a WebSocket message with payload `{ to: 'dave', text: 'Hey, great photo!' }`. The chat-service receives it, validates Carol's identity via the JWT embedded in the WebSocket handshake, and persists the message to Firestore (chosen over PostgreSQL because Firestore excels at real-time synchronization and doesn't require schema migrations for chat documents).

The chat-service then checks if Dave is online. If Dave has an active WebSocket connection, the service pushes the message to Dave's connection. Dave's frontend receives it and displays the message with a notification sound.

If Dave is offline, the message is still saved in Firestore. When Dave opens the app later, his frontend fetches unread messages via REST (`GET /api/v1/messages/unread`), displaying them as notifications.

---

## 13) Troubleshooting — Conceptual

### Secrets Not Available to Pods

**Symptoms**: Pods crash with errors like "JWT_SECRET environment variable not set" or "Connection refused to database."

**Diagnosis**:
1. Check if the Kubernetes secret exists: `kubectl get secret app-secrets -n instagram-clone`.
2. If missing, check External Secrets: `kubectl get externalsecret app-secrets -n instagram-clone`.
3. Examine ESO logs: `kubectl logs -l app=external-secrets -n external-secrets`.

**Common Causes**:
- SecretStore misconfigured (wrong project ID, missing Workload Identity binding).
- Secret names in Secret Manager don't match `remoteRef.key`.
- ESO not installed or CRDs missing.

**Remediation**: Fix SecretStore configuration, verify IAM permissions for ESO's service account, ensure secret names match.

### Cloud SQL Connection Issues

**Symptoms**: Services timeout or log "Connection refused to 10.133.1.2:5432."

**Diagnosis**:
1. Verify Cloud SQL instance is running in GCP Console.
2. Check if private IP is correctly configured: instance should have no public IP.
3. Verify VPC peering: `gcloud services vpc-peerings list`.
4. Test connectivity from a pod: `kubectl run test --rm -it --image=postgres -- psql -h 10.133.1.2 -U postgres`.

**Common Causes**:
- Private services connection not established.
- Firewall rules blocking internal traffic.
- Wrong IP in ConfigMap.

**Remediation**: Re-apply Terraform VPC module, verify `google_service_networking_connection` exists, update ConfigMap with correct IP.

### Image Pull Failures

**Symptoms**: Pods stuck in `ImagePullBackOff`. Events show "unauthorized" or "not found."

**Diagnosis**:
1. `kubectl describe pod <pod-name>` — Read events at the bottom.
2. Verify image exists: `gcloud container images describe gcr.io/project/auth-service:tag`.
3. Check image pull secrets: `kubectl get secrets` — Look for `gcr-json-key` or Workload Identity setup.

**Common Causes**:
- Image tag typo.
- Image not pushed to registry.
- GKE nodes lack permission to pull from GCR (Workload Identity misconfigured).

**Remediation**: Verify image name and tag, push missing image, or update GKE node service account IAM bindings.

### Health Check Mismatches

**Symptoms**: Pods marked "Unhealthy," load balancer returns 502 errors.

**Diagnosis**:
1. Check readiness/liveness probe configuration in Deployment.
2. Compare with actual endpoint: `kubectl exec <pod> -- curl localhost:8080/actuator/health`.
3. Examine GCE health check in Cloud Console.

**Common Causes**:
- Probe path `/health` but actuator exposes `/actuator/health`.
- Wrong port number.
- Application takes longer to start than `initialDelaySeconds`.

**Remediation**: Align probe path with actual endpoint, increase initialDelaySeconds if startup is slow.

### General Debugging Commands

```bash
# Logs for a specific pod
kubectl logs deployment/post-service -n instagram-clone --follow

# Describe pod (events, conditions)
kubectl describe pod <pod-name> -n instagram-clone

# Shell into a running pod
kubectl exec -it <pod-name> -n instagram-clone -- /bin/sh

# Check HPA status
kubectl get hpa -n instagram-clone

# View Terraform state
terraform state list
terraform state show module.cloud_sql.google_sql_database_instance.postgres

# Test database connectivity
kubectl run pgtest --rm -it --image=postgres:15 -- psql postgresql://user:pass@host:5432/db
```

---

## 14) Quick Start Runbook (1-Page Summary)

### Prerequisites
- GCP project with billing enabled
- `gcloud`, `terraform`, `kubectl`, `docker` installed
- APIs enabled: Compute, GKE, Cloud SQL, Secret Manager, GCS

### Deployment Steps

| Step | Command | Validation |
|------|---------|------------|
| 1. Init Terraform | `cd terraform && terraform init` | No errors |
| 2. Plan | `terraform plan -var-file=environments/prod/terraform.tfvars` | Review resources |
| 3. Apply | `terraform apply -var-file=environments/prod/terraform.tfvars` | Outputs show IPs |
| 4. Get credentials | `gcloud container clusters get-credentials <cluster> --region <region>` | `kubectl get nodes` shows nodes |
| 5. Install ESO | `kubectl apply -f https://...external-secrets.yaml` | ESO pods running |
| 6. Apply secrets | `kubectl apply -f k8s/base/secrets/` | `kubectl get secrets` shows `app-secrets` |
| 7. Apply all manifests | `kubectl apply -k k8s/overlays/prod` | All pods Running |
| 8. Wait for ingress | `kubectl get ingress -n instagram-clone -w` | External IP assigned |
| 9. Test | `curl https://<external-ip>/api/v1/health` | 200 OK |
| 10. Monitor | Port-forward Grafana, check dashboards | Metrics flowing |

### Rollback
```bash
# Revert deployment
kubectl rollout undo deployment/<service> -n instagram-clone

# Revert Terraform
terraform apply -target=module.<module> -var-file=...
```

### Emergency Contacts
- **GKE Issues**: Check GKE cluster in Cloud Console
- **Database Issues**: Cloud SQL Admin console
- **DNS Issues**: Verify managed certificate status

---

## 15) Repository Hotspots (Manual Inspection Checklist)

### Critical Directories

| Path | What to Inspect |
|------|-----------------|
| `terraform/main.tf` | Module composition, provider config |
| `terraform/modules/gke/main.tf` | Cluster config, node pools, Workload Identity |
| `terraform/modules/cloud-sql/main.tf` | Instance settings, private IP, backups |
| `terraform/modules/vpc/main.tf` | Subnets, NAT, private service access |
| `terraform/environments/prod/terraform.tfvars` | Environment-specific values |

### Backend Services

| Path | What to Inspect |
|------|-----------------|
| `backend/pom.xml` | Dependency versions, module list |
| `backend/common/` | Shared DTOs, security filters |
| `backend/auth-service/src/.../controller/` | API endpoints |
| `backend/auth-service/src/.../service/` | Business logic |
| `backend/auth-service/Dockerfile` | Build stages, JVM options |
| `backend/*/src/main/resources/application.yml` | Spring configuration |
| `backend/*/src/main/resources/db/migration/` | Flyway migrations |

### Frontend

| Path | What to Inspect |
|------|-----------------|
| `frontend/package.json` | Dependencies, scripts |
| `frontend/src/App.tsx` | Routing setup |
| `frontend/src/services/api.ts` | Axios config, interceptors |
| `frontend/src/hooks/useAuthStore.ts` | Auth state management |
| `frontend/Dockerfile` | Multi-stage build |
| `frontend/nginx.conf` | Routing, security headers |

### Kubernetes

| Path | What to Inspect |
|------|-----------------|
| `k8s/base/kustomization.yaml` | Resource composition |
| `k8s/base/deployments/*.yaml` | Pod specs, probes, resources |
| `k8s/base/ingress/ingress.yaml` | Path routing, TLS |
| `k8s/base/hpa/hpa.yaml` | Autoscaling thresholds |
| `k8s/base/network-policies/` | Traffic restrictions |
| `k8s/base/secrets/external-secrets.yaml` | Secret sync config |
| `k8s/overlays/prod/` | Production-specific patches |

### CI/CD

| Path | What to Inspect |
|------|-----------------|
| `cicd/Jenkinsfile` | Pipeline stages, agents |
| `argocd/application.yaml` | GitOps config, sync policy |

### Monitoring

| Path | What to Inspect |
|------|-----------------|
| `monitoring/prometheus/prometheus.yaml` | Scrape configs, alerting rules |
| `monitoring/grafana/grafana.yaml` | Dashboard provisioning |

---

## Conclusion

This documentation has walked you through the Instagram Clone from architecture to execution. The system demonstrates industry best practices: microservices for scalability, managed databases for reliability, GitOps for auditability, and comprehensive observability for operational excellence. As you explore the codebase, use this document as a map—each section points to concrete files and explains the reasoning behind design choices.

For questions or contributions, refer to the repository's README and contributing guidelines. Happy coding!

---

*Document generated: December 4, 2025*
*Target Audience: Engineers onboarding to the Instagram Clone project*
*Word Count: ~9,500 words*
