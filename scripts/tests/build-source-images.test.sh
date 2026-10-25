#!/usr/bin/env bash
# Exercise the real git archive, SSH quoting, checksum and isolated server build.
set -euo pipefail
script_dir=$(cd "$(dirname "$0")/.." && pwd)
fixture_dir=$(mktemp -d)
trap 'rm -rf -- "$fixture_dir"' EXIT
mkdir -p "$fixture_dir/bin" "$fixture_dir/repo/backend/moodcopilot" "$fixture_dir/repo/frontend" "$fixture_dir/repo/shared"
cat > "$fixture_dir/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_LOG"
key() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }
if [[ "$*" == 'image inspect '* ]]; then
  cat "$MOCK_IMAGES/$(key "${!#}")"
elif [[ "$1" == run ]]; then
  [[ "$SCENARIO" != maven-failure ]] || exit 1
  [[ "$*" == *'clean package'* && "$*" == *'-Dmaven.test.skip=true'* ]]
  [[ "$*" == *'/backend/moodcopilot:/app'* ]]
  cat >/dev/null
elif [[ "$1" == build ]]; then
  [[ "$SCENARIO" != build-failure ]] || exit 1
  args=("$@")
  for ((i=0; i<${#args[@]}; i++)); do
    if [[ "${args[i]}" == --tag ]]; then image=${args[i+1]}; fi
  done
  [[ "$*" == *'/.deploy/source-build.'* ]]
  value=$MOCK_REVISION
  if [[ "$SCENARIO" == wrong-revision ]]; then value=wrong; fi
  printf '%s' "$value" > "$MOCK_IMAGES/$(key "$image")"
else
  echo 'Unexpected Docker operation' >&2
  exit 99
fi
MOCK
cat > "$fixture_dir/bin/ssh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == *'StrictHostKeyChecking=yes'* && "$*" == *'IdentitiesOnly=yes'* ]]
command="${!#}"
[[ "$SCENARIO" != network-failure ]] || exit 1
if [[ "$SCENARIO" == corrupt-archive ]]; then
  python3 -c 'import sys; b=sys.stdin.buffer.read(); sys.stdout.buffer.write(b[:-1]+bytes([b[-1]^1]))' | sh -c "$command"
elif [[ "$SCENARIO" == truncated-archive ]]; then
  python3 -c 'import sys; sys.stdout.buffer.write(sys.stdin.buffer.read()[:-1])' | sh -c "$command"
else
  sh -c "$command"
fi
MOCK
chmod +x "$fixture_dir/bin/"*
printf '<project/>\n' > "$fixture_dir/repo/backend/moodcopilot/pom.xml"
printf 'FROM fixture\n' > "$fixture_dir/repo/backend/moodcopilot/Dockerfile"
printf 'FROM fixture\n' > "$fixture_dir/repo/frontend/Dockerfile"
printf 'tracked\n' > "$fixture_dir/repo/shared/source.txt"
printf 'backend\n' > "$fixture_dir/repo/.dockerignore"
git -C "$fixture_dir/repo" init -q
git -C "$fixture_dir/repo" add .
git -C "$fixture_dir/repo" -c user.name=Fixture -c user.email=fixture@example.invalid commit -qm fixture
export MOCK_REVISION
MOCK_REVISION=$(git -C "$fixture_dir/repo" rev-parse HEAD)
# These must never reach the archive, builds, logs or user's installation.
printf 'private-untracked-fixture\n' > "$fixture_dir/repo/.env"
printf 'private-dirty-fixture\n' > "$fixture_dir/repo/shared/source.txt"
export PATH="$fixture_dir/bin:$PATH"
export SSH_HOST=fixture.invalid SSH_USER=fixture SSH_PORT=22 SSH_KEY_FILE=fixture-key SSH_KNOWN_HOSTS_FILE=fixture-hosts
run_case() {
  export SCENARIO=$1
  local expected=$2 project="$fixture_dir/${3:-$SCENARIO}" actual=0
  mkdir -p "$project/images" "$project/.deploy"
  printf 'services: {}\n' > "$project/docker-compose.yml"
  printf 'keep-server-env\n' > "$project/.env"
  printf 'keep-server-script\n' > "$project/deploy.sh"
  export MOCK_IMAGES="$project/images" MOCK_LOG="$project/docker.log"
  : > "$MOCK_LOG"
  (cd "$fixture_dir/repo"; bash "$script_dir/build-source-images.sh" "$project" choco-emmm "$MOCK_REVISION") > "$project/output" 2>&1 || actual=$?
  if [[ "$actual" -ne "$expected" ]]; then cat "$project/output"; echo "FAIL source $SCENARIO ($actual)"; exit 1; fi
  [[ "$(cat "$project/.env")" == keep-server-env && "$(cat "$project/deploy.sh")" == keep-server-script ]]
  ! find "$project/.deploy" -maxdepth 1 -type d -name 'source-build.*' | grep -q .
  ! grep -q 'private-.*-fixture' "$project/output"
  if [[ "$expected" -eq 0 ]]; then
    [[ $(find "$MOCK_IMAGES" -type f | wc -l) -eq 2 ]]
    grep -q 'verified both fixed-SHA images' "$project/output"
  fi
  if [[ "$SCENARIO" == corrupt-archive || "$SCENARIO" == truncated-archive || "$SCENARIO" == network-failure || "$SCENARIO" == concurrent ]]; then [[ ! -s "$MOCK_LOG" ]]; fi
  echo "PASS source $SCENARIO"
}
run_case healthy 0 "path with 'quote'"
[[ $(grep -c '^build ' "$MOCK_LOG") -eq 2 ]]
run_case healthy 0 "path with 'quote'"
! grep -q '^run\|^build' "$MOCK_LOG"
echo 'PASS source reuses exact revision images'
run_case maven-failure 1
! grep -q '^build ' "$MOCK_LOG"
run_case build-failure 1
run_case wrong-revision 1
run_case network-failure 1
run_case corrupt-archive 1
run_case truncated-archive 1
mkdir -p "$fixture_dir/concurrent/.deploy"
(exec 8>"$fixture_dir/concurrent/.deploy/source-build.lock"; flock -n 8; run_case concurrent 1)
# Check the actual tracked archive, independent of the mocked Docker implementation.
git -C "$fixture_dir/repo" archive "$MOCK_REVISION" .dockerignore backend/moodcopilot frontend shared | tar -xOf - shared/source.txt > "$fixture_dir/tracked.txt"
[[ "$(cat "$fixture_dir/tracked.txt")" == tracked ]]
echo 'PASS source uses committed contents only'
