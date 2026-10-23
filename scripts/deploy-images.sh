#!/usr/bin/env bash
# Usage: bash scripts/deploy-images.sh /absolute/path/MoodCopilot owner commit-sha [registry|preloaded]
# Also runs via SSH stdin; never reads or transfers the server's .env contents.
set -euo pipefail
umask 077

project_dir=${1:?Missing project directory}
owner=${2:?Missing registry owner}
revision=${3:?Missing commit SHA}
image_source=${4:-registry}
[[ "$project_dir" == /* && -d "$project_dir" ]] || { echo 'Invalid project directory' >&2; exit 1; }
[[ "$owner" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo 'Invalid registry owner' >&2; exit 1; }
[[ "$revision" =~ ^[a-f0-9]{40}$ ]] || { echo 'Expected a full commit SHA' >&2; exit 1; }
[[ "$image_source" == registry || "$image_source" == preloaded ]] || { echo 'Invalid image source' >&2; exit 1; }
cd "$project_dir"
[[ -f docker-compose.yml ]] || { echo 'docker-compose.yml is missing' >&2; exit 1; }
command -v flock >/dev/null
mkdir -p .deploy
exec 9>.deploy/deploy.lock
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }

work_dir=''
registry_dir=''
cleanup() {
  [[ -z "$work_dir" ]] || rm -rf -- "$work_dir"
  [[ -z "$registry_dir" ]] || rm -rf -- "$registry_dir"
}
trap cleanup EXIT

# Use the workflow's short-lived token for private GHCR images. Credentials live
# only in a private temporary directory, not the server's persistent Docker config.
if [[ "$image_source" == registry && -n "${GHCR_TOKEN:-}" ]]; then
  registry_dir=$(mktemp -d "$PWD/.deploy/auth.XXXXXX")
  export DOCKER_CONFIG="$registry_dir"
  printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?Missing registry user}" --password-stdin
  unset GHCR_TOKEN
fi

compose=(docker compose --project-directory "$PWD" -f "$PWD/docker-compose.yml")
# Require an existing installation, and capture the actual local image IDs for rollback.
backend_id=$("${compose[@]}" ps -q backend)
web_id=$("${compose[@]}" ps -q web)
[[ -n "$backend_id" && -n "$web_id" ]] || {
  echo 'An existing backend + web installation is required; bootstrap it manually first.' >&2
  exit 1
}
old_backend=$(docker inspect --format '{{.Image}}' "$backend_id")
old_web=$(docker inspect --format '{{.Image}}' "$web_id")
[[ "$old_backend" =~ ^sha256:[a-f0-9]{64}$ && "$old_web" =~ ^sha256:[a-f0-9]{64}$ ]]

work_dir=$(mktemp -d "$PWD/.deploy/release.XXXXXX")
cat > "$work_dir/next.yml" <<EOF
services:
  backend:
    image: ghcr.io/$owner/moodcopilot-backend:$revision
  web:
    image: ghcr.io/$owner/moodcopilot-frontend:$revision
EOF
cat > "$work_dir/previous.yml" <<EOF
services:
  backend:
    image: $old_backend
  web:
    image: $old_web
EOF

# Both image sources must be complete before changing either container. Preloaded
# images arrive over the workflow's verified SSH connection; validate their SHA
# labels under the same deployment lock instead of returning to a slow registry.
if [[ "$image_source" == registry ]]; then
  "${compose[@]}" -f "$work_dir/next.yml" pull backend web
else
  for service in backend frontend; do
    image="ghcr.io/$owner/moodcopilot-$service:$revision"
    image_revision=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
    [[ "$image_revision" == "$revision" ]] || { echo 'Preloaded image revision mismatch' >&2; exit 1; }
  done
fi

start_pair() {
  "${compose[@]}" -f "$1" up -d --no-deps --no-build --pull never --force-recreate \
    --wait --wait-timeout 180 backend web || return 1
  # Verify static content AND the nginx -> backend path, not just a running container.
  "${compose[@]}" exec -T web wget -q -O /dev/null http://127.0.0.1:3000/ || return 1
  "${compose[@]}" exec -T web wget -q -O /dev/null http://127.0.0.1:3000/api/health || return 1
  # The public nginx keeps upstream addresses in memory; refresh them after recreation.
  if [[ -n "$("${compose[@]}" ps -q frontend)" ]]; then
    "${compose[@]}" exec -T frontend nginx -t || return 1
    "${compose[@]}" exec -T frontend nginx -s reload || return 1
  fi
}

if start_pair "$work_dir/next.yml"; then
  cp "$work_dir/previous.yml" .deploy/previous-images.yml
  cp "$work_dir/next.yml" .deploy/current-images.yml
  printf '%s\n' "$revision" > .deploy/current-revision
  echo "Deployment healthy: $revision"
else
  echo 'Deployment failed; restoring the previous local images.' >&2
  if start_pair "$work_dir/previous.yml"; then
    echo 'Previous services restored. Deployment remains failed.' >&2
  else
    echo 'ROLLBACK FAILED: inspect backend/web on the server immediately.' >&2
  fi
  exit 1
fi
