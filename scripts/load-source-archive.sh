#!/usr/bin/env bash
# Executed via bash -c; stdin contains a checked commit's source archive.
set -euo pipefail
umask 077
project_dir=${1:?Missing project directory}
owner=${2:?Missing registry owner}
revision=${3:?Missing revision}
archive_hash=${4:?Missing archive hash}
archive_size=${5:?Missing archive size}
[[ "$project_dir" == /* && -d "$project_dir" ]]
[[ "$owner" =~ ^[a-z0-9][a-z0-9-]*$ && "$revision" =~ ^[a-f0-9]{40}$ ]]
[[ "$archive_hash" =~ ^[a-f0-9]{64}$ && "$archive_size" =~ ^[0-9]+$ ]]
cd "$project_dir"
[[ -f docker-compose.yml ]] || { echo 'Existing installation required' >&2; exit 1; }
mkdir -p .deploy
exec 8>.deploy/source-build.lock
flock -n 8 || { echo 'Another source build is running' >&2; exit 1; }
source_dir=$(mktemp -d "$PWD/.deploy/source-build.XXXXXX")
trap 'rm -rf -- "$source_dir"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
cat > "$source_dir/source.tar.gz"
[[ "$(wc -c < "$source_dir/source.tar.gz")" == "$archive_size" ]] || { echo 'Source archive length mismatch' >&2; exit 1; }
actual_hash=$(sha256sum "$source_dir/source.tar.gz")
[[ "${actual_hash%% *}" == "$archive_hash" ]] || { echo 'Source archive checksum mismatch' >&2; exit 1; }
tar -xzf "$source_dir/source.tar.gz" -C "$source_dir"
[[ -f "$source_dir/backend/moodcopilot/pom.xml" && -f "$source_dir/frontend/Dockerfile" ]]
echo "[source] received and verified $archive_size bytes"
for service in backend frontend; do
  image="ghcr.io/$owner/moodcopilot-$service:$revision"
  if existing_revision=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image" 2>/dev/null) && [[ "$existing_revision" == "$revision" ]]; then
    echo "[source] already built $image"
    continue
  fi
  echo "[source] building $service from $revision"
  if [[ "$service" == backend ]]; then
    # CI has already run tests for this commit. Reuse only dependencies, never old JARs.
    mkdir -p "$HOME/.m2"
    docker run --rm -v "$source_dir/backend/moodcopilot:/app" -v "$HOME/.m2:/root/.m2" \
      -w /app maven:3.9-eclipse-temurin-21 \
      mvn --batch-mode --no-transfer-progress clean package -Dmaven.test.skip=true </dev/null
    context="$source_dir/backend/moodcopilot"
    dockerfile="$context/Dockerfile"
  else
    context="$source_dir"
    dockerfile="$context/frontend/Dockerfile"
  fi
  docker build --label "org.opencontainers.image.revision=$revision" \
    --file "$dockerfile" --tag "$image" "$context" </dev/null
  built_revision=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
  [[ "$built_revision" == "$revision" ]] || { echo 'Built image revision mismatch' >&2; exit 1; }
done
# Container replacement remains exclusively in deploy-images.sh, with its lock and rollback.
echo "[source] verified both fixed-SHA images"
