# MoodCopilot CI/CD Pipeline

## Summary

Built a complete automated CI/CD pipeline using GitHub Actions, replacing the manual SSH + deploy.sh workflow.

## What was done

### 3 GitHub Actions workflows
1. **CI** (`ci.yml`) — PRs and non-main pushes: backend Maven tests (with MySQL/Redis services), frontend vue-tsc + build, UniApp type-check + mp-weixin build, Docker build verification, Trivy security scanning
2. **Deploy** (`deploy.yml`) — main branch pushes: verify build → build & push Docker images to GHCR (with buildx cache) → SSH deploy → health check (30 retries) → auto-rollback on failure → old image cleanup (keep 10)
3. **Backup** (`backup.yml`) — daily at 03:00 Beijing time: SSH execute backup.sh + gzip integrity verification

### Infrastructure changes
- **Backend Dockerfile**: converted to multi-stage build (Maven build + JRE runtime) so CI builds from source
- **docker-compose.yml**: added `image: ghcr.io/...` fields to backend & web services, keeping `build:` for local dev
- **deploy.sh**: rewritten to support `--pull` (CI/CD) and `--build` (local) modes, with health check and auto-rollback
- Added `.dockerignore` files for backend and frontend

### Configuration guide
- `docs/cicd-setup.md` with all required GitHub Secrets, GHCR setup, SSH key generation, and first-use instructions

## Key decisions
- GHCR (GitHub Container Registry) for image storage — no extra service needed, uses GITHUB_TOKEN
- Image tagged with commit SHA for traceability + `latest` for convenience
- Auto-rollback restores previous image tag if health check fails after 5 minutes
- Backup workflow replaces manual cron setup
- Docker layer caching via `type=gha` for faster builds
