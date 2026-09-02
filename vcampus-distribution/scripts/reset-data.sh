#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEMO_DATABASE="$SCRIPT_DIR/../data/vCampus.accdb"
printf '%s\n' "确认删除 data/vCampus.accdb 并恢复虚拟校园测试数据？[y/N]"
ANSWER=
IFS= read -r ANSWER || true
case "$ANSWER" in
  y|Y) ;;
  *)
    echo "已取消，Demo 数据未更改。"
    exit 0
    ;;
esac
rm -f -- "$DEMO_DATABASE"
echo "已重置虚拟校园数据；下次启动服务端会重新创建。"
