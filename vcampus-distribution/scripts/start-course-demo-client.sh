#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
TOKEN=${1:-student-demo-1}
ROLE=${2:-STUDENT}
exec java -Dlogback.configurationFile=config/logback.xml -cp lib/vCampusClient.jar \
  edu.seu.vcampus.client.course.demo.CourseDemoClientMain config/client.properties "$TOKEN" "$ROLE"
