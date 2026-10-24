#!/usr/bin/env bash
# Executed via bash -c; stdin contains an archive, never shell commands.
set -euo pipefail
umask 077
project_dir=${1:?Missing project directory}
image=${2:?Missing image}
revision=${3:?Missing revision}
archive_hash=${4:?Missing archive hash}
archive_size=${5:?Missing archive size}
[[ "$project_dir" == /* && -d "$project_dir" ]]
[[ "$revision" =~ ^[a-f0-9]{40}$ && "$archive_hash" =~ ^[a-f0-9]{64}$ && "$archive_size" =~ ^[0-9]+$ ]]
[[ "$image" =~ ^ghcr[.]io/[a-z0-9-]+/moodcopilot-(backend|frontend):$revision$ ]]
cd "$project_dir"
mkdir -p .deploy
receive_dir=$(mktemp -d "$PWD/.deploy/incoming.XXXXXX")
trap 'rm -rf -- "$receive_dir"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
echo "[transfer] receiving $image ($archive_size bytes)"
df -h .
cat > "$receive_dir/image.tar.gz"
actual_size=$(wc -c < "$receive_dir/image.tar.gz")
[[ "$actual_size" == "$archive_size" ]] || { echo '[transfer] Archive length mismatch' >&2; exit 1; }
actual_hash=$(sha256sum "$receive_dir/image.tar.gz")
[[ "${actual_hash%% *}" == "$archive_hash" ]] || { echo '[transfer] Archive checksum mismatch' >&2; exit 1; }
echo "[transfer] received and verified $actual_size bytes; loading $image"
gzip -dc "$receive_dir/image.tar.gz" | docker image load
loaded_revision=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
[[ "$loaded_revision" == "$revision" ]] || { echo '[transfer] Loaded revision mismatch' >&2; exit 1; }
echo "[transfer] loaded $image"
