#!/usr/bin/env sh
set -eu

DEFAULT_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SCRIPT_DIR=${1:-$DEFAULT_SCRIPT_DIR}
TEMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/vcampus-script-test.XXXXXX")
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

printf '%s\n' '#!/usr/bin/env sh' 'exit 127' > "$TEMP_DIR/java"
chmod +x "$TEMP_DIR/java"

assert_missing_java_message() {
  script=$1
  output_file="$TEMP_DIR/output.txt"
  if PATH="$TEMP_DIR:/usr/bin:/bin" /bin/sh "$script" >"$output_file" 2>&1; then
    echo "FAIL: $script should reject a missing Java runtime" >&2
    exit 1
  fi
  if ! grep -q '未找到 Java' "$output_file"; then
    echo "FAIL: $script did not explain that Java is missing" >&2
    sed -n '1,20p' "$output_file" >&2
    exit 1
  fi
}

assert_missing_java_message "$SCRIPT_DIR/start-server-with-data.sh"
assert_missing_java_message "$SCRIPT_DIR/start-client.sh"

printf '%s\n' '#!/usr/bin/env sh' \
  'if [ "${1:-}" = "-version" ]; then echo "mystery runtime" >&2; exit 0; fi' \
  'exit 99' > "$TEMP_DIR/java"
chmod +x "$TEMP_DIR/java"
for script in "$SCRIPT_DIR/start-server-with-data.sh" "$SCRIPT_DIR/start-client.sh"; do
  output_file="$TEMP_DIR/output.txt"
  if PATH="$TEMP_DIR:/usr/bin:/bin" /bin/sh "$script" >"$output_file" 2>&1; then
    echo "FAIL: $script should reject an unrecognized Java version" >&2
    exit 1
  fi
  if ! grep -q '无法识别 Java 版本' "$output_file"; then
    echo "FAIL: $script did not explain the unrecognized Java version" >&2
    exit 1
  fi
done
echo "PASS: startup scripts report a missing Java runtime"
