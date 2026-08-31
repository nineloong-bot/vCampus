#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
exec java -Dlogback.configurationFile=config/logback.xml \
  -jar lib/vCampusClient.jar config/client.properties
