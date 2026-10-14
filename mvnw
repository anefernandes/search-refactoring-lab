#!/usr/bin/env sh
# Lightweight Maven launcher. Uses local Maven when available; otherwise downloads Maven 3.9.16.
set -eu
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
VERSION="3.9.16"
BASE="${MAVEN_USER_HOME:-${HOME:-$PWD}/.m2}/wrapper/dists/apache-maven-${VERSION}"
DIST="${BASE}/apache-maven-${VERSION}"
if [ ! -x "${DIST}/bin/mvn" ]; then
  mkdir -p "${BASE}"
  TMP="${BASE}/maven.tar.gz"
  URL="https://dlcdn.apache.org/maven/maven-3/${VERSION}/binaries/apache-maven-${VERSION}-bin.tar.gz"
  if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$TMP"; else wget -O "$TMP" "$URL"; fi
  tar -xzf "$TMP" -C "$BASE"
  rm -f "$TMP"
fi
exec "${DIST}/bin/mvn" "$@"
