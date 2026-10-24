#!/usr/bin/env bash
# Real Docker archive round trip, with SSH replaced by a local receiver. Uses
# only two uniquely tagged, scratch-based fixture images; never pushes images.
set -euo pipefail
script_dir=$(cd "$(dirname "$0")/.." && pwd)
fixture_dir=$(mktemp -d)
export REAL_DOCKER
REAL_DOCKER=$(command -v docker)
revision=$(printf '%s' "$fixture_dir" | sha256sum | cut -c1-40)
images=("ghcr.io/moodcopilot-fixture/moodcopilot-backend:$revision" "ghcr.io/moodcopilot-fixture/moodcopilot-frontend:$revision")
cleanup() {
  "$REAL_DOCKER" image rm "${images[@]}" >/dev/null 2>&1 || true
  rm -rf -- "$fixture_dir"
}
trap cleanup EXIT
mkdir -p "$fixture_dir/bin" "$fixture_dir/project" "$fixture_dir/context"
printf 'archive-round-trip\n' > "$fixture_dir/context/fixture"
printf 'FROM scratch\nCOPY fixture /fixture\n' > "$fixture_dir/context/Dockerfile"
for image in "${images[@]}"; do
  "$REAL_DOCKER" build --quiet --label "org.opencontainers.image.revision=$revision" -t "$image" "$fixture_dir/context" >/dev/null
done
cat > "$fixture_dir/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == pull ]]; then
  "$REAL_DOCKER" image inspect "$2" >/dev/null
else
  exec "$REAL_DOCKER" "$@"
fi
MOCK
cat > "$fixture_dir/bin/ssh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
command="${!#}"
case "$command" in
  true) exit 0 ;;
  'docker image inspect '*) exit 1 ;;
  'bash -c '*) exec bash -c "$command" ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "$fixture_dir/bin/"*
export PATH="$fixture_dir/bin:$PATH"
export SSH_HOST=fixture.invalid SSH_USER=fixture SSH_PORT=22 SSH_KEY_FILE=fixture-key SSH_KNOWN_HOSTS_FILE=fixture-hosts
bash "$script_dir/transfer-images.sh" "$fixture_dir/project" moodcopilot-fixture "$revision" > "$fixture_dir/output" 2>&1 || { cat "$fixture_dir/output"; exit 1; }
[[ $(grep -c 'upload/load complete' "$fixture_dir/output") -eq 2 ]]
for image in "${images[@]}"; do
  [[ $("$REAL_DOCKER" image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image") == "$revision" ]]
done
echo 'PASS real Docker archive round trip (2 fixture images)'
