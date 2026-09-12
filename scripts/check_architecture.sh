#!/usr/bin/env bash
# 架构门禁（doc 46 A-1）：扫描 commonMain 的 import 与页面级计数，命中禁止规则即非零退出。
#
# 实现说明（为什么规则写在 awk 里）：
#   Git Bash（Windows）下每次 spawn grep 约 200-500ms，逐文件逐规则 grep 会跑到分钟级。
#   这里只 spawn 一次 find、一次 awk（程序经参数传入，不落临时文件 —— mktemp 在 Git Bash
#   下返回 Windows 风格路径，会触发 safe-delete 垫片的失败重试，实测白白多花约 15s）。
#
# 用法：  bash scripts/check_architecture.sh        （或 ./gradlew architectureCheck）
# 豁免：  scripts/architecture-allowlist.txt        （逐文件逐条，禁止整目录豁免）
# 基线：  scripts/architecture-baseline.txt         （规则 5 的页面计数，只允许下调）
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

ROOT="shared/src/commonMain/kotlin/com/kuikly/stockchat"
ALLOWLIST="scripts/architecture-allowlist.txt"
BASELINE="scripts/architecture-baseline.txt"

AWK_PROGRAM=$(cat <<'AWK'
function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }

function load_list(file,    line, n, f, k) {
  while ((getline line < file) > 0) {
    if (line ~ /^[ \t]*#/ || trim(line) == "") continue
    n = split(line, f, "|")
    if (file == allowlist) {
      if (n >= 2) { exempt[trim(f[1]) "|" trim(f[2])] = 1; exemptCount++ }
    } else if (n >= 4) {
      k = trim(f[1])
      baseTimeout[k] = f[2] + 0; baseProvider[k] = f[3] + 0; baseObservable[k] = f[4] + 0
      baseSeen[k] = 1
    }
  }
  close(file)
}

BEGIN {
  exemptCount = 0; total = 0
  load_list(allowlist)
  load_list(baseline)
}

function viol(rule, msg) { total++; out[total] = "[" rule "] " msg }

function isData(p)          { return p ~ /^data\// }
function isComponent(p)     { return p ~ /(^page\/components\/|\/components?\/)/ }
function isState(p)         { return p ~ /\/state\// }
function isFoundation(p)    { return p ~ /^(foundation|shared)\// }
function isPageRoot(p)      { return p ~ /^page\/[^\/]*Page\.kt$/ }
function isPageComponent(p) { return p ~ /^page\/components\// }

function finish_file(   bt, bp, bo) {
  if (isPageRoot(path)) {
    bt = (path in baseSeen) ? baseTimeout[path] : 0
    bp = (path in baseSeen) ? baseProvider[path] : 0
    bo = (path in baseSeen) ? baseObservable[path] : 0
    if (fTimeout > bt || fProvider > bp || fObservable > bo) {
      viol("R5", path ": setTimeout " fTimeout "/" bt ", Provider 构造 " fProvider "/" bp \
               ", by observable " fObservable "/" bo \
               "（基线只允许下调；确有正当新增时在提交里显式改基线并说明）")
    }
  }
  if (isPageComponent(path) && !((path "|R6") in exempt)) {
    viol("R6", path ": page/components 新增文件（该目录只出不进；组件落 foundation/** 或对应 Feature 包）")
  }
}

FILENAME != lastFile {
  if (lastFile != "") finish_file()
  lastFile = FILENAME
  path = FILENAME
  sub(rootPrefix, "", path)
  fTimeout = 0; fProvider = 0; fObservable = 0
}

# 计数（规则 5）：页面级 setTimeout / Provider 构造 / 业务 by observable
{
  if (isPageRoot(path)) {
    if ($0 ~ /setTimeout\(/)                           fTimeout++
    if ($0 ~ /(^|[^A-Za-z])[A-Z][A-Za-z]*Provider\(/) fProvider++
    if ($0 ~ /by observable\(/)                       fObservable++
  }
}

$0 ~ /^import / {
  line = $0
  # 规则 1：data 层不得依赖 UI / 响应式 / 导航 / Toast
  if (isData(path)) {
    if (line ~ /^import (com\.kuikly\.stockchat\.page\.|com\.tencent\.kuikly\.core\.views\.|com\.tencent\.kuikly\.core\.reactive\.)/ \
        || line ~ /^import .*(Navigation|Toast)/) {
      if (!((path "|R1") in exempt)) viol("R1", path ":" FNR ": " line)
    }
  }
  # 规则 2：component 层不得依赖 Store / Repository / Provider / Bridge / SharedPreferences / Pager 作用域
  if (isComponent(path)) {
    if (line ~ /^import .*(Store|Repository|Provider|Bridge|SharedPreferences|PagerScope)/) {
      if (!((path "|R2") in exempt)) viol("R2", path ":" FNR ": " line)
    }
  }
  # 规则 3：state 层不得依赖 page.* / Kuikly View / 具体平台实现
  if (isState(path)) {
    if (line ~ /^import (com\.kuikly\.stockchat\.page\.|com\.tencent\.kuikly\.core\.views\.)/ \
        || line ~ /^import (platform\.|java\.|android\.|kotlin\.native\.)/ \
        || line ~ /^import com\.tencent\.km[xm]/) {
      if (!((path "|R3") in exempt)) viol("R3", path ":" FNR ": " line)
    }
  }
  # 规则 4：shared / foundation 不得 import 任何一个 Feature
  if (isFoundation(path)) {
    if (line ~ /^import com\.kuikly\.stockchat\.(chat|detail|market|risk|watchlist|glossary|alert)\./) {
      if (!((path "|R4") in exempt)) viol("R4", path ":" FNR ": " line)
    }
  }
}

END {
  finish_file()
  if (total == 0) {
    print "architectureCheck: OK -- 6 条规则全部通过（既有债务豁免 " exemptCount " 条）"
    print "                  豁免清单 " allowlist " / 计数基线 " baseline
    exit 0
  }
  print "architectureCheck: FAILED -- 发现被禁止的新依赖 / 新增计数："
  for (i = 1; i <= total; i++) print "  " out[i]
  print ""
  print "处理方式："
  print "  - 新引入的依赖 -> 改代码（这是门禁的目的）"
  print "  - 历史债务   -> 在 " allowlist " 追加「路径 | 规则编号 | 原因 | 预计清除的工作包」"
  print "  - 页面计数   -> 若确属正当新增，在提交里显式上调 " baseline " 并说明理由"
  exit 1
}
AWK
)

# 文件列表须做分词（仓库内路径无空格）
FILES="$(find "$ROOT" -name '*.kt' | sort)"
if [ -z "$FILES" ]; then
  echo "architectureCheck: 未找到扫描目标（$ROOT），请确认工作目录" >&2
  exit 2
fi

# shellcheck disable=SC2086
awk -v allowlist="$ALLOWLIST" -v baseline="$BASELINE" -v rootPrefix="$ROOT/" \
    "$AWK_PROGRAM" $FILES
