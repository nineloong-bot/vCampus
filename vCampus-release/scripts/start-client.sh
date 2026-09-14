#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
if ! command -v java >/dev/null 2>&1; then
  echo "未找到 Java，请安装 Java 21 或更高版本。" >&2
  exit 1
fi
if ! JAVA_OUTPUT=$(java -version 2>&1); then
  echo "未找到 Java，请安装 Java 21 或更高版本。" >&2
  exit 1
fi
JAVA_LINE=$(printf '%s\n' "$JAVA_OUTPUT" | head -n 1)
JAVA_VERSION=$(printf '%s' "$JAVA_LINE" | sed -E 's/.*version "([0-9]+).*/\1/')
case "$JAVA_VERSION" in
  ''|*[!0-9]*)
    echo "无法识别 Java 版本：$JAVA_LINE" >&2
    exit 1
    ;;
esac
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
  echo "需要 Java 21 或更高版本。" >&2
  exit 1
fi
exec java -Dlogback.configurationFile=config/logback.xml -jar lib/vCampusClient.jar config/client.properties
