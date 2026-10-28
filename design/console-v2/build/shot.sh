#!/usr/bin/env bash
# 重新生成 Console 02 设计稿截图（2x）
set -eo pipefail
cd "$(dirname "$0")/.."

if [ -z "$CHROME" ]; then
  CHROME="$HOME/Library/Caches/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-mac-arm64/chrome-headless-shell"
fi
if [ ! -x "$CHROME" ]; then
  echo "chrome-headless-shell not found: $CHROME" >&2
  echo "set CHROME=/path/to/chrome-headless-shell" >&2
  exit 1
fi

shot() {
  name="$1"; w="$2"; h="$3"
  "$CHROME" --no-sandbox --hide-scrollbars --force-device-scale-factor=2 \
    --window-size="$w,$h" --screenshot="screens/$name.png" "file://$PWD/$name.html" >/dev/null 2>&1
  echo "  screens/$name.png"
}

mkdir -p screens
echo "rendering..."
shot 00-overview 1440 2100
shot 01-api-list 1440 900
shot 02-api-edit 1440 900
shot 03-logs 1440 900
shot 04-assistant 1440 900
shot 05-login 1440 900
echo "done."
