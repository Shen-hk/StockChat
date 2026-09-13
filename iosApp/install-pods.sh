#!/usr/bin/env bash
# install-pods.sh — `pod install` 的兜底封装（克隆后开箱即用）
#
# 为什么不直接跑 pod install：
#   1. macOS 自带 Ruby 2.6 + 用户目录安装的 CocoaPods 1.15.x（activesupport 6.x）
#      在 concurrent-ruby >= 1.3.5 的环境下会直接崩——1.3.5 起它不再负责加载
#      logger，而旧版 activesupport 假定它已加载：
#          uninitialized constant ActiveSupport::LoggerThreadSafeLevel::Logger
#   2. 系统 locale 不是 UTF-8 时（如 C / POSIX），CocoaPods 在路径 Unicode
#      归一化处报：
#          Encoding::CompatibilityError: Unicode Normalization not appropriate
#          for ASCII-8BIT
#
# 本脚本对两者自动探测、自动绕过；环境正常时行为与裸 `pod install` 完全一致，
# 额外参数原样透传（如 ./install-pods.sh --repo-update）。
set -euo pipefail
cd "$(dirname "$0")"

# 1) locale 兜底：仅当当前 locale 不是 UTF-8 时才改写，不覆盖用户已有设置
case "${LC_ALL:-${LANG:-}}" in
  *[Uu][Tt][Ff]*8|*[Uu][Tt][Ff]*) : ;;  # 已是 UTF-8，不动
  *) export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ;;
esac

POD_BIN="$(command -v pod || true)"
if [ -z "$POD_BIN" ]; then
  echo "[install-pods] 未找到 pod 命令，请先安装 CocoaPods：" >&2
  echo "  brew install cocoapods        # 推荐" >&2
  echo "  sudo gem install cocoapods    # 或系统 Ruby" >&2
  exit 1
fi

# 2) 探测 pod 能否直接启动；不能则用 `ruby -rlogger` 预加载 logger 绕过
#    （在本来就没问题的环境里不会走到这一支，保持原生路径）
if "$POD_BIN" --version >/dev/null 2>&1; then
  exec "$POD_BIN" install "$@"
fi

if ruby -rlogger "$POD_BIN" --version >/dev/null 2>&1; then
  echo "[install-pods] 检测到 CocoaPods 受 concurrent-ruby>=1.3.5 移除 logger 的影响，"
  echo "[install-pods] 已改用 \`ruby -rlogger\` 预加载方式执行（仅影响本次进程，不改动 gem 环境）"
  exec ruby -rlogger "$POD_BIN" install "$@"
fi

# 3) 两种方式都起不来：把真实报错打出来让用户看，而不是静默失败
echo "[install-pods] pod 在直接与 -rlogger 两种方式下均无法启动，原始报错如下：" >&2
"$POD_BIN" --version || true
exit 1
