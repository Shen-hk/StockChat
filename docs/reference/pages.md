# 页面与功能参考

本文覆盖 `commonMain` 中注册的全部 Kuikly 页面。路由常量定义在 [`Routes.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/common/Routes.kt)，页面依赖由 `FeatureGraph` 装配；页面只负责布局、状态绑定和副作用适配，业务时序优先下沉到 Coordinator。

## 页面清单

| 路由 | 页面类 | 面向用户 | 主要入口 |
|---|---|---:|---|
| `ChatPage` | `ChatPage` | 是 | 默认首页、详情/风险等页面的“问 AI” |
| `StockDetailPage` | `StockDetailPage` | 是 | 股票实体、行情卡、自选、搜索、市场、预警 |
| `MarketPage` | `MarketPage` | 是 | 聊天抽屉、自选导航 |
| `HotspotPage` | `HotspotPage` | 是 | 市场热点入口 |
| `MarketCalendarPage` | `MarketCalendarPage` | 是 | 市场页日历按钮 |
| `WatchlistPage` | `WatchlistPage` | 是 | 聊天抽屉 |
| `RiskMapPage` | `RiskMapPage` | 是 | 聊天抽屉、自选页 |
| `GlossaryPage` | `GlossaryPage` | 是 | 聊天抽屉、搜索、术语实体 |
| `GlobalSearchPage` | `GlobalSearchPage` | 是 | 聊天、自选、风险 |
| `AlertCenterPage` | `AlertCenterPage` | 是 | 自选页、聊天抽屉 |
| `SettingsPage` | `SettingsPage` | 是 | 聊天抽屉 |
| `ApiConfigPage` | `ApiConfigPage` | 是 | 设置页 |
| `CardGallery` | `CardGalleryPage` | 开发参考 | 聊天抽屉 |
| `router` | `RouterPage` | 本地工具 | 宿主调试入口 |
| `image_adapter` | `ImageAdapterStandardTest` | 基准工具 | 图片适配器回归入口 |

## 路由参数

| 目标页面 | 参数 | 含义 |
|---|---|---|
| `StockDetailPage` | `symbol` | 股票代码；为空时页面使用演示标的 |
| `StockDetailPage` | `from` | 来源路由，用于返回与上下文 |
| `StockDetailPage` | `krTransition=islandExpand` | 灵动岛展开到详情的容器转场 |
| `ChatPage` | `question` | 预填的用户问题 |
| `ChatPage` | `focusNote` | 与自然语言问题分离的事实注记 |
| `ChatPage` | `focusSymbol` | 结构化焦点标的，用于行情上下文 |
| `ChatPage` | `openedViaAskAi=1` | 宿主用于收拢“详情 ⇄ 对话”导航栈 |
| `ChatPage` | `autoAsk`、`autoAskDelay` | 平台验证钩子；普通产品入口不使用 |
| `GlossaryPage` | `krTransition=islandExpand` | 术语岛展开转场 |
| `GlossaryPage` | `glossaryMode=list` | 直接进入词条列表 |
| `GlossaryPage` | `glossaryExpand=1` | 列表模式下展开目标内容的验证参数 |

统一导航辅助函数为 `openPage`、`closePage`、`openStockDetail`、`openChatWithQuestion`、`openGlossary` 和 `openUrl`。`openStockDetail` 会在转场期间预取行情，调用方不要绕过它重复实现预热。

## ChatPage — AI 对话

源码：[`ChatPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/ChatPage.kt)

- 欢迎态按市场上下文展示问题 starter，可新建会话并恢复历史会话。
- AI 请求支持流式文本、Markdown、结构化行情卡和错误状态；行情数字由端侧 Provider 填充。
- 股票与术语实体支持点击、长按预览和顶部灵动岛联动；股票可继续进入详情。
- Composer 支持普通输入、`@` 标的提及、`/` 指令及参数面板、图片/相机/文档附件、语音输入、停止生成。
- 对话流支持卡片展开、半屏/全屏 CardSheet、追问、对比面板、消息操作、图片预览、回到顶部和滚动位置保持。
- 抽屉提供会话管理及市场、自选、风险、搜索、预警、术语、设置等主导航。

主要状态域：`ChatWelcomeCoordinator`、`ChatScrollCoordinator`、`EntityInteractionCoordinator`、`CardSheetCoordinator`、`ComposerFocusCoordinator`、`ChatDrawerCoordinator`、`QuoteIslandCoordinator`、`VoiceInputCoordinator` 和 `CardInteractionCoordinator`。

## StockDetailPage — 个股详情

源码：[`StockDetailPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/StockDetailPage.kt)

- 行情 Hero 展示价格、涨跌、数据来源、实时脉冲和滚动后的紧凑顶栏。
- 支持分时与日/周/月 K 线、均线、成交量、区间缩放、十字光标、句图锚点和新闻旗标。
- 新闻跑马灯支持预览、打开原文、加入图表旗标及作为 AI 提问上下文。
- 提供一行归因、AI 流式解读、句子与图表联动、因子重放和下一阶段情景推演。
- 公司介绍/公司数据切换，展示行业、业务卡、公告研报、重要性信息和同业对比。
- 支持加入/移出自选、复制代码、记录关注理由、复盘关注时点以及底部快捷操作。

主要状态域：`DetailDataCoordinator`、`DetailAiInsightCoordinator`、`DetailAiForecastCoordinator`、`DetailOverlayCoordinator` 和 `DetailChartInteractionCoordinator`。

## MarketPage — 市场总览

源码：[`MarketPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/MarketPage.kt)

- 展示指数概览、交易阶段说明、市场宽度、量能、板块热度和热点标的。
- 叙事时间轴支持盘中快照回放、拖动时间刷、指标切换和当前切片定位。
- 板块阶梯可逐层展开；指数、板块和新闻支持长按/点击 Peek，再进入详情。
- 新闻带支持暂停、预览、原文与 AI 解读；市场 AI 区提供当前盘面解释。
- 支持下拉刷新、刷新结果反馈、吸顶指数栏和分阶段入场动效。

数据由市场概览、热点、新闻及 AI Provider 组合；真实接口失败时按配置回落到缓存或离线数据，并显示来源状态。

## HotspotPage — 热点与涨停

源码：[`HotspotPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/HotspotPage.kt)

- 展示板块排行、热点快照和涨停标的。
- 加载真实热点数据并保留离线快照回退。
- 标的行可进入个股详情，页面展示数据来源标记。

## MarketCalendarPage — 市场日历

源码：[`MarketCalendarPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/MarketCalendarPage.kt)

- 展示财报、公告和市场事件列表。
- 标记在线、缓存或离线数据来源。
- 带标准股票代码的事件可直接进入个股详情。

## WatchlistPage — 自选股

源码：[`WatchlistPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/WatchlistPage.kt)

- 展示组合概览、分组、筛选和行情列表。
- 搜索并添加标的；点击进入详情；可编辑关注理由。
- 支持左滑操作、删除后的限时撤销、长按菜单和拖拽排序。
- 提供自选简报/收件箱预览，并联动风险地图和预警中心。

主要状态域：`WatchlistCoordinator`、`WatchlistDragCoordinator` 和 `WatchlistBriefCoordinator`。

## RiskMapPage — 风险地图

源码：[`RiskMapPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/RiskMapPage.kt)

- 风险星图聚合行业集中度、相关性、波动、事件和情绪等维度。
- 维度面板提供解释、行业暴露、标注及组合归因。
- 支持历史快照、风险对照、AI 风险解释和预警规则入口。
- 空自选时引导搜索或添加标的；相关标的可进入详情或对话。

主要状态域：`RiskDataCoordinator`、`RiskSkyCoordinator`、`RiskAiCoordinator` 和 `RiskAlertCoordinator`。

## GlossaryPage — 投资术语库

源码：[`GlossaryPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/GlossaryPage.kt)

- 知识地图按类别展示掌握进度、依赖关系和推荐学习路径。
- 列表模式支持名称、拼音、别名搜索与类别筛选。
- 词条支持展开、解释深度切换、已读/掌握记录和遇见次数统计。
- Flow 学习模式以卡片队列浏览，可拖动、点击、跳转依赖并同步阅读状态。

## GlobalSearchPage — 全局搜索

源码：[`GlobalSearchPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/GlobalSearchPage.kt)

- 同时搜索股票代码、名称、拼音和术语。
- 支持“全部/股票/术语”等标签过滤；空查询展示浏览内容。
- 股票结果进入详情，术语结果进入术语库。

## AlertCenterPage — 预警中心

源码：[`AlertCenterPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/AlertCenterPage.kt)

- 将价格异动、风险暴露和规则事件组织为可筛选收件箱。
- 支持展开、标记全部已读、按标的静音、风险暴露静音和安静时段。
- 可为自选标的新增、启停和删除预警规则。
- 消息可进入个股详情、风险地图、自选页或相关术语。

## SettingsPage — 设置

源码：[`SettingsPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/SettingsPage.kt)

- 管理浅色/深色主题、字号档和表格显示风格。
- 切换真实行情与离线演示数据源，并持久化到本地。
- 进入 AI API 配置页；外观变化通过页面主题系统即时生效。

## ApiConfigPage — AI 服务配置

源码：[`ApiConfigPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/ApiConfigPage.kt)

- 选择内置模型服务商，或编辑兼容 OpenAI 的 endpoint、model 和 API key。
- 每个预设维护独立配置槽；支持显示/隐藏密钥、保存、清除和切换。
- “测试连接”在保存前验证服务可用性，并呈现成功/失败状态。
- 键盘出现时自动调整布局，保证密钥输入和操作按钮可达。

## CardGalleryPage — 卡片组件画廊

源码：[`CardGalleryPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/page/CardGalleryPage.kt)

- 展示协议注册的行情类与数据类卡片及不同密度。
- 验证折叠/展开、Accordion、钻取、子线程和 CardSheet 半屏/全屏交互。
- 可拉取实时行情替换演示数据，用于组件回归，不作为普通用户主流程。

## RouterPage — 本地路由工具

源码：[`RouterPage.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/RouterPage.kt)

- 输入 `pageName&key=value` 跳转任意已注册 Kuikly 页面。
- 缓存最近输入，支持本地/AAR 调试模式提示。
- 仅用于宿主接入和开发排查，不应作为产品导航入口。

## ImageAdapterStandardTest — 图片适配器基准

源码：[`ImageAdapterBenchmarks.kt`](../../shared/src/commonMain/kotlin/com/kuikly/stockchat/ImageAdapterBenchmarks.kt)

- 路由名为 `image_adapter`，用于宿主图片适配器兼容性回归。
- 覆盖 base64、page assets、HTTP/HTTPS、GIF 的加载状态与分辨率。
- 验证 capInsets 拉伸、内存缓存 `ImageRef` 和 Canvas `drawImage`。
- 这是工程基准页面，不属于用户功能导航。
