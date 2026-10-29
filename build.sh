#!/usr/bin/env bash
# Compile the atom and pack dist/build-name-setter/ — the directory tools/publish-atom.sh expects.
set -euo pipefail
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAR="$JAVA_HOME/bin/jar"
else
  JAVAC=javac
  JAR=jar
fi

classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
"$JAVAC" -encoding UTF-8 -d "$classes" src/BuildNameSetter.java

rm -rf dist/build-name-setter
mkdir -p dist/build-name-setter
"$JAR" --create --file dist/build-name-setter/build-name-setter.jar -C "$classes" .
cp task.json dist/build-name-setter/
[ -f meta.json ] && cp meta.json dist/build-name-setter/

echo "built dist/build-name-setter: $(ls dist/build-name-setter | tr '\n' ' ')"
