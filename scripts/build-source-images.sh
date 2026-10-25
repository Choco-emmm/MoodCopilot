#!/usr/bin/env bash
# Send only tracked application sources, then build fixed-SHA images on the server.
set -euo pipefail
umask 077
project_dir=${1:?Missing project directory}
owner=${2:?Missing registry owner}
revision=${3:?Missing revision}
[[ "$project_dir" == /* && "$owner" =~ ^[a-z0-9][a-z0-9-]*$ && "$revision" =~ ^[a-f0-9]{40}$ ]]
[[ "${SSH_HOST:?}" =~ ^[a-zA-Z0-9.-]+$ && "${SSH_USER:?}" =~ ^[a-zA-Z0-9_-]+$ && "${SSH_PORT:-22}" =~ ^[0-9]+$ ]]
[[ "$(git rev-parse --verify "$revision^{commit}")" == "$revision" ]]
script_dir=$(cd "$(dirname "$0")" && pwd)
archive_dir=$(mktemp -d)
trap 'rm -rf -- "$archive_dir"' EXIT
git archive --format=tar.gz --output="$archive_dir/source.tar.gz" "$revision" .dockerignore backend/moodcopilot frontend shared
archive_size=$(wc -c < "$archive_dir/source.tar.gz")
archive_hash=$(sha256sum "$archive_dir/source.tar.gz")
ssh_command=(ssh -i "${SSH_KEY_FILE:?}" -p "${SSH_PORT:-22}" -o BatchMode=yes
  -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="${SSH_KNOWN_HOSTS_FILE:?}" -o ConnectTimeout=20
  -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -o IPQoS=none "$SSH_USER@$SSH_HOST")
remote_command=$(python3 -c 'import pathlib, shlex, sys; print(shlex.join(["bash", "-c", pathlib.Path(sys.argv[1]).read_text(), "--", *sys.argv[2:]]))' \
  "$script_dir/load-source-archive.sh" "$project_dir" "$owner" "$revision" "${archive_hash%% *}" "$archive_size")
echo "[source] sending $archive_size bytes for $revision; building with server caches"
timeout 1800 "${ssh_command[@]}" "$remote_command" < "$archive_dir/source.tar.gz"
echo "[source] both application images ready for $revision"
