# Terraform Errors & Lessons — Instagram Clone (prod)

This document summarizes the Terraform errors encountered when deploying the `prod` environment, explains root causes, and lists concrete prevention and runbook steps to avoid repeating the same problems.

**Location**: `terraform/` (root module), `modules/api-gateway/`, `environments/prod/terraform.tfvars`

---

## Short summary of the key errors encountered

- State lock failure when acquiring the GCS backend lock (Error 412 / precondition failed).
- Resource conflict: GKE cluster already existed in GCP while Terraform state did not (Error 409 alreadyExists).
- API Gateway OpenAPI validation errors (x-google-backend used at path level, invalid `apiKey` usage).
- API Gateway rejection of backend address: "cannot route requests by IP Address" (backend must be a hostname / domain or supported service).
- Initial regional quota failure (SSD_TOTAL_GB exceeded) before reducing node/disk sizes.

## Detailed messages and root causes

1) State lock failure (GCS precondition NotMet / Error 412)
  - Message: `writing "gs://.../prod.tflock" failed: googleapi: Error 412: At least one of the pre-conditions you specified did not hold., conditionNotMet`
  - Root cause: A prior Terraform operation acquired a lock and either crashed, or the lock was left in an inconsistent state. Remote GCS backend enforces object generation preconditions; concurrent or partially-finished writes can hit 412.

2) Already exists — GKE cluster (Error 409)
  - Message: `googleapi: Error 409: Already exists: projects/.../clusters/instagram-clone-prod-gke`.
  - Root cause: A previous apply partially created the cluster (or it was created manually). The Terraform state file did not contain the cluster resource, so a fresh apply attempted to create it again.

3) OpenAPI validation errors (ApiConfig convert failure)
  - Message snippets: `Extension x-google-backend was specified at a path level, which is not supported.` and `apiKey 'Authorization' is ignored. Only apiKey with 'name' as 'key' and 'in' as 'query', or 'name' as 'api_key' ... are supported.`
  - Root cause: The OpenAPI `x-google-backend` extension was used in an unsupported way (path-level vs op/top-level expectations) and the security definition did not match API Gateway's expectations.

4) Backend IP routing forbidden
  - Message: `Backend URL "https://35.226.3.44/api/auth" is forbidden: cannot route requests by IP Address.`
  - Root cause: Google API Gateway does not accept bare IP addresses as backends. It requires a resolvable hostname (for example, a DNS name fronted by an external load balancer or Cloud Run endpoint). Using the cluster IP directly is not valid.

5) Quota exceeded (SSD_TOTAL_GB)
  - Message: provider or GCP API error indicating requested SSD GB > regional quota.
  - Root cause: requesting larger disks / more nodes than available quota. Solution: reduce requested PD SSDs, request quota increase, or choose different zone/region.

## Immediate fixes applied during the run

- Used `terraform force-unlock` (after verifying no active operator) to clear stale state lock.
- Imported the existing GKE cluster into Terraform state using `terraform import module.gke.google_container_cluster.primary projects/.../locations/us-central1/clusters/instagram-clone-prod-gke` so Terraform managed the existing resource instead of trying to recreate it.
- Fixed the OpenAPI spec: moved/adjusted `x-google-backend` usage to operation-level where required and changed the API key header name to a supported one (`x-api-key`), then re-run `terraform apply`.
- Disabled the API Gateway module in the `prod` tfvars temporarily to allow the rest of the infra to apply, because API Gateway requires a domain/hostname backend and additional changes.
- Reduced GKE node count / disk size in `environments/prod/terraform.tfvars` to fit regional SSD quota.

## Prevention checklist (pre-apply)

Run these checks before `terraform apply` in shared or production projects:

- Check remote state lock and active operators:
  - `gsutil ls -L gs://<bucket>/<path>.tflock` (inspect object metadata)
  - Use `terraform plan` and avoid concurrent apply operations.
- Detect existing resources in the cloud that Terraform may attempt to create:
  - `gcloud container clusters list --region <region>`
  - `gcloud sql instances list --filter="name:..."`
  - If a resource exists but is not in state => `terraform import` it (see runbook below).
- Validate quotas and limits:
  - Check quotas in Cloud Console or `gcloud compute regions describe <region> --format='json(quotas)'` and compare requested PD SSD GB.
  - Reduce sizes or request quota increases before apply.
- Validate OpenAPI / API Gateway config before applying:
  - Lint and validate `openapi.yaml` with a tool (for example `swagger-cli validate` or `openapi-generator`), and ensure `x-google-backend` is used per API Gateway expectations.
  - Ensure backend is a hostname (DNS) or a supported service URL (not a bare node IP).
- Use provider and module version pinning, and run `terraform init -upgrade` in a controlled manner.

## Runbook: common remediation commands

- Unlock remote state (only after confirming no active operator):
```bash
cd terraform
terraform force-unlock -force <LOCK_ID>
```

- Import an existing resource into Terraform state (example: GKE cluster):
```bash
cd terraform
terraform import -var-file=environments/prod/terraform.tfvars \
  module.gke.google_container_cluster.primary \
  projects/<PROJECT>/locations/<REGION>/clusters/<CLUSTER_NAME>
```

- Examine a stuck cluster or nodepool:
```bash
gcloud container clusters describe <CLUSTER> --region <REGION>
gcloud container node-pools list --cluster <CLUSTER> --region <REGION>
```

- Check quotas:
```bash
gcloud compute regions describe us-central1 --format='json(quotas)'
gcloud compute project-info describe --format='json(quotas)'
```

## API Gateway / OpenAPI guidance

- Do not use an IP address as a backend for API Gateway. Instead:
  - Expose your GKE service via an external HTTP(S) Load Balancer with a DNS name, or use Cloud Run / Cloud Functions with a domain.
  - Use the load balancer's hostname, Cloud Run service URL, or a Cloud Endpoint that resolves to a hostname.
- `x-google-backend` placement:
  - Place backend extents at operation or top-level according to API Gateway docs. Avoid incorrect path-level placement that OpenAPI-to-service-config conversion rejects.
- API key configuration:
  - Use `securityDefinitions` that API Gateway supports (for example `name: x-api-key` and `in: header`) and ensure every operation's `security` references an `api_key` entry if you expect API key enforcement.

## Suggested CI / Automation additions

- Add pre-apply checks in CI:
  - `terraform fmt && terraform validate`
  - `terraform plan -var-file=...` and fail on unexpected adds in protected envs
  - OpenAPI lint step: `swagger-cli validate modules/api-gateway/openapi.yaml` or `openapi-cli validate`.
  - Quota check script (optional) to compare requested PD SSD or CPUs against quotas.

## Final notes / lessons learned

- Always check for existing resources when running Terraform against a project used by multiple people or previous attempts. The `terraform import` path is the safe way to bring existing resources into state.
- Avoid using raw IP addresses for external-facing backends — use a proper load balancer / DNS / service URL.
- Make small, incremental changes for prod (smaller node pools / disks) if quota is tight, and request quota increases proactively for intended production specs.

---

File created by automation on deployment run — update this document if you identify additional recurring errors.
