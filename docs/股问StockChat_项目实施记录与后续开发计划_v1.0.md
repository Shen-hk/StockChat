# 股问 StockChat：项目实施记录与后续开发计划

> 文档版本：v1.0
>
> 更新日期：2026-08-24
>
> 代码基线：`ecb62e5`（`feat: 应用内API配置 + DeepSeek真实调用`）
>
> 参考基线：`股问StockChat_立项文档_v1.1.md`
>
> 适用对象：项目负责人、产品、开发、测试与后续接手人员

## 1. 文档目的

本文档用于持续记录 StockChat 的真实实施状态，重点回答四个问题：

1. 当前版本已经完成了什么；
2. 原立项文档 M1—M6 分别落实到什么程度；
3. 当前还存在哪些功能缺口、技术债和交付风险；
4. 后续按什么顺序开发，以及每个阶段如何验收。

状态说明：

- **已完成**：实现、构建或运行验证、验收证据均已具备；
- **部分完成**：主结构已存在，但仍缺少立项文档中的关键能力或验收项；
- **未开始**：当前代码中没有形成可验收实现；
- **待验证**：已有代码或构建产物，但尚未在目标环境完成运行验收。

## 2. 项目概述

股问 StockChat 是一个基于 Kuikly / Kotlin Multiplatform 的中文股票解释助手。用户以自然语言提问，应用将解释文本、行情卡片、图表、归因、资讯与追问建议混排展示，并通过股票详情页承接更深信息。

### 2.1 产品边界

- 只提供信息解释，不提供买卖、仓位、目标价或收益承诺；
- AI 内容和归因结果不能替代公告原文、持牌机构意见或个人投资决策；
- 在线接口失败时必须展示真实状态，不能用 Mock 回答冒充在线成功；
- API Key 不进入源码、Git、URL 参数或 Android 构建产物。

### 2.2 当前总体结论

项目已经从工程空壳推进到**可运行 MVP**：

- Android 与 H5 共用核心业务、协议、数据和 UI 代码；
- 聊天、文本/卡片混排、详情页和基础交互主流程已形成；
- 腾讯行情接口适配和离线演示数据已接入；
- 应用内 API 配置页面和 DeepSeek 真实请求已落地；
- Android Debug APK 与 H5 本地产物已经生成；
- 当前主要缺口集中在真实数据全链路、真正 SSE、多轮会话、持久化、完整图表、组件通用性和自动化交付。

## 3. 当前状态总览

| 维度 | 当前状态 | 判断 | 下一关键动作 |
|---|---|---|---|
| 跨端工程 | Android、H5 已构建；iOS/OHOS 保留工程 | 主端部分完成 | 补 CI、真机与其他目标端验证 |
| AI 闭环 | 应用内配置 + DeepSeek 非流式真实请求 | 部分完成 | 真正 SSE、多轮上下文、会话持久化 |
| 行情数据 | 腾讯快照/分时/K线适配 + Mock/缓存雏形 | 部分完成 | 全页面统一 Provider、TTL 和数据模式 |
| 卡片体系 | 8 类模型、协议混排、画廊和降级骨架 | 部分完成 | Registry 单一分发、三密度和渐进披露 |
| 详情与图表 | 详情页、指标、归因、资讯、分时几何和均线算法 | 部分完成 | 蜡烛图、周期切换、真实序列合并 |
| 实体交互 | 基础识别、点击、Context Bar、建议 chips | 部分完成 | Trie、消歧、长按、动效和生命周期完善 |
| 测试交付 | 9 项单测；Android APK 与 H5 本地产物 | 部分完成 | 集成测试、CI、H5 部署和演示回放 |

## 4. 当前架构与模块

### 4.1 运行链路

```text
ChatPage / API 设置
        ↓
ChatViewModel（消息、发送、停止、清空）
        ↓
DeepSeekProvider / QuoteProvider
        ↓
AiResponseLexer + CardPayloadParser + CardAssembler
        ↓
文本、结构化卡片、Context Bar、股票详情页
```

### 4.2 工程结构

| 模块 | 职责 | 当前限制 |
|---|---|---|
| `shared/src/commonMain` | 跨端页面、状态、协议、Provider、卡片和算法 | 部分页面仍存在 Mock 直连 |
| `shared/src/commonTest` | 协议、配置、图表、实体和行情解析测试 | 缺 ViewModel、缓存和集成测试 |
| `androidApp` | Android 壳工程和平台桥接 | 已构建 APK，未完成本轮真机运行验收 |
| `h5App` | Web 渲染入口与桥接 | 已有本地产物，尚未部署；生产 AI 需要后端代理 |
| `iosApp` | iOS 壳工程 | Windows 环境无法完成构建和运行验证 |
| `ohosApp` | OHOS 壳工程 | 当前未纳入主交付验证范围 |

### 4.3 核心实现位置

| 用途 | 位置 |
|---|---|
| 聊天状态与 AI 调用 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/chat/ChatViewModel.kt` |
| API 配置模型 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/config/AiConfig.kt` |
| API 配置页面 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/page/ApiConfigPage.kt` |
| AI Provider | `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/provider/AiProvider.kt` |
| 行情 Provider | `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/provider/QuoteProvider.kt` |
| 腾讯行情适配 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/provider/TencentQuoteProvider.kt` |
| 卡片体系 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/cards` |
| 响应协议 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/protocol` |
| 图表算法 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/chart` |
| 实体识别 | `shared/src/commonMain/kotlin/com/kuikly/stockchat/richtext` |
| 自动化测试 | `shared/src/commonTest/kotlin/com/kuikly/stockchat` |

## 5. 已实施记录

### 5.1 Git 提交时间线

| 提交 | 日期 | 实施主题 | 主要结果 |
|---|---|---|---|
| `a25e3c4` | 2026-08-24 | Initial commit | Kuikly 多端工程、Android/H5/iOS/OHOS 壳与基础桥接 |
| `933e15c` | 2026-08-24 | M1 数据基础设施与核心算法 | 格式化、证券模型、Mock 数据、QuoteProvider、协议解析、实体识别、图表算法和基础测试 |
| `cced8e6` | 2026-08-24 | 对话混排卡片与实时数据链路 | 聊天、卡片、详情页、腾讯行情、实体富文本、画廊和行情解析测试 |
| `de8517e` | 2026-08-24 | Android 与 H5 跨端交付闭环 | 修复跨端构建配置，形成 Android APK 与 H5 本地产物，补充 README |
| `2918012` | 2026-08-24 | 支持本地配置 DeepSeek API Key | 阶段性采用构建期本地配置，后续已被应用内配置方案替代 |
| `ecb62e5` | 2026-08-24 | 应用内 API 配置 + DeepSeek 真实调用 | 移除构建期 Key 注入，增加配置页、连接测试、设备本地保存、真实请求与真实错误呈现 |

### 5.2 已实现能力明细

#### 工程与跨端

- Kuikly / Kotlin Multiplatform 工程已形成；
- Android 与 H5 共用 `commonMain` 业务实现；
- Android、H5、iOS 已补齐 API 配置页所需的平台导航 actual；
- Android Debug APK 已生成；
- H5 本地产物已生成并完成过浏览器主流程检查。

#### 聊天与 AI

- 已实现用户消息、助手消息、发送状态、停止和清空；
- 已实现文本与 `card:type` 结构化内容混排；
- 已接入 DeepSeek OpenAI 兼容接口；
- 已实现应用内 API 地址、模型名称和 API Key 配置；
- 已实现 Key 显示/隐藏、清空、保存和连接测试；
- 未配置、鉴权失败或接口异常时展示真实错误。

当前限制：

- 请求仍使用 `stream=false`；
- 页面中的逐步显示是完整响应到达后的本地分片，不是真正服务端 SSE；
- 当前请求只包含系统提示和本轮问题，没有发送完整多轮上下文；
- 会话未持久化，重启后不能恢复。

#### 行情数据

- 已定义 QuoteProvider 抽象；
- 已接入腾讯行情快照、分时和 K 线接口解析；
- 已提供确定性的 Mock / 离线演示数据；
- 已有内存缓存和失败回退雏形；
- 详情页可以尝试拉取真实快照。

当前限制：

- 聊天卡片与 Context Bar 仍可能直接读取 `MockDataBank`；
- 缓存 TTL、数据来源、更新时间和数据模式没有完整统一；
- 快照、分时和 K 线数据合并不完整，真实快照到达后图表可能缺少序列。

#### 卡片与协议

- Lexer 支持文本块、卡片块和未闭合围栏骨架；
- Parser 支持结构化负载解析和未知卡片降级；
- 已有行情、图表、归因、术语、洞察、对比、资讯和追问建议模型；
- 已有 `CardRegistry`、`CardShell` 和卡片画廊页；
- 已定义 FULL / COMPACT / MINI 三种密度。

当前限制：

- `CardShell` 仍通过中心 `when` 分发，Registry 不是唯一入口；
- 三档密度没有在所有 Renderer 中完整落实；
- 折叠、展开、全页和完整合规脚注未形成统一链路；
- 风险卡片和字段缺失降级测试仍待补充。

#### 详情、图表与交互

- 已有股票详情页、行情头、指标区、AI 解读区、归因和资讯区；
- 已有分时几何计算和 K 线移动平均算法；
- 已有 MINI 分时 Canvas；
- 已有基础股票/术语识别、点击实体和 Context Bar；
- 已有建议 chips 的点击发送能力。

当前限制：

- 缺少完整蜡烛 K 线 Canvas；
- 缺少分时/K线切换和日/周/月周期；
- 实体识别尚未使用 Trie，缺少上下文消歧和歧义选择；
- 缺少长按、滑入淡出动画、MINI 行情条和完整建议生命周期。

### 5.3 API 配置方案演进

`2918012` 中的构建期本地 Key 配置是过渡方案。`ecb62e5` 已完成以下替换：

- 不再从 `local.properties` 读取 DeepSeek Key；
- 不再通过 Android `BuildConfig` 注入 Key；
- 不再通过 H5 URL 参数传递 Key；
- 改为应用运行时配置；
- 配置只保存在当前设备；
- 未配置或接口失败时不再静默切到 Mock 回答。

## 6. 原立项里程碑落实情况

| 里程碑 | 状态 | 已落实 | 未完成或待补 |
|---|---|---|---|
| M1 工程骨架与基础设施 | 部分完成 | 跨端工程、路由/主题、Mock、腾讯三件套、基础测试 | CI；Android 真机行情验收；iOS/OHOS 验证 |
| M2 聊天闭环 | 部分完成 | 聊天状态、停止、混排、DeepSeek 真实请求、应用内配置 | 真 SSE；性能验收；降级状态点；持久化；多轮上下文 |
| M3 卡片体系 v1 | 部分完成 | 模型、协议、骨架、未知卡降级、画廊和主要卡片 | Registry 单一分发；异常测试；三密度×两主题；渐进披露 |
| M4 详情承接与图表 | 部分完成 | 详情页、指标、AI 区、归因、资讯、分时几何和均线计算 | 蜡烛图；周期切换；真实序列合并；滚动位置恢复 |
| M5 交互创新 | 部分完成 | 基础实体识别、点击、Context Bar、建议 chips | Trie、消歧、长按、动效、MINI 条、渐进披露、增强输入 |
| M6 演示打磨与交付 | 部分完成 | Android APK、H5 本地产物、README、本文档 | H5 部署、回放脚本、主题回归、录屏、环境检查、开源材料 |

总体判断：当前版本横跨 M1—M6 并形成了可演示主流程，但尚未满足任何后期里程碑的全部验收标准。后续应优先补齐 P0 基础链路，而不是继续横向增加功能。

## 7. 当前缺口、技术债与风险

| 优先级 | 问题 | 影响 | 处理方向 |
|---|---|---|---|
| P0 | Chat 卡片和 Context Bar 仍可能直读 Mock | 页面间数据不一致，无法证明真实链路 | 统一 QuoteRepository / Provider 注入 |
| P0 | DeepSeek 目前 `stream=false` | 停止不等于中断服务端流，长回答首字延迟高 | Ktor SSE 解析、增量协议、Job 取消 |
| P0 | 无多轮上下文和会话持久化 | 追问语义断裂，重启丢失对话 | 上下文裁剪策略 + 本地会话仓库 |
| P0 | H5 由浏览器前端保存 Key 并直连 | 无法安全隐藏密钥，可能受 CORS 限制 | 正式环境使用自有后端代理 |
| P1 | 完整 K 线、周期切换和真实序列合并缺失 | 详情页图表不完整 | Canvas 蜡烛图、MA、周期和数据合并 |
| P1 | CardRegistry 未成为唯一入口 | 新增卡片仍需修改中心分支 | Registry 驱动解析与渲染 |
| P1 | 密度、折叠、消歧、长按和动效不完整 | 交互创新缺完整验收矩阵 | 先在 Gallery 组件化，再接业务页 |
| P1 | 缺 CI、集成测试、H5 部署和回放脚本 | 交付不可重复 | 建立流水线和固定演示问题集 |
| P2 | KSP/Gradle/Kuikly 存在弃用警告 | 后续升级成本增加 | 单独安排依赖兼容性治理 |

## 8. 后续开发计划

后续按“先统一底层链路，再完善组件与交互，最后完成交付”推进。

### 8.1 阶段 1：真实数据与 AI 基础设施闭环（P0）

#### 工作包 A：行情数据统一

1. 定义单一 `QuoteRepository`，统一封装 Tencent、缓存与离线演示数据；
2. 移除 `CardAssembler`、Context Bar 和详情页对 `MockDataBank` 的业务直连；
3. 实现快照 TTL 60 秒、分时/K线 TTL 5 分钟；
4. 为所有行情结果提供数据来源和更新时间；
5. 实现 `online / cache / offline` 数据模式；
6. 合并快照、分时和 K 线刷新，避免真实快照到达后图表为空。

验收标准：

- 同一股票在聊天、Context Bar 和详情页的价格、涨跌幅、更新时间一致；
- 在线、缓存、离线三种模式可触发，且来源状态可见；
- 断网时不崩溃、不伪装在线，缓存或离线数据有明确标识；
- Provider、TTL、缓存过期和页面一致性测试通过。

建议阶段提交：

```text
feat: 真实行情贯通 + TTL缓存 + 数据模式统一
```

#### 工作包 B：DeepSeek SSE、多轮与持久化

1. 将请求改为 `stream=true`；
2. 解析 SSE `data` 事件、增量文本和 `[DONE]`；
3. 将网络流、Lexer 和 UI 更新串成单一可取消 Job；
4. 点击停止时同时取消网络请求和 UI 更新；
5. 发送最近多轮上下文，并实现消息数/字符数裁剪规则；
6. 增加本地会话仓库，保存消息、卡片负载、错误和完成状态；
7. 增加重试、超时和可理解的错误分类。

验收标准：

- 真实回答按服务器事件逐步出现；
- 点击停止后网络请求立即取消，页面不再追加内容；
- 追问能够引用上一轮上下文；
- 应用重启后会话完整恢复；
- 鉴权失败、额度不足、超时、断网有不同且明确的提示。

建议阶段提交：

```text
feat: DeepSeek SSE + 多轮上下文 + 会话持久化
```

### 8.2 阶段 2：图表与卡片体系完备（P0/P1）

#### 工作内容

- 实现 K 线 Canvas 和 MA5/10/20；
- 实现分时/K线切换与日/周/月周期；
- 完成快照、分时、K线按时间戳合并；
- 让 `CardRegistry` 成为模型解析和 Renderer 分发的唯一入口；
- 完成 FULL / COMPACT / MINI × Light / Dark 矩阵；
- 完成骨架、折叠、展开、全页、缺字段降级和合规脚注。

验收标准：

- 真实分时与 K 线序列可以完整绘制；
- 坐标、涨跌语义和 MA 算法测试通过；
- 切换周期不闪空，离线缓存仍可展示；
- 新增卡片无需修改中心 `when`；
- Gallery 覆盖所有卡片 × 密度 × 主题；
- 未知类型和缺字段均能可读降级。

建议阶段提交：

```text
feat: K线Canvas + 周期切换 + 详情数据合并
feat: CardRegistry重构 + 三档密度 + 渐进披露
```

### 8.3 阶段 3：实体与交互创新（P1）

#### 工作内容

- 使用 Trie 完成词典匹配和最长匹配；
- 增加股票/术语上下文消歧与歧义选择器；
- 完成实体点击、长按、Context Bar 滑入淡出和手势协调；
- Context Bar 使用 MINI 行情 Renderer，并接入统一真实数据；
- 完成 suggestions 加载、完成、错误、替换和过期生命周期；
- 视工期增加 `@股票` 联想、对比卡增强和左滑加自选。

验收标准：

- 核心问题集实体识别抽检准确率不低于 95%；
- 歧义实体可以人工选择；
- 长按和列表滚动无手势冲突；
- Context Bar 数据与详情页一致；
- 旧 suggestions 不会在新回答后继续生效。

建议阶段提交：

```text
feat: 实体Trie + 消歧 + ContextBar交互升级
```

### 8.4 阶段 4：质量、发布与演示交付（P1）

#### 自动化质量

- 协议异常：未闭合围栏、非法 JSON、未知卡、字段缺失、原生代码块；
- 数据链路：在线成功、缓存命中、缓存过期、断网降级、来源状态；
- ViewModel：发送、停止、重试、清空、恢复和快速连续操作；
- 跨端构建：Android/JVM 单测、JS 编译、Android assemble、H5 distribution；
- UI 回归：Light/Dark、主要宽度、Gallery 全矩阵和主流程截图。

#### 交付物

| 交付物 | 完成条件 |
|---|---|
| CI 流水线 | 每次提交自动测试、JS 编译和 Android 构建；失败阻断合并 |
| H5 演示地址 | 可公开访问；AI 经后端代理；固定版本可回滚 |
| 演示回放 | 4 分钟固定问题集、一键重置、真实/缓存/离线均可演示 |
| 主题与响应式回归 | 全页面 × 两主题 × 主要宽度通过 |
| 最终文档与录屏 | 实施版文档、cards KDoc、构建指南和演示录屏 |
| 环境检查 | Key/余额、腾讯连通性、代理健康和主动断网演练 |

建议阶段提交：

```text
test: 跨端回归测试 + CI + H5交付文档
```

## 9. 建议执行顺序

后续提交建议按以下顺序执行：

1. `feat: 真实行情贯通 + TTL缓存 + 数据模式统一`
2. `feat: DeepSeek SSE + 多轮上下文 + 会话持久化`
3. `feat: K线Canvas + 周期切换 + 详情数据合并`
4. `feat: CardRegistry重构 + 三档密度 + 渐进披露`
5. `feat: 实体Trie + 消歧 + ContextBar交互升级`
6. `test: 跨端回归测试 + CI + H5交付文档`

其中第 1、2 项为共同前置，不建议调换到图表和交互之后。

## 10. 构建与验证记录

### 10.1 标准命令

```powershell
.\gradlew.bat :shared:testDebugUnitTest :shared:compileKotlinJs
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:jsBrowserDevelopmentExecutableDistribution
```

Android Debug APK：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### 10.2 2026-08-24 复验结果

| 检查项 | 结果 | 备注 |
|---|---|---|
| Git 工作区 | 通过 | 生成项目文档前无未提交代码变更 |
| `commonTest` | 通过 | 共发现并执行 9 个 `@Test` |
| `shared` JS 编译 | 通过 | 与单测同一命令复验 |
| Android APK | 已有产物 | 约 4.11 MB；本轮未做物理设备运行验收 |
| H5 | 已有本地产物 | 此前完成浏览器主流程检查；尚无部署 URL |
| iOS / OHOS | 待验证 | Windows 无法构建 iOS；OHOS 未纳入本轮主交付 |

### 10.3 Windows 文件占用故障记录

复验首次在 `:shared:generateDebugRFile` 失败：

```text
Couldn't delete shared/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar
```

停止项目 Gradle 守护进程后重跑成功，说明本次失败属于 Windows 文件句柄占用，不是源码编译错误。

处理顺序：

1. 停止 IDE 中正在运行的构建或同步任务，避免同一项目目录并发构建；
2. 在项目根目录运行：

   ```powershell
   .\gradlew.bat --stop
   ```

3. 重新执行原构建命令；
4. 若仍失败，关闭占用该项目的 IDE 后再试；
5. 只有确认本项目没有构建进程后，才清理对应 `build` 中间产物。

当前非阻断警告：

- KSP 在 Kotlin Multiplatform 中的旧配置方式已弃用；
- 当前 Gradle 脚本存在 Gradle 9 兼容性警告；
- Kuikly/Pager 部分 API 已弃用；
- Windows 环境会禁用 iOS Kotlin/Native 目标。

## 11. API 配置与安全说明

### 11.1 当前配置方式

1. 启动应用；
2. 从聊天页右上角进入“API 设置”；
3. 填写 DeepSeek 兼容 API 地址、模型名称和 API Key；
4. 先执行连接测试；
5. 测试通过后保存并返回聊天页；
6. 发送问题触发真实接口请求。

### 11.2 已落实的安全边界

- Key 不写入源码、`local.properties`、URL 参数或 Android `BuildConfig`；
- 配置只保存在当前设备；
- Android 配置不会进入 APK；
- 未配置、鉴权失败和接口异常均展示真实错误；
- 日志、错误提示和文档不得回显完整 Key。

### 11.3 H5 生产环境要求

浏览器前端无法安全隐藏供应商 API Key。H5 浏览器本地存储只适合开发调试，正式环境必须通过自有后端代理完成：

- 项目自身用户鉴权；
- 供应商 Key 保管；
- 模型白名单；
- 请求超时和长度限制；
- 并发与频率限制；
- 成本和额度保护；
- 请求 ID、耗时和错误类型审计；
- 供应商错误映射与可见降级状态。

## 12. 开发和文档维护规则

### 12.1 阶段完成标准

一个阶段只有同时满足以下条件，才能标记为完成：

- 代码实现完成；
- 必要的单测或集成测试通过；
- Android/H5 等目标端完成相应构建或运行验证；
- 有可复现命令、截图、日志或录屏作为验收证据；
- README 和本文档同步更新；
- 提交内容边界清晰，没有混入无关改动。

### 12.2 文档更新要求

每次阶段性提交后至少更新：

- 第 5 节“已实施记录”；
- 第 6 节“里程碑落实情况”；
- 第 7 节“当前缺口、技术债与风险”；
- 第 8 节“后续开发计划”；
- 第 10 节“构建与验证记录”。

状态只能沿以下方向推进：

```text
未开始 → 进行中 / 部分完成 → 已完成
```

没有验收证据不得标记为“已完成”。

## 13. 变更记录

| 版本 | 日期 | 代码基线 | 说明 |
|---|---|---|---|
| v1.0 | 2026-08-24 | `ecb62e5` | 首次建立项目级实施记录、里程碑状态、后续四阶段计划、构建运维和 API 安全说明 |
