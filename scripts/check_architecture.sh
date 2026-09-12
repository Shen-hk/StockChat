#!/usr/bin/env bash
#
# check_architecture.sh — StockChat 四层架构门禁
#
# 扫描 shared/src/commonMain/kotlin 的 package / import 声明，命中「反向依赖」即非零退出。
# 规则全文见 docs/architecture/package-rules.md。
#
# 用法：
#   bash scripts/check_architecture.sh               # 检查（gradle architectureCheck 调它）
#   bash scripts/check_architecture.sh --list        # 打印全部命中（含已豁免），不判定
#   bash scripts/check_architecture.sh --baseline    # 重新生成 R5/R6 基线快照
#
# 退出码：0 = 通过；1 = 存在未豁免违规或基线退化；2 = 用法错误。
#
# 设计约束：
#   * 零第三方依赖，只用 find / awk / sort，离线可跑；
#   * 全部判定在**单次 awk** 内完成。本机进程启动开销极大（39 次 grep 要 3.8s），
#     所以刻意不做「逐文件 grep」式的循环。
#   * 文件清单通过命令行参数传给 awk，依赖「单次 exec 不超过 ARG_MAX」这一前提；
#     当前约 190 个 .kt 文件，距离上限两个数量级，安全。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$ROOT/shared/src/commonMain/kotlin"
ALLOW="$SCRIPT_DIR/architecture-allowlist.txt"
BASELINE="$SCRIPT_DIR/architecture-baseline.txt"

MODE="check"
case "${1:-}" in
  --list)     MODE="list" ;;
  --baseline) MODE="baseline" ;;
  "")         ;;
  *) echo "未知参数：$1（可用：--list / --baseline）" >&2; exit 2 ;;
esac

# shellcheck disable=SC2016
AWK_PROG='
# ---------------------------------------------------------------------------
# 工具
# ---------------------------------------------------------------------------

function rel(f,   s, p) {
  p = "/com/kuikly/stockchat/"
  if (index(f, p) == 0) return ""
  s = f
  sub(/^.*\/com\/kuikly\/stockchat\//, "", s)
  return s
}

function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }

# 简单插入排序（BWK awk 没有 asort），保证 baseline 输出稳定
function sortInto(arr, out,   i, j, n, k, tmp) {
  n = 0
  for (k in arr) { n++; out[n] = k }
  for (i = 2; i <= n; i++) {
    tmp = out[i]; j = i - 1
    while (j >= 1 && out[j] > tmp) { out[j + 1] = out[j]; j-- }
    out[j + 1] = tmp
  }
  return n
}

# ---------------------------------------------------------------------------
# 分层判定
# ---------------------------------------------------------------------------

function isData(p)       { return p ~ /^com[.]kuikly[.]stockchat[.]data([.]|$)/ }
function isComponent(p)  { return p ~ /[.]components?$/ }
function isState(p)      { return p ~ /[.]state$/ }
function isFoundation(p) { return p ~ /^com[.]kuikly[.]stockchat[.]foundation([.]|$)/ \
                             || p ~ /^com[.]kuikly[.]stockchat[.]shared([.]|$)/ }

function isPageImp(i)     { return i ~ /^com[.]kuikly[.]stockchat[.]page([.]|$)/ }
function isKuiklyView(i)  { return i ~ /^com[.]tencent[.]kuikly[.]core[.]views([.]|$)/ }
function isReactive(i)    { return i ~ /^com[.]tencent[.]kuikly[.]core[.]reactive([.]|$)/ }
function isPlatformImpl(i){ return i ~ /^com[.]tencent[.]kuikly[.]core[.]module([.]|$)/ }
function isFeatureImp(i)  { return i ~ /^com[.]kuikly[.]stockchat[.](chat|detail|market|risk|watchlist|glossary|alert)([.]|$)/ }

function ruleOf(p, i) {
  # R1 — data 不得依赖 UI / 响应式 / 平台导航 / Toast
  # 注：core.nvi.serialization.json 是数据层合法的序列化工具，不在此列。
  if (isData(p)) {
    if (isPageImp(i))    return "R1"
    if (isKuiklyView(i)) return "R1"
    if (isReactive(i))   return "R1"
    if (i ~ /^com[.]tencent[.]kuikly[.]core[.]nvi[.]/ \
        && i !~ /^com[.]tencent[.]kuikly[.]core[.]nvi[.]serialization/) return "R1"
    if (i ~ /(^|[.])Toast$/) return "R1"
  }
  # R2 — component 不得持有数据/平台访问权，也不得向上依赖 Page 层
  if (isComponent(p)) {
    if (i ~ /(Repository|Store|Provider|Bridge)$/) return "R2"
    if (i ~ /SharedPreferences$/)                  return "R2"
    if (i ~ /PagerScope$/)                         return "R2"
    if (isPageImp(i))                              return "R2"
  }
  # R3 — state 不得依赖 UI / 具体平台实现
  if (isState(p)) {
    if (isPageImp(i))      return "R3"
    if (isKuiklyView(i))   return "R3"
    if (isPlatformImpl(i)) return "R3"
  }
  # R4 — shared / foundation 不得反向依赖任何 Feature
  if (isFoundation(p)) {
    if (isPageImp(i))    return "R4"
    if (isFeatureImp(i)) return "R4"
  }
  return ""
}

# ---------------------------------------------------------------------------
# BEGIN：载入白名单与基线
# ---------------------------------------------------------------------------

BEGIN {
  while ((getline line < ALLOW) > 0) {
    sub(/#.*/, "", line); line = trim(line)
    if (line == "") continue
    if (split(line, a, "|") < 3) continue
    for (i = 1; i <= 3; i++) a[i] = trim(a[i])
    al[a[1] "|" a[2] "|" a[3]] = 1
  }
  close(ALLOW)

  while ((getline line < BASE) > 0) {
    sub(/#.*/, "", line); line = trim(line)
    if (line == "") continue
    if (index(line, "|") > 0) {
      split(line, a, "|")
      baseN[trim(a[1]) "|" trim(a[2])] = trim(a[3])
    } else {
      baseFile[line] = 1
    }
  }
  close(BASE)
}

# ---------------------------------------------------------------------------
# 主扫描：一遍读完所有 .kt
# ---------------------------------------------------------------------------

{
  if (FNR == 1) {
    # 落盘上一个 Page 文件的计数
    if (prevPage != "") { pageSt[prevPage] = st; pagePc[prevPage] = pc; pageBo[prevPage] = bo }
    rp = rel(FILENAME)
    pkg = ""
    inScope    = (rp != "")
    isPageFile = (rp ~ /^page\/[^\/]*Page[.]kt$/)
    isCompFile = (rp ~ /^page\/components\//)
    prevPage = isPageFile ? rp : ""
    if (isCompFile) curComp[rp] = 1
    st = 0; pc = 0; bo = 0
  }
  if (!inScope) next

  if ($1 == "package") { pkg = $2; next }

  if ($1 == "import") {
    imp = $2
    sub(/;.*$/, "", imp)
    r = ruleOf(pkg, imp)
    if (r != "") {
      nhit++
      if (MODE == "list") { print rp "|" r "|" imp }
      else if (!((rp "|" r "|" imp) in al)) {
        nbad++
        badPath[nbad] = rp; badRule[nbad] = r; badImp[nbad] = imp
      }
    }
    next
  }

  if (isPageFile) {
    # 注意：不要把正则字面量当函数实参传（BWK awk 会把它求值成布尔 0/1）。
    # 这里内联 gsub，用副本计数，避免改动 $0。
    t = $0; st += gsub(/setTimeout[(]/, "&", t)
    t = $0; pc += gsub(/Provider[(]/, "&", t)
    t = $0; bo += gsub(/by observable/, "&", t)
  }
}

# ---------------------------------------------------------------------------
# END：判定
# ---------------------------------------------------------------------------

END {
  if (prevPage != "") { pageSt[prevPage] = st; pagePc[prevPage] = pc; pageBo[prevPage] = bo }

  if (MODE == "baseline") {
    print "# StockChat 架构门禁基线快照 — 由 scripts/check_architecture.sh --baseline 生成"
    print "# 不要手改。R5：page/*Page.kt 的计数上限（只许降，不许升）。"
    print "# R6：page/components/ 的既有文件清单（只许减，不许增）。"
    print "#"
    print "# 格式：<相对路径>|<指标>|<基线值>  或  <相对路径>"
    ni = sortInto(pageSt, ia)
    for (i = 1; i <= ni; i++) print ia[i] "|setTimeout|" pageSt[ia[i]]
    ni = sortInto(pagePc, ia)
    for (i = 1; i <= ni; i++) print ia[i] "|ProviderCtor|" pagePc[ia[i]]
    ni = sortInto(pageBo, ia)
    for (i = 1; i <= ni; i++) print ia[i] "|byObservable|" pageBo[ia[i]]
    ni = sortInto(curComp, ic)
    for (i = 1; i <= ni; i++) print ic[i]
    exit 0
  }

  if (MODE == "list") exit 0

  fail = 0

  if (nbad > 0) {
    print "架构门禁失败：发现未豁免的反向依赖（规则见 docs/architecture/package-rules.md）"
    print ""
    for (i = 1; i <= nbad; i++) {
      printf "  [%s] %s\n        %s\n", badRule[i], badPath[i], badImp[i]
    }
    print ""
    print "修法：① 改设计，别让依赖方向倒挂；② 确属历史债务则写进 scripts/architecture-allowlist.txt"
    print "      （粒度=文件×规则×符号，禁止通配符、禁止整目录豁免）。"
    fail = 1
  }

  # R5 — Page 预算只许降不许升
  for (k in pageSt) { curN[k "|setTimeout"]   = pageSt[k] }
  for (k in pagePc) { curN[k "|ProviderCtor"] = pagePc[k] }
  for (k in pageBo) { curN[k "|byObservable"] = pageBo[k] }

  r5 = 0
  nk = sortInto(baseN, ka)
  for (i = 1; i <= nk; i++) {
    k = ka[i]
    split(k, parts, "|")
    if (parts[1] !~ /^page\//) continue
    cur = (k in curN) ? curN[k] : 0
    if (cur + 0 > baseN[k] + 0) {
      if (r5 == 0) { print "R5 Page 预算退化（page/*Page.kt 的 Timer / Provider / observable 只许降不许升）：" }
      printf "  %s：当前 %s > 基线 %s\n", k, cur, baseN[k]
      r5 = 1
    }
  }
  if (r5) fail = 1

  # R6 — page/components/ 禁止新增文件
  r6 = 0
  ni = sortInto(curComp, ic)
  for (i = 1; i <= ni; i++) {
    if (!(ic[i] in baseFile)) {
      if (r6 == 0) { print "R6 page/components/ 禁止新增文件（该目录只允许把文件搬出去）：" }
      printf "  %s\n", ic[i]
      r6 = 1
    }
  }
  if (r6) fail = 1

  if (!fail) print "architectureCheck 通过（扫描命中 " nhit " 条，全部已登记在 scripts/architecture-allowlist.txt）。"

  exit (fail ? 1 : 0)
}
'

# shellcheck disable=SC2086
files="$(find "$SRC" -name '*.kt' -type f | LC_ALL=C sort)"

if [ -z "$files" ]; then
  echo "architectureCheck：未找到任何 .kt 源文件（$SRC），跳过。" >&2
  exit 0
fi

if [ "$MODE" = "baseline" ]; then
  # shellcheck disable=SC2086
  awk -v ALLOW="$ALLOW" -v BASE="$BASELINE" -v MODE="$MODE" "$AWK_PROG" $files > "$BASELINE"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "已写入基线：$BASELINE"
  else
    echo "生成基线失败（rc=$rc）" >&2
  fi
  exit "$rc"
fi

# shellcheck disable=SC2086
out="$(awk -v ALLOW="$ALLOW" -v BASE="$BASELINE" -v MODE="$MODE" "$AWK_PROG" $files)"
rc=$?
[ -n "$out" ] && printf '%s\n' "$out"

exit "$rc"
