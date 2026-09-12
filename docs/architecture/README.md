# docs/architecture

四层架构的**门禁**与规则文档。规则全文见 [`package-rules.md`](./package-rules.md)。

## 怎么跑

```bash
./gradlew architectureCheck
```

通过时输出一行摘要；失败时打印违规文件的规则号、相对路径与具体 import，退出码非零。

只想看当前所有命中（含已登记的债务）：

```bash
bash scripts/check_architecture.sh --list
```

脚本是纯 `find` + `awk`，零第三方依赖、可离线跑，单次约 2 秒。

## 它拦什么

6 条规则（R1–R6），核心是**依赖只能自上而下**：
`Page → Component → State → Data`，以及 `shared` / `foundation` 不得反向依赖任何 Feature。
另外 R5 / R6 对 `page/*Page.kt` 的 Timer/Provider/observable 计数和 `page/components/` 的文件数设了**不许变差**的上限。

细节与正反例见 [`package-rules.md`](./package-rules.md)。

## 命中之后怎么办

1. **优先改设计**，别让依赖方向倒挂 —— 把被依赖的类型下沉到 Feature 自己的 `domain`，或改成「只读 accessor + Actions」。
2. **确属历史债务**，才写进 `scripts/architecture-allowlist.txt`：

```text
<相对路径> | <规则号> | <被豁免的 import 符号> | <原因 / 预计删除的工作包>
```

粒度是**文件 × 规则 × 符号**，所以同一文件新增一个违规符号仍会被拦。
禁止通配符、禁止整目录豁免；每行都要写清谁在什么时候删掉它。

## 什么时候要重新生成基线

R5 / R6 的基线快照在 `scripts/architecture-baseline.txt`，只在**指标确实下降**后重新生成：

```bash
bash scripts/check_architecture.sh --baseline
```

生成后连同对应的重构一起提交，并检查 diff 里只有下降、没有上升。
**不要**为了让门禁通过而单独跑一次 `--baseline` —— 那正是这套门禁要防的事。
