#!/usr/bin/env bash
# Exercises real archive, stdin and receiver paths with fixture Docker/SSH only.
set -euo pipefail
script_dir=$(cd "$(dirname "$0")/.." && pwd)
fixture_dir=$(mktemp -d)
trap 'rm -rf -- "$fixture_dir"' EXIT
mkdir -p "$fixture_dir/bin"
cat > "$fixture_dir/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_LOG"
marker() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }
if [[ "$*" == 'image save '* ]]; then
  [[ "$SCENARIO" != export-failure ]] || exit 1
  printf '%s' "$5" > "$4"
elif [[ "$*" == 'image inspect '* ]]; then
  image="${!#}"
  cat "$MOCK_IMAGES/$(marker "$image")"
  echo
elif [[ "$*" == 'image load' ]]; then
  image=$(cat)
  [[ "$SCENARIO" != load-failure ]] || exit 1
  [[ "$SCENARIO" != frontend-failure || "$image" != *-frontend:* ]] || exit 1
  if [[ "$SCENARIO" == wrong-revision ]]; then value=wrong; else value="$MOCK_REVISION"; fi
  printf '%s' "$value" > "$MOCK_IMAGES/$(marker "$image")"
elif [[ "$*" == 'compose exec '* ]]; then
  cat >/dev/null
  echo '{"status":"UP"}'
elif [[ "$*" == *'ps -q '* ]]; then
  echo container-fixture
elif [[ "$1" == inspect ]]; then
  echo sha256:fixture
elif [[ "$*" == 'compose ps '* ]]; then
  echo 'backend running healthy'
fi
MOCK
cat > "$fixture_dir/bin/ssh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
command="${!#}"
[[ "$command" != true ]] || exit 0
if [[ "$command" == 'bash -c '* ]]; then
  echo RECEIVE >> "$MOCK_LOG"
  [[ "$SCENARIO" != network-failure ]] || exit 1
  if [[ "$SCENARIO" == corrupt-archive ]]; then
    python3 -c 'import sys; b=sys.stdin.buffer.read(); sys.stdout.buffer.write(b[:-1]+bytes([b[-1]^1]))' | sh -c "$command"
  else
    sh -c "$command"
  fi
else
  sh -c "$command"
fi
MOCK
printf '#!/usr/bin/env bash\necho source-fixture\n' > "$fixture_dir/bin/git"
chmod +x "$fixture_dir/bin/"*
export PATH="$fixture_dir/bin:$PATH"
export SSH_HOST=fixture.invalid SSH_USER=fixture SSH_PORT=22 SSH_KEY_FILE=fixture-key SSH_KNOWN_HOSTS_FILE=fixture-hosts
export MOCK_REVISION=0123456789abcdef0123456789abcdef01234567
run_case() {
  export SCENARIO=$1
  local expected=$2
  local project="$fixture_dir/${3:-$SCENARIO}"
  mkdir -p "$project/images"
  export MOCK_IMAGES="$project/images" MOCK_LOG="$project/docker.log"
  : > "$MOCK_LOG"
  local actual=0
  bash "$script_dir/transfer-images.sh" "$project" choco-emmm "$MOCK_REVISION" > "$project/output" 2>&1 || actual=$?
  if [[ "$actual" -ne "$expected" ]]; then cat "$project/output"; echo "FAIL $SCENARIO ($actual)"; exit 1; fi
  if [[ "$expected" -eq 0 ]]; then
    [[ $(find "$MOCK_IMAGES" -type f | wc -l) -eq 2 ]]
    grep -q 'upload/load complete' "$project/output"
  fi
  if [[ "$SCENARIO" == corrupt-archive || "$SCENARIO" == export-failure || "$SCENARIO" == network-failure ]]; then
    ! grep -q '^image load$' "$MOCK_LOG"
  fi
  ! find "$project/.deploy" -name 'incoming.*' -print 2>/dev/null | grep -q .
  echo "PASS transfer $SCENARIO"
}
run_case healthy 0 "path with 'quote'"
run_case export-failure 1
run_case network-failure 1
run_case corrupt-archive 1
run_case load-failure 1
run_case wrong-revision 1
run_case frontend-failure 1
export SCENARIO=healthy
bash "$script_dir/transfer-images.sh" "$fixture_dir/frontend-failure" choco-emmm "$MOCK_REVISION" > "$fixture_dir/retry-output" 2>&1
grep -q 'already loaded .*moodcopilot-backend' "$fixture_dir/retry-output"
[[ $(find "$MOCK_IMAGES" -type f | wc -l) -eq 2 ]]
echo 'PASS transfer resumes after first image'
# Reproduce the SSH stdin transport: a Docker mock which drains stdin must not
# swallow the disk checks or completion marker in the actual remote script.
export SCENARIO=healthy
printf '%s' "$MOCK_REVISION" > "$MOCK_IMAGES/$(printf sha256:fixture | sha256sum | cut -d' ' -f1)"
cat "$script_dir/check-server.sh" | bash -s -- "$fixture_dir/frontend-failure" > "$fixture_dir/check-output"
grep -qx 'Disk space:' "$fixture_dir/check-output"
grep -qx SERVER_CHECK_COMPLETE "$fixture_dir/check-output"
echo 'PASS server check preserves script stdin'
export TRANSFER_TIMEOUT_SECONDS=invalid
run_case invalid-timeout 1
unset TRANSFER_TIMEOUT_SECONDS
