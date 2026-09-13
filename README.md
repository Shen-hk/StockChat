<div align="center">

<img src="assets/images/icon.png" width="128" height="128" alt="股问 StockChat" />

# 股问 · StockChat

**做股民的解释器，不做荐股机。**

基于 Kotlin Multiplatform 与腾讯 Kuikly 构建的跨端 AI 股票工作台。用户可以用自然语言提问，在流式回答中直接查看可点击的实时行情卡片，再进入个股详情继续研究走势、事件与风险。

项目用一套共享业务与 UI 代码运行在 **Android / iOS / OpenHarmony（鸿蒙）**，把 AI 对话、行情数据、结构化卡片、金融图表和会话上下文串成一条完整链路。

[Kotlin Multiplatform] · [Kuikly 2.25.0] · [JDK 17] · [Gradle 8.7] · [AGP 8.5.2]

</div>

<div align="center">

[项目亮点](#项目亮点) · [架构设计](#项目架构) ·  [三端运行](#三端演示) · [测试质量](#测试与代码质量)

</div>

---

## 一分钟体验路径

1. 播放任意一个三端演示视频，查看「聊天提问 → 行情卡片 → 个股详情 → 返回追问」主链路。
2. Android 用户可直接安装仓库内的 Release APK；其他平台按下文步骤自行构建。
3. 进入「API 设置」，推荐「小米 MiMo」(有多模态)，填入自己的 API Key 并通过连接测试。
4. 输入「腾讯和宁德时代最近怎么样」，体验 Markdown、实体识别与实时行情卡片混排。

---

## 三端同时运行的真实样貌

> 同一份 Kuikly 共享代码，在 **Android 手机 · iOS（iPhone 13）· OpenHarmony 平板** 三种设备形态上的真实运行截图。

<div align="center">

| Android 手机 | iOS | OpenHarmony |
| :---: | :---: | :---: |
| <img src="assets/images/screenshot-android.jpg" alt="Android 手机端首页截图" width="196" /> | <img src="assets/images/screenshot-ios.png" alt="iOS（iPhone 13）端首页截图" width="211" /> | <img src="assets/images/screenshot-ohos-tablet.jpg" alt="OpenHarmony 平板端首页截图" width="276" /> |


<sub>三端共用同一套顶栏（☰ 会话抽屉 / 全局搜索 / 新建会话）、同一套欢迎区（App 图标 +「StockChat 帮你看 ×××」+ 问 AI / 看行情 双 Tab）与同一套底部输入栏（+ / 按住说话 / 声波）；差异只落在状态栏与系统手势区。「为你推荐」的问题取自端侧话题池并按会话轮换，所以三张截图里的推荐问题各不相同——这正是同一份业务代码、不同入口上下文的体现。</sub>

</div>

---

## 三端演示视频

每个视频演示同一段问股流程在三端原生机器上的真实表现（流式打字机、行情卡混排、点击下钻详情、长按实体弹灵动岛）。

| 端 | 演示视频 | 内容亮点 |
| :--- | :---: | :--- |
| **Android** | [📥 推荐下载 / 含字幕播放](assets/videos/android-demo.mp4) | 主流验证端；分时→日 K 切换、十字光标、卡片展开 |
| **iOS** | [📥 下载 / 播放](assets/videos/ios-demo.mp4) | iOS 原生手势；Kuikly 与 UIKit 互不打架 |
| **OpenHarmony（鸿蒙）** | [📥 下载 / 播放](assets/videos/ohos-demo.mp4) | 鸿蒙原生渲染管线下 K 线 / 卡片混排 |


---

## 立即下载

| 产物 | 下载 | 说明 |
| :--- | :---: | :--- |
| **Android Release 包** | [📥 `releases/StockChat-android-release.apk`](releases/StockChat-android-release.apk) | arm64-v8a · 22.8 MB · arm64-v8a 单架构 · minSdk 23 / targetSdk 34 |

---

## 项目亮点

### 可信的 AI 股票解释

- **模型负责表达，端侧负责事实**：LLM 只生成正文与结构化卡片意图；卡片中的价格、涨跌幅、K 线等行情字段由端侧 Provider 注入，避免把模型生成的数字当成实时行情。
- **数据状态可追溯**：真实接口 → 本地缓存 → 确定性离线数据三级降级，行情卡持续展示数据来源与抓取时间，不用 Mock 冒充在线结果。
- **产品边界克制**：定位是“解释器”而不是“荐股机”，不提供目标价、买卖指令或收益承诺。

### 不止是聊天框

- **文本、卡片、实体三流合一**：Markdown 流式回答与 14 类结构化卡片混排；股票名、代码和术语可识别、下钻、长按预览或拖入输入框。
- **聊看一体闭环**：提问 → 混排回答 → 行情 / 实体下钻 → 个股详情 → 返回并恢复会话位置，研究过程不丢上下文。
- **多模态与输入增强**：支持图片提问、按住说话、`@` 标的联想和 `/` 指令面板；平台能力不可用时提供明确降级提示。
- **协议驱动卡片**：行情侧 7 类 + 数据侧 7 类卡片，支持 `FULL / COMPACT / MINI` 三种密度；未知协议有兜底渲染，流式 JSON 未闭合时先展示骨架。

### 真正的跨端工程

- **一套共享业务与 UI**：Android、iOS、OpenHarmony 共用 Kotlin Multiplatform + Kuikly 实现，不是三套外观相似的页面。
- **commonMain 自绘图表**：分时 / 日 / 周 / 月 K、MA5/10/20、均价线、十字光标与时间刷均由纯 Kotlin Canvas 实现，坐标和刻度算法可单测。
- **平台差异集中收口**：网络、调度、动效策略与宿主能力通过 `expect/actual` 和 `PlatformPolicy` 隔离，单端兼容修复不会扩散到业务层。
- **质量门禁进入构建链**：373 个单元测试覆盖协议、行情解析、图表算法、实体识别与域状态机；`architectureCheck` 阻止新增反向依赖。

---

## 项目架构

### 总览

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

### 四层架构与依赖方向

| 层 | 职责 | 依赖方向 |
| :--- | :--- | :--- |
| **page（页面层）** | 业务页面与共享组件 | → 域编排 → data 端口 |
| **domain（域编排）** | 聊天、自选、风险、详情、市场、术语等业务用例 | → data 端口 |
| **data（数据端口）** | Provider、Repository、Mock、Storage | — 不依赖 UI |
| **platform（适配层）** | Kuikly 适配、平台差异（iOS/Android/OHOS） | → 平台 SDK |

- **Page 不直接构造 Provider**——唯一装配点在 `app/assembly/FeatureGraph`。
- **Kuikly 适配器仅在 `app/platform` 内**，跨页面的 UI 行为不外溢到端口层。
- **规则由架构门禁强制**：白名单 + 基线双清单管理（`./gradlew :shared:architectureCheck`）。

### 聊天请求链路（意图与数据分离）

```
用户问题 / 语音
        │
        ▼
ChatViewModel（依赖经 ChatDependencies 注入）
        │
        ├─ 正文分支   OpenAiCompatAiProvider ──SSE──▶ AiResponseLexer 边流边切
        │                    │                            ├─ Markdown 正文流（打字机渲染）
        │                    │                            └─ ```card:xxx 意图块 ─▶ CardPayloadParser ─▶ CardRegistry 分发
        ├─ 实体分支   Trie 最长匹配 + 会话上下文消歧 ─▶ 实体高亮 / 长按灵动岛
        └─ 行情分支   QuoteRepository ─▶ 腾讯 / 东财 Provider ─▶ 卡片数字端侧填充
        │
        ▼
混排回答（Markdown / 行情卡 / 图片）──卡片或实体点击──▶ StockDetailPage
```

### 数据层降级策略

```
┌──────────────┐    命中    ┌──────────────────────┐
│ 真实接口调用 │──网络异常──▶│ 本地缓存 QuoteCache  │
│ 腾讯 / 东财  │            │  (TTL 内复现)         │
└──────┬───────┘            └──────────┬───────────┘
       │ 成功                          │ 缓存 miss
       ▼                               ▼
   ┌────────────┐              ┌────────────────────┐
   │ 在线真实    │              │ 确定性离线 Mock     │
   │ UI 标注时间 │              │  UI 标注"离线演示"   │
   └────────────┘              └────────────────────┘
```

---

## 三端跑通 · 拉下项目以后

> **共同的先决条件**：JDK 17、Gradle 8.7（仓库内 `./gradlew`）、Kotlin 2.1.21（OHOS 变体 2.0.21-ohos）、Kuikly 2.25.0-2.1.21。
>
> **共同的配置动作**：App 启动后进入聊天页 → 右上角「⋮」→「API 设置」，填入 API 地址 / 模型名 / Key，先「测试连接」通过再保存；配置只存当前设备，**不写源码、不写 `local.properties`、不写 URL 参数、不写 `BuildConfig`**。

### 数据来源说明（请阅读后再调试各端）

| 端 | 行情数据来源 | 备注 |
| :--- | :--- | :--- |
| Android | 腾讯证券 `qt.gtimg.cn`（GBK）、东财 `push2/push2ex/datacenter-web` | HTTPS，间隔 ≥ 200 ms；UA 已配 |
| iOS | 同上 | iOS App Transport Security 默认放行 HTTPS |
| OpenHarmony | 同上 | 部分鸿蒙模拟器对 GBK 解析需走 GBK→UTF-8 兼容路径 |

> ⚠️ **如果免费公开数据来源临时失效**（腾讯 `qt.gtimg.cn` 或东财任何接口报超时 / 403 / 解析异常），请在 App 内进入「设置 → 数据源 → 数据接入方式」切换到 **Mock 离线演示**，所有行情卡会回落到确定性数据，并在卡顶显式标注「离线演示」与数据时点；切回「在线真实」即可恢复。
>
> **不要把 Mock 当在线结果。** UI 顶部小字就是给你和评审看数据状态的。

---

### Android

Windows / macOS / Linux 命令相同。

```bash
# 1. 构建 Debug APK（约 4–8 分钟）
./gradlew :androidApp:assembleDebug

# 2. 安装到已连接的真机或模拟器
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 3. 启动
adb shell am start -n com.kuikly.stockchat/.KuiklyRenderActivity
```

**Release 包**：

- **直接使用仓库自带的** [Release 包](releases/StockChat-android-release.apk)：先校验 SHA-256（见 §「立即下载」），再
  ```bash
  adb install -r releases/StockChat-android-release.apk
  adb shell am start -n com.kuikly.stockchat/.KuiklyRenderActivity
  ```
- 自构建：`./gradlew :androidApp:assembleRelease`（签名请按 §「构建产物 & 签名」自行配置）。

**特殊情况**：

- 首次运行前确认 `local.properties` 已设置 `sdk.dir`。
- `Gradle fileHashes.lock` 访问被拒 → 先 `./gradlew --stop` 再重试。
- 临时 Mock：见上文「数据来源说明」。

### iOS

macOS + Xcode + CocoaPods 环境。

```bash
# 1. 同步 KMP Framework 给 iOS
./gradlew :shared:linkPodDebugFrameworkIosX64

# 2. 安装 Pod 依赖
cd iosApp && pod install && cd ..

# 3. 用 Xcode 打开工作空间
open iosApp/iosApp.xcworkspace   # 选 iosApp target → Run (⌘R)
```

**特殊情况**：

- `pod install` 失败时多跑 `cd iosApp && pod repo update --verbose`。
- 模拟器与真机要分别 sync framework：模拟器用上一步 `IosX64`；真机用 `./gradlew :shared:linkPodDebugFrameworkIosArm64`。
- 暂时未实现桥接模块：上传、语音模块（详见 §「已知缺口」）。

### OpenHarmony（鸿蒙）

Windows + DevEco Studio + hvigor 环境；需 `OHOS_SDK_HOME` 与 `DEVECO_SDK_HOME` 同条 `&&` 链内 export。

```bash
# 1. 构建 libshared.so（独立 settings 文件）
./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --no-daemon

# 2. so 拷到鸿蒙工程 + 头文件同步
cp shared/build/ohos/arm64-v8a/libshared.so ohosApp/entry/libs/arm64-v8a/
# （头文件与页面资源同步脚本见 ohosApp/entry/oh-package.json5 注释）

# 3. hvigor 打包（Windows 下用 Bash 跑，PowerShell 跑会秒退）
cd ohosApp && node hvigor.js --mode module -p module=entry@default assembleHap --no-daemon --no-parallel

# 4. 安装到设备 / 启动
hdc install -r entry/build/default/outputs/default/entry-default.hap
hdc shell aa start -b com.kuikly.stockchat -a EntryAbility
```

**特殊情况**：
- 当前为 arm64-v8a 配置，请使用真实设备。
- 别加 `--parallel`，鸿蒙 hvigor 会 OOM。
- 设备锁屏时 `aa start` 报错 `10106102`——请用户先手动解锁再启动。
- 签名 `signingConfigs`：material 内 7 字段全必选（含 `certpath`），`store/keyPassword` ≥ 32 字符；本机 credential 在 IDE vault 内，AI 不可达，请使用自己的证书。
- 临时 Mock：见上文「数据来源说明」。

---

## 构建产物 & 签名

| 产物 | 路径 | 备注 |
| :--- | :--- | :--- |
| Android Debug APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | 自构建 |
| Android Release APK | `releases/StockChat-android-release.apk` | 仓库内置 |
| OpenHarmony HAP | `ohosApp/entry/build/default/outputs/default/` | 自构建 |
| iOS | `iosApp/iosApp.xcworkspace` | Xcode Run |

> Release 签名：仓库自带的 Release APK 由本机签名证书签出，仅用于评审 / 内测。请勿转给他人使用；正式分发请按合规要求自行签。

---

## 核心体验详解

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
| :--- | :--- |
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
| :--- | :--- | :--- |
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
| 设置 | `SettingsPage` | 主题、字号、表格样式、归档、数据源切换 |
| API 设置 | `ApiConfigPage` | 服务商、Key、连接测试 |
| 卡片画廊 | `CardGallery` | 14 类卡片预览（调试用） |

---

## 测试与代码质量

```bash
# 跨端单测：协议解析、行情字段解析、图表算法、实体识别、抽屉等域状态机、架构边界
./gradlew :shared:testDebugUnitTest

# 架构门禁：扫描包依赖方向，命中未登记的反向依赖即失败
./gradlew :shared:architectureCheck
```

| 项目 | 结果 |
| :--- | :--- |
| 单元测试 | **373 用例全部通过**（2026-09-13 实测，0 failures / 0 errors） |
| 架构门禁 | 0 违例（`scripts/architecture-allowlist.txt` + `architecture-baseline.txt` 双清单） |
| 分层规则 | `docs/architecture/package-rules.md` |
| 工程规模 | commonMain 218 个 Kotlin 文件、约 5.1 万行 |

域状态机（抽屉、风险、预警等）以 `PlainState + FakeScheduler` 形式在 commonTest 纯测，不依赖 Kuikly 运行时。

---



## 文档

GitHub 只保留与当前源码一致的维护参考，不收录阶段汇报、施工记录、调试材料和交互原型：

- [文档索引](docs/reference/README.md)
- [页面与功能](docs/reference/pages.md)
- [通用组件 DSL](docs/reference/components-dsl.md)
- [架构分层规则](docs/architecture/package-rules.md) · 门禁脚本 `scripts/check_architecture.sh`

---

## 排坑快查

| 现象 | 排查路径 |
| :--- | :--- |
| 行情数字全是占位 / 报错「免费接口超时」 | 切到 Mock 离线演示（设置 → 数据源），界面会明示「离线演示」；切回即恢复 |
| AI 回答空白 / 「连接失败」 | 「API 设置」→ 测试连接；检查 Key / Base URL / 模型名是否拼写一致 |
| Android Gradle `fileHashes.lock` access denied | `./gradlew --stop` 后重试 |
| OHOS `aa start` 报 `10106102` | 设备先解锁再启动 |

---

## 产品边界

所有 AI 内容与归因结果都属于解释性信息，**不能替代公告原文、持牌机构意见或个人投资决策**。界面不输出「建议买入」「目标价」或任何收益承诺。行情来自免费公开接口，可能存在延迟，界面上会标注数据来源与数据时间。

<div align="center">

**股问 StockChat · 做股民的解释器，不做荐股机。**

</div>
