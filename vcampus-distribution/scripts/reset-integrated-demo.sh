#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEMO_DATABASE="$SCRIPT_DIR/../data/course-user-demo.accdb"
rm -f -- "$DEMO_DATABASE"
echo "已重置认证选课 Demo 数据库；下次启动服务端会重新创建。"
