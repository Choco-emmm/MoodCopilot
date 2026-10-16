#!/usr/bin/env bash
# Exercise orchestration without a daemon, credentials, network, or user data.
set -euo pipefail
script_dir=$(cd "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/bin"
cat > "$test_dir/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_LOG"
if [[ "$1" == login ]]; then
  cat >/dev/null
  [[ "$SCENARIO" != auth-failure ]]
elif [[ "$1" == inspect ]]; then
  printf 'sha256:%064d\n' 1
elif [[ "$*" == *'ps -q backend'* ]]; then
  [[ "$SCENARIO" == missing ]] || echo backend-container
elif [[ "$*" == *'ps -q web'* ]]; then
  echo web-container
elif [[ "$*" == *'ps -q frontend'* ]]; then
  echo public-nginx
elif [[ "$*" == *'pull backend web'* ]]; then
  [[ "$SCENARIO" != *pull-failure ]]
elif [[ "$*" == *' up '* ]]; then
  if [[ "$SCENARIO" == rollback-failure ]]; then exit 1; fi
  if [[ "$SCENARIO" == unhealthy && "$*" == *next.yml* ]]; then exit 1; fi
elif [[ "$*" == *'exec -T web wget'* && "$SCENARIO" == web-failure ]]; then
  # Fail the first probe only, so rollback must verify its restored web server.
  if [[ ! -e "$MOCK_PROBE_FAILED" ]]; then touch "$MOCK_PROBE_FAILED"; exit 1; fi
fi
MOCK
chmod +x "$test_dir/bin/docker"
export PATH="$test_dir/bin:$PATH"
revision=0123456789abcdef0123456789abcdef01234567

run_case() {
  export SCENARIO=$1
  local expected=$2
  local project="$test_dir/$SCENARIO"
  mkdir -p "$project"
  printf 'services: {}\n' > "$project/docker-compose.yml"
  export MOCK_LOG="$project/docker.log"
  export MOCK_PROBE_FAILED="$project/probe.failed"
  : > "$MOCK_LOG"
  if [[ "$SCENARIO" == auth-* ]]; then
    export GHCR_TOKEN=token-fixture-that-must-not-be-logged GHCR_USER=test-user
  else
    unset GHCR_TOKEN GHCR_USER
  fi
  local actual=0
  bash "$script_dir/deploy-images.sh" "$project" choco-emmm "$revision" > "$project/output" 2>&1 || actual=$?
  [[ "$actual" -eq "$expected" ]] || { cat "$project/output"; echo "FAIL $SCENARIO ($actual)"; exit 1; }
  if [[ "$expected" -eq 0 ]]; then
    [[ "$(cat "$project/.deploy/current-revision")" == "$revision" ]]
    grep -q 'nginx -s reload' "$MOCK_LOG"
    grep -q '/api/health' "$MOCK_LOG"
  else
    [[ ! -e "$project/.deploy/current-revision" ]]
  fi
  case "$SCENARIO" in
    pull-failure|auth-pull-failure|missing|auth-failure) ! grep -q ' up ' "$MOCK_LOG" ;;
    unhealthy|web-failure|rollback-failure) grep -q 'previous.yml up' "$MOCK_LOG" ;;
  esac
  # Data stores, source checkout, volumes and the public ingress are never recreated.
  ! grep ' up ' "$MOCK_LOG" | grep -Eq ' (mysql|redis|rabbitmq|tunnel|frontend)( |$)'
  ! grep -Eq 'down|prune|volume rm|--build' "$MOCK_LOG"
  ! grep -q 'token-fixture' "$MOCK_LOG" "$project/output"
  if compgen -G "$project/.deploy/auth.*" >/dev/null; then
    echo 'FAIL registry credentials were not cleaned up'; exit 1
  fi
  echo "PASS $SCENARIO"
}

run_case healthy 0
run_case pull-failure 1
run_case missing 1
run_case unhealthy 1
run_case web-failure 1
run_case rollback-failure 1
run_case auth-healthy 0
run_case auth-failure 1
run_case auth-pull-failure 1
unset GHCR_TOKEN GHCR_USER
export MOCK_LOG="$test_dir/invalid.log"
: > "$MOCK_LOG"
if bash "$script_dir/deploy-images.sh" "$test_dir/healthy" choco-emmm latest >/dev/null 2>&1; then
  echo 'FAIL invalid revision accepted'; exit 1
fi
[[ ! -s "$MOCK_LOG" ]]
echo 'PASS invalid revision'
# A second deployment must fail before inspecting or changing containers.
exec 8>"$test_dir/healthy/.deploy/deploy.lock"
flock -n 8
if bash "$script_dir/deploy-images.sh" "$test_dir/healthy" choco-emmm "$revision" >/dev/null 2>&1; then
  echo 'FAIL concurrent deployment accepted'; exit 1
fi
[[ ! -s "$MOCK_LOG" ]]
echo 'PASS deployment lock'
