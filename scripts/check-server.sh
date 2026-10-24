#!/usr/bin/env bash
# Read-only remote checks. Also executed as bash -s over SSH stdin.
set -euo pipefail
cd "${1:?Missing project directory}"
echo 'Architecture:'
uname -m
echo 'Source checkout (not the running image revision):'
git branch --show-current
git log -1 --oneline
git status --short
echo 'Deployment prerequisites:'
command -v flock
docker compose version --short
echo 'Services:'
docker compose ps --format '{{.Service}} {{.State}} {{.Health}}'
echo 'Backend health:'
docker compose exec -T backend curl --fail --silent --show-error http://localhost:18080/api/health </dev/null
echo
echo 'Disk space:'
df -h .
echo 'Recorded deployment revision:'
if [[ -f .deploy/current-revision ]]; then
  current_revision=$(cat .deploy/current-revision)
  [[ "$current_revision" =~ ^[a-f0-9]{40}$ ]]
  printf '%s\n' "$current_revision"
else
  echo 'No recorded image deployment'
fi
echo 'Application image revisions:'
for service in backend web; do
  container_id=$(docker compose ps -q "$service")
  [[ -n "$container_id" ]]
  image_id=$(docker inspect --format '{{.Image}}' "$container_id")
  printf '%s ' "$service"
  docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image_id"
done
echo SERVER_CHECK_COMPLETE
