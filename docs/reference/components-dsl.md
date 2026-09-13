# 通用组件 DSL 参考

StockChat 的 UI DSL 主要是 `ViewContainer<*, *>` 扩展函数：调用时组件直接挂到当前容器，数据和动作通过参数传入。组件不直接创建 Provider、Store、Bridge 或页面实例；页面负责组装只读状态和回调。

## 基本写法

```kotlin
View {
    attr { flexDirectionRow(); alignItemsCenter() }

    AppTopBar(
        title = "自选",
        theme = page.theme,
        onBack = page::closePage,
    )

    vif({ page.loading }) {
        ChartLoadingSkeleton(page.theme)
    }
}
```

在 `vif`、`vbind`、`vfor` 的 creator 内必须调用当前 receiver 的组件扩展：写 `Component(...)` 或 `this.Component(...)`，不要写 `page.Component(...)`，否则节点会被挂到页面根而不是指令容器。

## 响应式与动画约束

- `observable` 只在 `attr {}`、`event {}`、`vif {}`、`vfor {}` 或 `vbind {}` 中读取；普通 `body()` 和子构建闭包只拿到初始快照。
- 状态决定视图是否存在或列表数量时使用 `vif` / `vbind` / `vfor`，不要在普通 builder 中 `if`。
- `vfor` 的 item creator 顶层必须且只能生成一个非指令子节点；多节点先包一层 `View`。
- 动画驱动 observable 在 `attr` 中最后读取，目标属性随后写入，`animate(...)` 必须是最后一条语句。
- 新挂载视图的入场使用 mounted → presented 两帧状态；预状态周期要提前注册下一次变化需要的动画。
- 主题在深层 builder 中显式使用 `page.theme`；在动画 `attr` 外缓存主题，避免覆盖动画 driver。
- `CanvasContext.batchDraw` 统一走 `PlatformProfile.canvasBatchDrawSupported`，不可写死为 `true`。

## 基础通用组件

| DSL | 用途 | 关键输入 |
|---|---|---|
| `AppTopBar` | 页面统一顶栏 | 标题、副标题、返回动作、右侧 `AppTopBarAction`、紧凑指标 |
| `FeatureTile` / `FeatureTileRow` | 功能入口卡片及同行排列 | 标题、说明、图标、点击动作 |
| `GlassContainer` | 玻璃卡容器 | renderer、圆角、内容；实时玻璃能力由平台策略决定 |
| `GlassBackdrop` | 页面/浮层玻璃背景 | renderer、背景和可见状态 |
| `Attr.applyGlassSurfaceSkin` | 普通 Attr 复用玻璃表面皮肤 | theme、边框、阴影、圆角 |
| `DataModeBadge` | 在线/缓存/离线状态徽标 | `SourceStamp`/数据模式、theme |
| `SourceStampLine` | 数据来源与时间说明 | `SourceStamp`、theme |
| `InsightSectionTitle` | 洞察区标题 | title、subtitle、theme |
| `ExplanationCard` | 解释文本卡 | text、来源标记、theme |
| `MetricTile` | 指标值单元 | label、value、note、accent |
| `SegmentBar` | 比例分段条 | 各段数量/颜色 |
| `DivergingBar` | 正负双向比例条 | 正负值、颜色与说明 |
| `UndoBar` | 限时撤销提示 | 文案、剩余时间、撤销动作 |
| `RowGestureLayer` | 行级点击/长按/横滑手势层 | 手势开关、阈值与回调 |
| `RouterNavBar` | 基准/本地工具页导航栏 | `RouterNavigationBarAttr` 初始化 |

`AppTopBarAction` 封装图标绘制与点击；`AppTopBarMetric` 用于滚动后紧凑行情。所有功能页应优先使用 `AppTopBar`，避免各自复制安全区和返回逻辑。

## 字体与图标 DSL

- `TextAttr.fontSizeScaled`、`TextAttr.lineHeightScaled`：按当前页面字号档缩放文本。
- `InputAttr.fontSizeScaled`、`TextAreaAttr.fontSizeScaled`、`TextAreaAttr.lineHeightScaled`：输入控件对应缩放。
- `LineIcon*`：统一线性图标 DSL，包括导航、搜索、收藏、预警、媒体、趋势、复制、刷新、分享等；签名通常为 `(color, size)`，有状态的图标增加 `selected` 等参数。
- `GeneratedIcon0`…`GeneratedIcon10`：由矢量路径生成的 Canvas 图标；新增业务代码优先使用有语义名称的 `LineIcon*`。

## 富文本与卡片

| DSL | 用途 |
|---|---|
| `EntityRichText` | 渲染可点击/长按的股票与术语实体文本 |
| `EntityStreamingMarkdown` | 流式 Markdown 与实体识别组合渲染 |
| `CollapsibleCard` | 按 `CardModel`/`CardContext` 渲染可折叠协议卡 |
| `CardShell` | 卡片统一边框、标题、状态、操作区和未知类型降级 |
| `CardSheetHost` | 卡片半屏/全屏承载、拖动和关闭 |

卡片调用方传入 `CardContext`，其中包含密度、主题、加载状态和动作回调。渲染分发统一经过 `CardRegistry`；新卡片类型应注册 renderer，不应在页面写类型分支。

## 市场与新闻组件

| DSL | 用途 |
|---|---|
| `AtmosphereBackdrop` | 市场/详情页氛围背景 |
| `MarketNarrativeAxis` | 盘中叙事轴、时间刷和事件钉 |
| `NewsMarquee` | 循环新闻跑马灯 |
| `NewsTape` | 可交互新闻带 |
| `NewsSummaryBar` | 新闻摘要与当前项说明 |

新闻组件只接收 `NewsItem` 列表、可见状态和动作，不直接打开 URL 或发起 AI 请求；这些副作用由页面回调完成。

## Chat 组合组件

| DSL | 用途 |
|---|---|
| `WelcomeSection` | 欢迎态、starter 和市场上下文入口 |
| `ChatTopNav` | 聊天顶栏及股票/术语灵动岛 |
| `ChatDrawer` | 会话列表和主功能导航抽屉 |
| `ChatMessageView` | 单条用户/助手消息、卡片和消息动作 |
| `ComposerChrome` | Composer 外框、折叠/展开和焦点视觉 |
| `ComposerInputRow` | 文本输入、发送/停止、语音切换 |
| `ComposerActionRow` | `@`、`/`、媒体等快捷动作 |
| `VoiceBar` | 按住说话、录音反馈与取消手势 |
| `DateDivider` | 会话日期分隔 |
| `ComposerGuideRow` | 输入引导内容 |
| `RegressionQuestionRow` | 回归问题快捷入口 |
| `ActiveComparePanel` / `TermComparePanel` | 股票或术语对比面板 |
| `MarketFallbackPrompt` | 行情不可用时的降级提示 |
| `ImagePreviewOverlay` | 全屏图片预览 |
| `MessageActionOverlay` | 复制、重试等消息操作浮层 |

Composer 的大型参数优先收敛到 `*Props` 数据类；状态变化通过 Coordinator 产生 Effect，页面翻译为桥调用或导航。

## 自选组件

| DSL | 用途 |
|---|---|
| `WatchlistScrollContent` | 自选列表主体、分组及行交互 |
| `WatchlistAggregateHeader` | 组合涨跌概览 |
| `WatchlistFilterChip` | 分组/状态筛选 |
| `WatchlistSearchOverlay` | 搜索和添加标的 |
| `WatchlistMenuOverlay` | 长按/更多菜单 |
| `WatchlistReasonEditorOverlay` | 关注理由编辑 |
| `WatchlistInboxPreviewRow` | 简报入口行 |
| `WatchlistBriefCard` | 自选简报卡 |

列表数据使用 `ObservableList` 配合 `vfor`；拖拽状态读取须由 `dragSymbol` 门控，取消拖拽时不要把动画 driver 与布局清理放进同一批更新。

## 风险组件

| DSL | 用途 |
|---|---|
| `RiskSkyChart` | 风险维度星图 Canvas |
| `renderSkyMode` | 星图模式整体布局 |
| `renderSkyAiBlock` | 风险 AI 解释区 |
| `renderRiskPanels` | 风险维度面板集合 |
| `renderHeadline` | 风险摘要标题 |
| `renderDimensionPanel` | 单风险维度详情 |
| `renderIndustryContent` | 行业集中度内容 |
| `renderAnnotation` | 用户风险标注 |
| `renderSnapshotHistory` | 历史风险快照 |
| `renderAttributionEntry` | 组合归因入口 |

风险组件统一接收 `RiskUiProps`，避免组件层反向依赖 `RiskMapPage` 或数据 Provider。

## 个股详情组件

详情页组件属于业务内复用 DSL，不应被基础页面随意依赖：

| 分组 | DSL |
|---|---|
| 页面骨架 | `DetailHeroSection`、`DetailChartCard`、`DetailCompanyInfoSection`、`DetailNewsTicker`、`DetailAiInsightBlock`、`DetailAttributionBoard`、`DetailOverlays` |
| 图表 | `DetailTimelineChart`、`ChartLegend`、`ChartSegment`、`ChartViewportControls`、`ChartLoadingSkeleton` |
| 指标/布局 | `SectionLabel`、`TickerText`、`SecondaryMetricRow`、`BusinessInsightGrid`、`RevealBlock` |
| 复盘/归因 | `RevisitCard`、`QuickReasonChips`、`MaterialityBadge`、`BalanceSpectrumBlock`、`FactorReplayBlock`、`DisclosureMaterialityBlock` |
| AI | `AiInsightBlock`、`AttributionForecastWorkbench` |
| 浮层/操作 | `IndustryCompareOverlay`、`DetailBottomBar` |

图表交互状态由 `DetailChartInteractionCoordinator` 管理，Overlay 互斥由 `DetailOverlayCoordinator` 管理；组件只渲染 accessor 并发送 action。

## 新增组件检查单

1. 判断归属：跨 Feature 放 `foundation/ui`；业务专用放 `<feature>/**/component`；不要继续扩张历史 `page/components`。
2. 用 `ViewContainer<*, *>` 扩展表达组合组件，用 `*Props` 聚合大量只读值和动作。
3. 不在组件内获取 Provider、Store、Bridge、Router 或页面实例。
4. 所有动态存在性、列表和文本都建立真实响应式依赖。
5. `vfor` creator 顶层恰好一个实体节点；滚动容器内验证事件命中。
6. 动画按 driver 的上一周期注册语义设计，并提供 reduced-motion 行为。
7. Canvas 使用平台能力开关；至少通过 Android 编译、单测，涉及鸿蒙实现时额外跑 OHOS link。
8. 将新的公共 DSL 补充到本文对应分类。
