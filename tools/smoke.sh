#!/usr/bin/env bash
# Run the atom the way the runner does — input.json + build.log in, mutations.json + output.json out — and assert
# what it wrote. The atom is the real package: the SDK's AtomRunner is inside the jar.
set -euo pipefail
cd "$(dirname "$0")/.."

export BUILD_NUMBER=7 JOB_NAME="$(basename "$PWD")-job"

./build.sh >/dev/null
jar="$(cd dist/build-name-setter && pwd)/build-name-setter.jar"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
fail() { echo "smoke failed: $*" >&2; exit 1; }

# run <dir> [extra env assignments...]: returns the atom's exit code, log in <dir>/run.log
run() {
  local dir="$1"; shift
  python3 - "$dir/input.json" "$dir" <<'JSONFIX'
import json, os, sys
path, workspace = sys.argv[1], sys.argv[2]
given = {}
if os.path.exists(path) and os.path.getsize(path):
    given = json.load(open(path))
# the runner writes its environment into input.json first, then the job's inputs on top
data = dict(os.environ)
data.update(given)
data.setdefault("atom_workspace", workspace)
json.dump(data, open(path, "w"))
JSONFIX
  printf '%s' "${SMOKE_LOG_CONTENT:-}" >"$dir/build.log"
  ( cd "$dir" && env atom_data_dir="$dir" atom_data_input=input.json atom_data_output=output.json \
      BUILD_NUMBER=7 GIT_COMMIT=abc1234 "$@" \
      "$java_bin" -cp "$jar" com.jenkinschina.atom.sdk.AtomRunner ) >"$dir/run.log" 2>&1 || return $?
  return 0
}

# 1. template only: ${ENV} expansion, description, success output
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "release-${BUILD_NUMBER}-${GIT_COMMIT}", "description": "built by atom",
 "fromLog": "", "matchTemplate": ""}
JSON
run "$dir" || fail "template run failed: $(cat "$dir/run.log")"
grep -q '"displayName": "release-7-abc1234"' "$dir/mutations.json" \
  || fail "template name: $(cat "$dir/mutations.json")"
grep -q '"description": "built by atom"' "$dir/mutations.json" || fail "description missing"
grep -q '"status":"success"' "$dir/output.json" || fail "output.json is not a success"
grep -q "\[atom\] task com.jenkinschina.atom" "$dir/run.log" || fail "the SDK did not run the task"

# 2. name from the console log, with a template around the captured group
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "unknown", "description": "", "fromLog": "artifact: mylib-([0-9.]+)\\.jar",
 "matchTemplate": "v%MATCH%", "atom_build_log": "build.log"}
JSON
SMOKE_LOG_CONTENT=$'Checking out\nartifact: mylib-2.4.1.jar\npacked\n' \
  run "$dir" atom_build_log=build.log || fail "log run failed: $(cat "$dir/run.log")"
grep -q '"displayName": "v2.4.1"' "$dir/mutations.json" || fail "log regex: $(cat "$dir/mutations.json")"
if grep -q '"description"' "$dir/mutations.json"; then fail "empty description must not be written"; fi

# 3. declaring fromLog without the log granted must fail loudly, not silently keep the template
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "x", "description": "", "fromLog": "nothing", "matchTemplate": ""}
JSON
if run "$dir"; then fail "a missing context must fail the atom"; fi
grep -q '"status":"error"' "$dir/output.json" || fail "missing context must be an error"
grep -q 'build log was not handed over' "$dir/output.json" || fail "message is not readable: $(cat "$dir/output.json")"

echo "smoke ok: template, log regex, missing context"
