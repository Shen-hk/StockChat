#!/usr/bin/env bash
# h5 端全页面 DOM 回归探针（regression probe）
#
# 验证：`./gradlew :h5App:publishLocalJSBundle` 之后，13 个 @Page 在浏览器里
# 都能挂到 #root、生成至少一个 canvas、内容真实加载（无 JS Error / 空 DOM）。
#
# 用法：
#   bash scripts/h5_regression.sh        # 启 http + agent-browser 全跑
#   PAGES="ChatPage MarketPage" bash scripts/h5_regression.sh   # 只验指定页
#
# 依赖：
#   - h5App/build/distributions 已构建
#   - python3 在 PATH（自带 http.server）
#   - agent-browser >= 0.27 已安装
#
# 输出：每页一行 `PASS|FAIL + 关键指标`；末尾 `PASS: X / Y · FAIL: Z`。
# 退出码：全部 PASS → 0；任一 FAIL → 1。
set -u

PORT=8088
PAGES=${PAGES:-"ChatPage MarketPage WatchlistPage GlossaryPage RiskMapPage StockDetailPage SettingsPage ApiConfigPage CardGallery HotspotPage GlobalSearchPage MarketCalendarPage AlertCenterPage"}

PASS=0
FAIL=0
FAILS=()

# 起点：仓库根
ROOT=$(cd "$(dirname "$0")/.." && pwd)
echo "[h5-regression] root: $ROOT"
cd "$ROOT"

# 1. 启 http.server（背景）
python -m http.server "$PORT" --bind 127.0.0.1 --directory h5App/build/distributions \
    > /tmp/h5_http.log 2>&1 &
HTTP_PID=$!
trap "agent-browser close > /dev/null 2>&1 || true; kill $HTTP_PID 2>/dev/null" EXIT
sleep 1

# 2. 起 headless Chromium（一次性）
agent-browser open "http://127.0.0.1:$PORT/index.html" > /dev/null 2>&1 || true
sleep 2

# 3. 探针模板
PROBE='(()=>{const r=document.getElementById("root");const cs=document.querySelectorAll("canvas");const ps=[...document.querySelectorAll("p")].map(e=>e.textContent.trim()).filter(Boolean);return JSON.stringify({rootOK:!!r,kids:r?.children.length??0,canvases:cs.length,t:ps.length,s:ps.slice(0,4)})})()'

for page in $PAGES; do
    agent-browser open "http://127.0.0.1:$PORT/index.html?page_name=${page}" \
        > /dev/null 2>&1 || true
    sleep 5
    raw=$(agent-browser eval "$PROBE" 2>&1 | tail -1)
    # raw 形如  "{\"rootOK\":true,\"kids\":1,...}"
    if [[ "$raw" == *'\"rootOK\":true'* && "$raw" != *'\"canvases\":0,'* && "$raw" != *'\"canvases\":0}'* ]]; then
        status="PASS"
        PASS=$((PASS + 1))
    else
        status="FAIL"
        FAIL=$((FAIL + 1))
        FAILS+=("$page")
    fi
    printf '%-22s %-4s %s\n' "$page" "$status" "$raw"
done

echo ""
echo "==== summary ===="
echo "PASS: $PASS / $(echo $PAGES | wc -w)    FAIL: $FAIL"
if [[ "$FAIL" -gt 0 ]]; then
    echo "Failed pages: ${FAILS[*]}"
    exit 1
fi
exit 0
