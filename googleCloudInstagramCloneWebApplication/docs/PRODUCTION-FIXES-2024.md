# Production Deployment Fixes - Session Summary

## Issues Fixed (December 2024)

This document summarizes critical fixes applied during the production deployment session.

---

## Issue 1: 500 Internal Server Error on Signup

### Symptoms
- User clicks "Sign Up" on frontend
- Request returns 500 error
- Auth service logs show: `io.jsonwebtoken.io.DecodingException: Illegal base64 character: '<'`

### Root Cause
The `JWT_SECRET` stored in Kubernetes secrets contained invalid base64 characters.

The `JwtTokenProvider.java` in `backend/common` uses:
```java
Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))
```

This requires a properly base64-encoded secret key.

### Solution
```bash
# Generate proper base64-encoded JWT secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')

# Update Kubernetes secret
kubectl -n instagram-clone delete secret app-secrets
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  # ... other keys
```

---

## Issue 2: 403 Forbidden on API Calls

### Symptoms
- Requests to `/api/v1/auth/register` return 403
- Curl shows request being rejected

### Root Cause
API Gateway (nginx) was configured to route `/api/auth/*` but frontend calls `/api/v1/auth/*`.

The path mismatch caused requests to go to the wrong handler or be rejected.

### Solution
Updated nginx ConfigMap to handle `/api/v1/*` paths:

```nginx
location /api/v1/auth/ {
    proxy_pass http://auth_backend/api/v1/auth/;
    # ... headers
}
```

Key insight: The nginx `proxy_pass` must preserve the `/api/v1/` prefix since Spring controllers expect that path.

---

## Issue 3: Database Authentication Failed

### Symptoms
- Services fail to connect to PostgreSQL
- Logs show: `FATAL: password authentication failed for user "instagram_prod"`

### Root Cause
Two issues:
1. `DB_USERNAME` in secret was `instagram_prod` but Cloud SQL user is `instagram_app`
2. Password in Kubernetes secret didn't match Cloud SQL user password

### Solution
```bash
# Fix username in secret
kubectl -n instagram-clone delete secret app-secrets
kubectl -n instagram-clone create secret generic app-secrets \
  --from-literal=DB_USERNAME="instagram_app" \
  --from-literal=DB_PASSWORD="$DB_PASS" \
  # ... other keys

# Sync Cloud SQL password
gcloud sql users set-password instagram_app \
  --instance=instagram-clone-prod-postgres \
  --password="$DB_PASS" \
  --project=$PROJECT_ID
```

---

## Configuration Files Changed

### 1. API Gateway ConfigMap
**File**: Applied via kubectl (not in git)  
**Change**: Added `/api/v1/*` route handlers

### 2. Kubernetes Secret (app-secrets)
**Change**: Recreated with:
- Valid base64 JWT_SECRET
- Correct DB_USERNAME (instagram_app)
- Synced DB_PASSWORD

### 3. Terraform Variables
**File**: `terraform/environments/prod/terraform.tfvars`  
**Changes**:
```hcl
gke_min_node_count   = 2
gke_max_node_count   = 10
gke_disk_size_gb     = 100
redis_memory_size_gb = 10
```

---

## Verification Commands

```bash
# Test signup endpoint
curl -X POST http://34.54.24.2/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!","username":"testuser","fullName":"Test User"}'

# Expected: 201 with JWT token

# Test login endpoint
curl -X POST http://34.54.24.2/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!"}'

# Expected: 200 with JWT token
```

---

## Key Learnings

1. **JWT Libraries Require Proper Encoding**: The JJWT library's `Decoders.BASE64.decode()` will fail on any non-base64 characters. Always generate secrets with `openssl rand -base64 64`.

2. **API Path Consistency**: Frontend axios baseURL and backend controller mappings must align with the API gateway routes. In this case:
   - Frontend: `baseURL: '/api/v1'`
   - Controllers: `@RequestMapping("/api/v1/auth")`
   - Gateway: Must route `/api/v1/*` → backend `/api/v1/*`

3. **Credential Synchronization**: Kubernetes secrets and cloud database credentials must be kept in sync. After changing one, always update the other.

4. **Service Restart Required**: After changing ConfigMaps or Secrets, services must be restarted:
   ```bash
   kubectl -n instagram-clone rollout restart deployment --all
   ```

---

## Current Production Status

| Component | Status |
|-----------|--------|
| Frontend | ✅ Running |
| Auth Service | ✅ Running |
| User Service | ✅ Running |
| Post Service | ✅ Running |
| Feed Service | ✅ Running |
| Comment Service | ✅ Running |
| Like Service | ✅ Running |
| API Gateway | ✅ Running |
| Cloud SQL | ✅ Connected |
| Redis | ✅ Connected |

**Application URL**: http://34.54.24.2/
