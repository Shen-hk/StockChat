# 股问 StockChat

> **做股民的解释器，不做荐股机。**

基于 Kotlin Multiplatform 与腾讯 Kuikly 的 AI 股票问答应用。用户用自然语言提问，AI 以「Markdown 文本 + 结构化行情卡片」混排作答，卡片可直接下钻个股详情页；长按正文里的股票名，顶栏灵动岛即时浮现迷你行情——**聊着天就把盘看了**。一套共享代码同时运行在 Android、iOS、OpenHarmony 和 H5。

技术底座：腾讯 Kuikly（KMP）+ OpenAI 兼容大模型（DeepSeek 等）+ 腾讯 / 东方财富公开行情接口。

> 行情来自免费公开接口、可能延迟；AI 内容均为解释性信息，仅供参考，不构成投资建议。

---

## 项目亮点

- **意图与数据分离** — LLM 只在回答里输出卡片意图（` ```card:xxx ` 围栏块），价格、涨跌幅、K 线序列等数字 100% 由端侧 Provider 拉取填充，行情数字不可能被模型幻觉污染。
- **协议驱动的卡片体系** — 14 类卡片（行情侧 7 + 数据侧 7），`CardRegistry` 按类型分发渲染器，未注册类型自动降级；FULL / COMPACT / MINI 三级密度在聊天流、详情页、顶栏行情条三处复用。
- **三流合一交互** — 文本流（实体可点击）× 卡片流（折叠态一句话结论，展开看图表全量指标）× 联动层（长按实体 → 顶栏灵动岛，松手即收，不打断对话心流）。
- **聊看一体闭环** — 提问 → 混排回答 → 点卡片 / 实体 → 详情页 → 回到对话（恢复滚动位置），全程不跳出上下文。
- **三级降级链** — 真实接口 → 本地缓存 → 确定性离线数据，界面诚实标注「在线真实 / 缓存 / 离线演示」与数据时间，不拿 Mock 冒充在线结果。
- **commonMain 自绘图表** — 分时 / 日 / 周 / 月 K、MA5/10/20、均价线、十字光标与时间刷，全部用 Kuikly Canvas 纯 Kotlin 绘制，坐标与刻度计算在纯函数层，可跨端复用、可单测。
- **四端同源 + 平台隔离层** — `expect/actual` 的 `PlatformPolicy` 做单一开关，一端验证期的修复默认只对该端生效，不外溢到其他端。
- **合规与反游戏化** — 不推荐、不预测、不给目标价；无庆祝动画、无 FOMO 榜单、无一键下单；涨跌色走语义 token（A 股红涨绿跌，附 ▲▼ 箭头做色盲友好）。

---

## 构建产物

仓库不预置安装包与演示视频，按「四端跑通」一节自行构建：

| 产物 | 位置（构建后） |
|---|---|
| Android APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| H5 静态产物 | `shared/build/dist/js/developmentExecutable/`（配合 h5App 静态服务） |
| OpenHarmony HAP | `ohosApp/entry/build/default/outputs/default/` |
| iOS | `iosApp.xcworkspace`（Xcode 运行） |

---

## 评审快速开始

```bash
# 1) 跨端单测（最常用的回归入口）
./gradlew :shared:testDebugUnitTest

# 2) 架构门禁
./gradlew :shared:architectureCheck

# 3) 构建并安装 Android 包
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

验收主链路：**聊天提问 → 混排回答（正文 + 行情卡）→ 点卡片 / 长按实体 → 个股详情 → 返回对话**。首次启动无 Key 时会引导进入「API 设置」。

---

## 四端跑通

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.7（仓库内 `./gradlew`） |
| Kotlin | 2.1.21（鸿蒙变体 2.0.21-ohos） |
| Kuikly | 2.25.0-2.1.21 |
| Android Gradle Plugin | 8.5.2 |
| Android SDK | compileSdk 34，minSdk 23 |
| iOS | Xcode + CocoaPods |
| OpenHarmony | DevEco Studio |

### API Key 配置（与常见做法不同，请留意）

**Key 不写入源码、`local.properties`、URL 参数或 `BuildConfig`。** 启动 App 后从聊天页右上角进入「API 设置」，填 API 地址、模型名与 Key，可先测试连接再保存；配置只保存在当前设备。行情接口（腾讯 / 东财）无需密钥。

预设服务商均为 OpenAI 兼容的 `chat/completions` 接口，选中预设后只需填 Key：DeepSeek（默认）、Kimi、小米 MiMo、智谱 GLM、通义千问、豆包、腾讯混元、OpenAI，或自定义任意兼容端点。

### Android

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### iOS

```bash
# 先同步 KMP Framework（CocoaPods 插件），再装 Pod 依赖
./gradlew :shared:linkPodDebugFrameworkIosX64
cd iosApp && pod install
open iosApp.xcworkspace   # Xcode 选 iosApp target，Run（⌘R）
```

### H5

```bash
./gradlew :shared:jsBrowserDevelopmentExecutableDistribution
# 开发期默认从 127.0.0.1:8083 加载共享业务脚本（h5App 静态服务）
```

### OpenHarmony

```bash
# ohos 任务由独立 settings 文件注册，需 DevEco SDK 环境
./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64
# libshared.so 拷入 ohosApp/entry/libs/arm64-v8a/，头文件与页面资源同步后：
cd ohosApp && hvigor --mode module -p module=entry@default assembleHap
```

Windows 下可用根目录 `runOhosApp.ps1` 一键完成 so 构建、同步、打包与安装；也可用 DevEco Studio 打开 `ohosApp` 直接 Run。签名请使用你自己的证书与 Profile（`build-profile.json5` 中的签名配置是本机材料引用，勿使用他人路径）。

---

## 核心体验

### 问 AI，得到带行情的回答

输入「腾讯和宁德时代最近怎么样」，回答由 Markdown 文本与结构化卡片混排组成。SSE 流式输出 + 打字机增量渲染，支持停止与重试；流式中卡片 JSON 未闭合时显示骨架卡，闭合瞬间替换。多轮上下文（带裁剪）+ 会话本地持久化 + 侧边栏会话历史；回答前显式复述问题理解，可一键纠正；追问建议 chips 仅在最新成功回复中生效。

### 输入增强

`@` 唤起标的联想，`/` 唤起斜杠指令（指令面板带参数填写）；按住说话、松开发送、上滑取消的语音输入（对标微信范式）；多行自适应输入框。

### 实体交互

正文中的股票名 / 代码 / 术语经 Trie 最长匹配 + 会话上下文消歧自动识别：单击进详情，长按在顶栏灵动岛预览行情并可切自选，实体还能拖拽进输入框自动组装提问上下文。

### 卡片体系（14 类）

行情侧 7 类：行情快照、走势图、AI 解读、归因链、术语、资讯、双标的对比；数据侧 7 类：资金流、财报、股东户数、龙虎榜、分红事项、公告披露、产品概念。全部支持三密度复用、降级渲染、信源与数据时间标注。

### 个股详情页

分时 / 日 K / 周 K / 月 K 切换 + MA5/10/20 + 均价线与昨收基准线，十字光标与时间刷；指标区、AI 解读与涨跌归因工作台、公司介绍、公告与研报、资金流 / 财报 / 股东户数 / 龙虎榜 / 分红等数据侧卡片。

### 市场页与热点

市场总览四视角：市场宽度、量能资金、板块竞速、连板梯队（8 时段态）；板块热点与涨停池（连板 / 封单 / 开板次数）；AI 复盘卡——复盘锚点由端侧事实槽位生成，模型只许引用时间点，坐标由端侧映射；财报日历与历史回放。

### 自选 · 风险 · 知识（三模块闭环）

| 模块 | 核心能力 |
|---|---|
| **自选股** WatchlistPage | 关注理由记录与「当初理由对照」、分组状态色带、异动标记、左滑操作 + 限时撤销、拖拽排序、久未查看提示 |
| **风险地图** RiskMapPage | 六维暴露解释器（行业集中度 / 相关性矩阵 / 波动对比 / 事件时间线 / 情绪暴露）、每周暴露快照、跑输大盘归因 |
| **知识库** GlossaryPage | 80 条术语（拼音与别名检索）、依赖路径知识地图、「下一步该懂什么」推荐、对话中「遇到即记」 |

三者构成闭环：**自选（我在看什么）→ 风险地图（押了哪些共同变量）→ 知识库（还缺什么）→ 回自选**。

### 其他

全局搜索（代码 / 名称 / 拼音 / 术语）、异动预警中心（前台轮询 + 归因说明）、分享长图与文案复制、明暗主题 + 字号档、卡片画廊页（开发期组件独立预览）。

---

## 页面与路由

页面统一在共享层用 Kuikly `@Page` 注册，跳转走 RouterModule。

| 页面 | 路由名 | 说明 |
|---|---|---|
| 路由壳 | `router` | 页面路由与宿主容器 |
| 聊天主页 | `ChatPage` | 对话、会话抽屉、输入增强、灵动岛 |
| 个股详情 | `StockDetailPage` | K 线 / 分时、AI 解读、公告研报 |
| 自选股 | `WatchlistPage` | 关注管理、理由对照、异动 |
| 风险地图 | `RiskMapPage` | 六维暴露解释器 |
| 知识库 | `GlossaryPage` | 术语、知识地图、闪卡 |
| 市场 | `MarketPage` | 四视角总览、涨停池 |
| 热点 | `HotspotPage` | 板块热点 |
| 财报日历 | `MarketCalendarPage` | 日历与历史回放 |
| 全局搜索 | `GlobalSearchPage` | 代码 / 名称 / 拼音 / 术语 |
| 异动预警 | `AlertCenterPage` | 预警收件箱 |
| 设置 | `SettingsPage` | 主题、字号、表格样式、归档 |
| API 设置 | `ApiConfigPage` | 服务商、Key、连接测试 |
| 卡片画廊 | `CardGallery` | 14 类卡片预览（调试用） |

---

## 架构

```
shared/src/commonMain/kotlin/com/kuikly/stockchat
├── page/                  Kuikly 页面（13 个业务页 + components/ 共享组件）
├── chat/                  聊天域：ViewModel、会话抽屉、欢迎区、输入、实体、灵动岛
├── detail/  watchlist/  risk/   详情、自选、风险域编排
├── cards/                 14 类卡片渲染器与三密度体系
├── protocol/              卡片协议解析（AiResponseLexer / CardPayloadParser）
├── chart/                 图表数据模型（commonMain 纯 Kotlin）
├── composer/ richtext/ voice/   输入编排、实体富文本、语音
├── data/                  端口与实现：provider（行情 / AI）、storage、mock
├── foundation/ common/ base/    设计 token、外观体系、路由与工具
└── app/
    ├── assembly/          装配根（FeatureGraph，唯一构造 Provider 的地方）
    └── platform/          Kuikly 适配器
```

依赖方向固定为 **页面 → 域编排 → data 端口**，data 不依赖 UI；**Page 不直接构造 Provider**——唯一装配点在 `app/assembly` 的 FeatureGraph，Kuikly 适配层收在 `app/platform`。规则由架构门禁脚本强制（白名单 + 基线双清单管理）。

聊天请求链路（意图与数据分离）：

```
用户问题 / 语音
      │
      ▼
ChatViewModel（依赖经 ChatDependencies 注入）
      │
      ├─ 正文分支   OpenAiCompatAiProvider ──SSE──▶ AiResponseLexer 边流边切
      │                  │                            ├─ Markdown 正文流（打字机渲染）
      │                  │                            └─ ```card:xxx 意图块 ─▶ CardPayloadParser ─▶ CardRegistry 分发
      ├─ 实体分支   Trie 最长匹配 + 会话上下文消歧 ─▶ 实体高亮 / 长按灵动岛
      └─ 行情分支   QuoteRepository ─▶ 腾讯 / 东财 Provider ─▶ 卡片数字端侧填充
      │
      ▼
混排回答（Markdown / 行情卡 / 图片）──卡片或实体点击──▶ StockDetailPage
```

---

## 数据来源与降级

- **行情**：腾讯证券公开接口（快照、分时、日 / 周 / 月 K）；东方财富公开接口（资金流、板块与涨停池、财报、股东户数、龙虎榜、分红、公告、研报）
- **AI**：任意 OpenAI 兼容服务商，应用内「API 设置」配置；预设含 DeepSeek、Kimi、MiMo、智谱、通义、豆包、混元、OpenAI
- **降级链**：真实接口 → 本地缓存（`QuoteCacheStore`）→ 确定性离线数据（`OfflineMarketInsightProvider`），界面标注当前模式与数据时间
- **失败处理**：AI 请求失败或 Key 缺失时只展示错误状态，不伪造回答；所有行情与 AI 结论保留时间戳与风险提示

---

## 测试与代码质量

```bash
# 跨端单测：协议解析、行情字段解析、图表算法、实体识别、抽屉等域状态机、架构边界
./gradlew :shared:testDebugUnitTest

# 架构门禁：扫描包依赖方向，命中未登记的反向依赖即失败（等价 bash scripts/check_architecture.sh）
./gradlew :shared:architectureCheck
```

| 项目 | 结果 |
|---|---|
| 单元测试 | **373 用例全部通过**（2026-09-13 实测，0 failures / 0 errors） |
| 架构门禁 | 0 违例（`scripts/architecture-allowlist.txt` + `architecture-baseline.txt` 双清单） |
| 分层规则 | `docs/architecture/package-rules.md` |
| 工程规模 | commonMain 218 个 Kotlin 文件、约 5.1 万行 |

域状态机（抽屉、风险、预警等）以 `PlainState + FakeScheduler` 形式在 commonTest 纯测，不依赖 Kuikly 运行时。

---

## 已知缺口（诚实标注）

- iOS 侧已跑通页面级模拟器冒烟验证，但**宿主桥的上传与语音模块尚未实现**，且 `Info.plist` 还缺相册 / 相机用途描述
- 鸿蒙侧仅完成构建链路（需 DevEco SDK 环境），未做真机走查
- H5 产物用于浏览器演示分发；生产环境必须由自有后端代理大模型请求，浏览器前端无法安全隐藏密钥
- 推送、云同步、词库热更新等 P2 项未排期

---

## 文档

`docs/` 下 49 篇编号文档 + 22 篇可交互原型 HTML，覆盖调研、PRD、交互规范、技术方案与逐波重构施工书。重点：

- [项目级目标架构与演进蓝图](docs/39-项目级目标架构与演进蓝图_v1.0.md) · [界面层次分析法（LDRS）](docs/24-界面层次分析法%28LDRS%29与三模块重构规格_v1.0.md)
- [架构分层规则](docs/architecture/package-rules.md) · 门禁脚本 `scripts/check_architecture.sh`
- [项目实施记录与后续开发计划](docs/股问StockChat_项目实施记录与后续开发计划_v1.0.md) · [阶段性汇报](docs/26-阶段性汇报_v1.0.md)

---

## 产品边界

所有 AI 内容与归因结果都属于解释性信息，**不能替代公告原文、持牌机构意见或个人投资决策**。界面不输出「建议买入」「目标价」或任何收益承诺。行情来自免费公开接口，可能存在延迟，界面上会标注数据来源与数据时间。
