# docs/architecture

四层架构整改的**规则与闸门**。规则正文见 [`package-rules.md`](./package-rules.md)。

## 怎么跑

```bash
./gradlew architectureCheck          # 推荐：与其它 Gradle 任务同一入口
bash scripts/check_architecture.sh   # 等价（约 4s；Windows Git Bash 下已做单进程优化）
```

通过时输出：

```text
architectureCheck: OK -- 6 条规则全部通过（既有债务豁免 25 条）
```

失败时逐条打印 `[规则] 相对路径:行号: 命中的 import`，并以非零退出码结束构建。

## 三个文件各管什么

| 文件 | 作用 | 谁改 |
|---|---|---|
| `scripts/check_architecture.sh` | 门禁实现（find + 单次 awk，零第三方依赖） | 只在新增规则时改 |
| `scripts/architecture-allowlist.txt` | **既有债务豁免**：`路径 \| 规则 \| 原因 \| 预计清除的工作包` | 每个工作包清掉债务后**删行** |
| `scripts/architecture-baseline.txt` | **R5 计数基线**（Page 的 setTimeout / Provider 构造 / by observable） | 计数下降时**下调**以收紧 |

## 加白名单的正确姿势

```text
# ✅ 逐文件逐条，写清原因与清除它的工作包
detail/chart/state/DetailChartUiState.kt | R3 | 图表 UI 状态持有 page.components.ChartFlag | A-4

# ❌ 禁止整目录豁免、禁止不写原因
detail/chart/state/* | R3
```

## 实现注记（别踩）

- 规则写在 awk 里而不是逐文件 grep：Git Bash（Windows）每次 spawn grep 约 200–500ms，逐文件逐规则会跑到分钟级；单进程扫描实测 4s。
- **不要**为了复用而引入 `mktemp` 临时文件：Git Bash 下 `mktemp` 返回 Windows 风格路径，退出时 `rm` 会走 safe-delete 垫片并失败重试，实测白白多花约 15s。
- 规则命中行号取自 awk 的 `FNR`，即**文件内真实行号**，可直接跳转。
