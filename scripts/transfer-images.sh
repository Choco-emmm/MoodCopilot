#!/usr/bin/env bash
# Runner-side staged delivery; credentials remain in its private Docker config.
set -euo pipefail
umask 077
project_dir=${1:?Missing project directory}
owner=${2:?Missing registry owner}
revision=${3:?Missing revision}
transfer_timeout=${TRANSFER_TIMEOUT_SECONDS:-4500}
[[ "$transfer_timeout" =~ ^[0-9]{2,4}$ ]] && (( 10#$transfer_timeout >= 60 && 10#$transfer_timeout <= 7200 )) || { echo 'Invalid transfer timeout (60-7200 seconds)' >&2; exit 1; }
[[ "$owner" =~ ^[a-z0-9][a-z0-9-]*$ && "$revision" =~ ^[a-f0-9]{40}$ ]]
[[ "${SSH_HOST:?}" =~ ^[a-zA-Z0-9.-]+$ && "${SSH_USER:?}" =~ ^[a-zA-Z0-9_-]+$ && "${SSH_PORT:-22}" =~ ^[0-9]+$ ]]
script_dir=$(cd "$(dirname "$0")" && pwd)
archive_dir=$(mktemp -d)
trap 'rm -rf -- "$archive_dir"' EXIT
ssh_command=(ssh -i "${SSH_KEY_FILE:?}" -p "${SSH_PORT:-22}" -o BatchMode=yes
  -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="${SSH_KNOWN_HOSTS_FILE:?}" -o ConnectTimeout=20
  -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -o IPQoS=none "$SSH_USER@$SSH_HOST")
timeout 45 "${ssh_command[@]}" true </dev/null
for service in backend frontend; do
  image="ghcr.io/$owner/moodcopilot-$service:$revision"
  printf -v image_arg '%q' "$image"
  # A failed delivery of the second image need not resend the first one.
  if existing_revision=$(timeout 45 "${ssh_command[@]}" "docker image inspect --format '{{ index .Config.Labels \"org.opencontainers.image.revision\" }}' $image_arg" </dev/null 2>/dev/null) && [[ "$existing_revision" == "$revision" ]]; then
    echo "[transfer] already loaded $image"
    continue
  fi
  echo "[transfer] pulling $image on Runner"
  timeout 180 docker pull "$image"
  echo "[transfer] export started $(date -u +%FT%TZ)"
  timeout 180 docker image save --output "$archive_dir/image.tar" "$image"
  echo "[transfer] export complete $(wc -c < "$archive_dir/image.tar") bytes"
  timeout 180 gzip -1 -c "$archive_dir/image.tar" > "$archive_dir/image.tar.gz"
  rm -- "$archive_dir/image.tar"
  archive_size=$(wc -c < "$archive_dir/image.tar.gz")
  archive_hash=$(sha256sum "$archive_dir/image.tar.gz")
  # SSH first invokes the account's login shell, which can be POSIX sh. Bash
  # printf %q may emit $'...' quoting, so quote the complete command portably.
  remote_command=$(python3 -c 'import pathlib, shlex, sys; print(shlex.join(["bash", "-c", pathlib.Path(sys.argv[1]).read_text(), "--", *sys.argv[2:]]))' \
    "$script_dir/load-image-archive.sh" "$project_dir" "$image" "$revision" "${archive_hash%% *}" "$archive_size")
  echo "[transfer] upload started $(date -u +%FT%TZ), $archive_size bytes"
  python3 -u "$script_dir/stream-archive.py" "$archive_dir/image.tar.gz" |
    timeout "$transfer_timeout" "${ssh_command[@]}" "$remote_command"
  echo "[transfer] upload/load complete $(date -u +%FT%TZ)"
  rm -- "$archive_dir/image.tar.gz"
done
