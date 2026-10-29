#!/usr/bin/env bash
# Run the atom the way the runner does — input.json + build.log in, mutations.json + output.json out — and assert
# what it wrote. No Jenkins needed, so CI can run it on every push.
set -euo pipefail
cd "$(dirname "$0")/.."

./build.sh >/dev/null
jar="$(cd dist/build-name-setter && pwd)/build-name-setter.jar"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

run() { # run <dir> <build.log content or empty>
  local dir="$1" log="$2"
  printf '%s' "$log" >"$dir/build.log"
  ( cd "$dir" && atom_data_dir="$dir" atom_data_input=input.json atom_data_output=output.json \
      atom_build_log=build.log BUILD_NUMBER=7 GIT_COMMIT=abc1234 \
      "$java_bin" -cp "$jar" BuildNameSetter >/dev/null )
}

fail() { echo "smoke failed: $*" >&2; exit 1; }

# 1. template only: environment expansion, description, success output
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "release-${BUILD_NUMBER}-${GIT_COMMIT}", "description": "built by atom",
 "fromLog": "", "matchTemplate": ""}
JSON
run "$dir" ""
grep -q '"displayName": "release-7-abc1234"' "$dir/mutations.json" || fail "template: $(cat "$dir/mutations.json")"
grep -q '"description": "built by atom"' "$dir/mutations.json" || fail "description missing"
grep -q '"status":"success"' "$dir/output.json" || fail "output.json is not a success"

# 2. name from the console log, with a template around the captured group
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "unknown", "description": "", "fromLog": "artifact: mylib-([0-9.]+)\\.jar",
 "matchTemplate": "v%MATCH%"}
JSON
run "$dir" $'Checking out\nartifact: mylib-2.4.1.jar\npacked\n'
grep -q '"displayName": "v2.4.1"' "$dir/mutations.json" || fail "log regex: $(cat "$dir/mutations.json")"
grep -q '"description"' "$dir/mutations.json" && fail "empty description must not be written"

# 3. declaring fromLog without the log granted must fail loudly, not silently keep the template
dir="$(mktemp -d)"
cat >"$dir/input.json" <<'JSON'
{"displayName": "x", "description": "", "fromLog": "nothing", "matchTemplate": ""}
JSON
printf '{}' >"$dir/build.log"
( cd "$dir" && atom_data_dir="$dir" atom_data_input=input.json atom_data_output=output.json \
    "$java_bin" -cp "$jar" BuildNameSetter >/dev/null )
grep -q '"status":"error"' "$dir/output.json" || fail "missing context must be an error"

echo "smoke ok: template, log regex, missing context"
