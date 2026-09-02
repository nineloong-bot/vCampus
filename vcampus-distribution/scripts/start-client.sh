#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
JAVA_LINE=$(java -version 2>&1 | head -n 1)
JAVA_VERSION=$(printf '%s' "$JAVA_LINE" | sed -E 's/.*version "([0-9]+).*/\1/')
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
  echo "需要 Java 21 或更高版本。" >&2
  exit 1
fi
exec java -Dlogback.configurationFile=config/logback.xml -jar lib/vCampusClient.jar config/client.properties
