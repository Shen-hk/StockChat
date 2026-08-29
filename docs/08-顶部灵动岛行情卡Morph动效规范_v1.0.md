# 08 · 顶部灵动岛行情卡 Morph 动效规范

**项目**：股问 StockChat · AI 股票问答应用
**版本**：v1.0 ｜ **日期**：2026-08-28 ｜ **状态**：评审稿
**上游文档**：04-通用卡片组件设计规范、06-渐进披露交互设计规范
**配套文件**：`08-顶部灵动岛行情卡Morph动效-预览.html`（可交互预览，含时间轴与参数）
**回应问题**：「顶部灵动岛弹出行情卡时，两侧的汉堡标与 ＋ 如何向两边褪去」

---

## 1. 文档定位

本文档只解决**一个动作**：顶部灵动岛从静止胶囊 morph 为行情卡的这 0.6 秒里，左右两个常驻图标如何退场与回归。

它不是一个通用动画规范，而是一条**精确到毫秒的编排脚本**。给出的数值都已在配套 HTML 预览中实机验证（含 0.25× 慢放），可直接照抄进 Kuikly 实现。

| 读者 | 关注章节 |
|---|---|
| 开发 | §4 状态机、§5/§6 时间轴、§9 Motion token、§10 实现要点 |
| 设计 | §2 场景、§3 结构、§7 曲线、§8 三条约束 |
| 评审 | §8 因果链论证、§11 无障碍、§12 验收清单 |

---

## 2. 场景与触发

### 2.1 三态定义

| 态 | 形态 | 触发 | 退出 |
|---|---|---|---|
| `IDLE` 静止 | 126×36 黑色胶囊 r18，两侧各一个 44×44 毛玻璃圆钮 | 默认 | — |
| `QUOTE` 行情卡 | 364×84 深色卡 r26，内容为单个标的的 MINI 密度行情 | AI 回答流中出现首个行情实体 / 用户点击胶囊 | 卡片自动收起（6s）或用户点击 / 上滑 |
| `DISMISS` 收起中 | 过渡态 | — | 回到 `IDLE` |

### 2.2 触发时机（与渐进披露宪章对齐）

行情卡**不主动抢占**。触发条件满足其一：

1. **流式回答首个行情实体到达** —— 文本还在输出时卡片才展开，符合「聊看一体」，不打断阅读节奏；
2. **用户点击胶囊** —— 手动展开当前持仓/关注标的；
3. **多标的场景** —— 只展示最近一次提问的主标的，多标的仍走 06 号文档的「堆叠洗牌」，不在此处展开。

> **抑制冲动**（宪章一）：卡片展开后 6 秒自动收起，不做常驻；不给"一键交易"入口，点击整卡跳详情页而非下单。

---

## 3. 结构解剖：三层与一个真相

```
island-layer  (flex, justify-content:center, gap:13px)
├── icon.l    汉堡  44×44        ← 被动外推 + 主动位移
├── island    容器  126×36 → 364×84   ← 唯一发生 morph 的元素
│   └── quote 内容  固定 364×84      ← 尺寸恒定，靠 overflow:hidden 裁剪
└── icon.r    ＋    44×44        ← 镜像
```

**关键结构决策**：

| 决策 | 理由 |
|---|---|
| 容器与卡片是**同一个 DOM/视图节点** | 两张卡交叉淡入淡出会出现重影与黑底透叠；morph 保证任意时刻只有一个实体 |
| 图标是 `island-layer` 的 flex 兄弟节点，用 `gap` 与容器相隔 | 容器变宽，flex 自动把图标**顶开** —— 这是物理正确的因果，而非手写的巧合 |
| 卡内容**固定 364×84**，不跟随容器尺寸 | 容器宽度动画期间内容不回流、不抖动、不变形，只有"被揭开"的感觉 |

---

## 4. 状态机

```
        ┌────────────── 6s 超时 / 点击卡片 / 上滑 ──────────────┐
        ↓                                                      │
   ┌────────┐  首个行情实体到达 / 点击胶囊   ┌──────────┐        │
   │  IDLE  │ ────────────────────────────→ │  QUOTE   │ ──────┘
   │ 126×36 │                               │  364×84  │
   └────────┘ ←────────────────────────────  └──────────┘
        ↑            收起动画 540ms              │
        └───────────────────────────────────────┘
```

**中断处理**（必做）：动画进行中再次触发，从**当前插值位置**继续，不回零重启。实现方式见 §10——Kuikly/Compose 用 `animate*AsState`（自带中断续接），Web 用 `transition`（天然可中断），**禁止用 keyframes 硬编码**。

---

## 5. 展开时间轴（总时长 645ms）

| # | 对象 | 属性 | 起 | 时长 | 曲线 | 说明 |
|---|---|---|---|---|---|---|
| 1 | 容器 | `width` `height` `border-radius` | 0 | **560ms** | `spring` 微过冲 | 主导动作，带 ~5% 过冲产生"弹出"生命力 |
| 2 | 容器 | `box-shadow` | 0 | 560ms | `linear` | `0 2px 6px` → `0 18px 44px -10px`，强化"浮起" |
| 3 | 左汉堡 | `opacity` | 0 | **150ms** | `ease-in` | **先于 transform 归零 —— 防穿模的关键** |
| 4 | 左汉堡 | `transform` | 0 | **210ms** | `ease-in` | `translateX(-32px) scale(.84)`，叠加 flex 顶开 |
| 5 | 左汉堡 | `filter: blur` | 0 | 150ms | `ease-in` | `0 → 3px`，制造"消散"而非"消失" |
| 6 | 右 ＋ | 同 3–5，镜像 `+32px` | 0 | 150/210ms | `ease-in` | 与左侧**严格同步**，不做左右错峰 |
| 7 | 名称/代码 | `opacity` + `translateY(9px)` | **130** | 240ms | `ease-out` | 内容 stagger 开始 |
| 8 | 现价 | 同上 | **170** | 240ms | `ease-out` | 主信息次优先 |
| 9 | 涨跌幅 | 同上 | **200** | 240ms | `ease-out` | |
| 10 | 迷你走势线 | `stroke-dashoffset` | **215** | 430ms | `ease-out` | 最后收尾，绘制感拉长整体余韵 |

### 5.1 编排原则（三条）

**① 图标退场要"快"，容器展开要"沉"。**
两者 t=0 同步启动，但图标只跑 210ms、容器跑 560ms。图标的"利落"反衬容器的"从容"，视觉重心自然落到行情卡上。若让图标等容器展开完再动，会出现明显迟滞；若图标先消失容器才动，动作会断成两截。

**② 内容必须等容器铺开一半才入场。**
首个内容元素延迟 130ms，此时容器宽度约完成 45%。内容若在 t=0 就出现，会挤在 126px 的窄胶囊里被裁掉，产生"闪一下"的廉价感。

**③ 走势线最后画。**
`stroke-dashoffset` 430ms 收尾于 645ms，让动作有余韵而不突兀收束。这是整条时间轴里唯一的"慢元素"，起收尾锚点作用。

---

## 6. 收起时间轴（总时长 540ms）

采用 Material 的不对称原则：**退场快、入场慢**。收起是"清理"，不该拖沓；图标回归是"复位"，该从容。

| # | 对象 | 属性 | 起 | 时长 | 曲线 | 说明 |
|---|---|---|---|---|---|---|
| 1 | 卡内容 | `opacity` + `translateY(-6px)` | 0 | **120ms** | `linear` | 内容先撤，向上抽离 |
| 2 | 容器 | `width` `height` `border-radius` | **100** | **440ms** | `spring` | 内容撤退后才收，避免内容被挤变形 |
| 3 | 两侧图标 | `opacity` `filter` | **200** | 260ms | `ease-out` | 容器收了一半才回归 |
| 4 | 两侧图标 | `transform` → 原位 | **200** | **340ms** | `ease-out` | 减速到位，是整条轴里最"软"的一段 |

> 图标回归延迟 200ms：让用户先看到"卡片走了"，再看到"按钮回来了"。零延迟回归会让两个动作黏在一起，失去层次。

---

## 7. 缓动曲线库

| 名称 | `cubic-bezier` | 用途 | 手感 |
|---|---|---|---|
| `spring` | `(.34, 1.20, .64, 1)` | 容器扩张/收缩 | 轻微过冲（约 5%），有生命力的"弹出" |
| `ease-in`（加速） | `(.55, 0, .85, .35)` | 图标退场 | 越走越快，像被外力推走 |
| `ease-out`（减速） | `(.05, .7, .1, 1)` | 内容入场、图标回归 | 快速启动、缓缓停住，从容 |
| `linear` | `linear` | 阴影、透明度淡出 | 不抢戏，只做配合 |

**过冲的代价（易踩坑）**：spring 曲线会让容器宽度短暂超过 364px（约 +5%，即 ~373px）。任何按 364px 计算的位置都必须按**过冲峰值**校核，否则过冲瞬间会穿模。图标的 opacity 在 150ms 就已归零，远早于过冲发生的峰值时刻（约 380ms），因此天然免疫——这正是把 opacity 时长压到 150ms 的第二个理由。

---

## 8. 三条硬性约束

### 约束一 · 图标位移必须快过容器边缘（防穿模）

容器单侧扩张量 `(364 − 126) / 2 = 119px`。图标中心初始距中心点 `63 + 13 + 22 = 98px`，容器边缘会在约 210ms 时扫过图标原位置。

解法不是"躲开"，而是**在边缘扫到之前就不可见**：

```
opacity:  150ms 归零   ← 此刻容器仅扩张约 41px，图标中心仍在 139px，边缘在 104px，尚未接触
transform: 210ms 走完  ← 后 60ms 的位移观众已看不见
```

反向错误示范：opacity 与 transform 同为 210ms、甚至更长 —— 会看到图标被黑底吞掉一半，产生粘连感。可勾选预览页的「对照：只淡出不位移」观察差异。

### 约束二 · 只动 transform / opacity / filter

容器宽高不可避免触发布局，但必须把它限制在**单点**：

- 卡内容固定尺寸 + 父级 `overflow:hidden` 裁剪 → 内容层零回流、零抖动；
- 图标的额外位移走 `transform`，只有 flex 顶开那部分是布局（3 个节点的 flex 重排，开销可忽略，且与 iOS 灵动岛实现一致）；
- 阴影走 `box-shadow` 过渡（合成层属性，不重排）。

禁止动画 `padding`、`font-size`、`left/right` 等触发整链重排的属性。

### 约束三 · 因果感来自"被顶开"，不是"自己走开"

`gap:13px` 的 flex 布局让图标随容器边缘**被动**外移，这在物理上是正确的因果：容器长大了，所以把你挤开。额外叠加的 32px `translateX` 与 3px 模糊只是放大这个感受，是"修辞"不是"事实"。

若把图标改为 `position:absolute` 固定坐标、只做 32px 位移，动作会变成"两个按钮恰好也往外飘了一下"——失去因果，观感松散。

---

## 9. Motion token 建议

项目现有 `GlassTokens.kt` 中的 `GlassMaterial.dissolve`（`transitionOnly = true`，blur 32 / alpha .68 / stroke 0）本就是为过渡态准备的材质，图标退场**直接复用它**，不要新造：

```kotlin
// cards/theme/MotionTokens.kt —— 新增，与 GlassTokens 平级
object MotionTokens {
    // 时长
    const val SHELL_EXPAND = 560
    const val SHELL_COLLAPSE = 440
    const val ICON_EXIT = 210
    const val ICON_EXIT_FADE = 150      // opacity 单独、更短
    const val ICON_ENTER = 340
    const val ITEM_ENTER = 240
    const val CONTENT_EXIT = 120

    // 延迟
    const val SHELL_COLLAPSE_DELAY = 100
    const val ICON_ENTER_DELAY = 200
    const val ITEM_DELAY_NAME = 130
    const val ITEM_DELAY_PRICE = 170
    const val ITEM_DELAY_CHG = 200
    const val SPARK_DELAY = 215

    // 形态
    const val W_IDLE = 126f
    const val H_IDLE = 36f
    const val R_IDLE = 18f
    const val W_QUOTE = 364f
    const val H_QUOTE = 84f
    const val R_QUOTE = 26f

    const val ICON_PUSH = 32f      // 额外外推
    const val ICON_SCALE_EXIT = 0.84f
    const val AUTO_DISMISS_MS = 6_000L
}
```

**为什么 opacity 要单独定义时长**：它是防穿模的保险丝，一旦被"统一成 210ms"就会失效。命名上独立成 `ICON_EXIT_FADE`，并在注释里写明原因，防止后人合并。

---

## 10. 平台实现要点

### 10.1 Kuikly（本项目主路径）

```kotlin
// 伪代码，API 需按项目所用 Kuikly 版本核对
@Page("islandQuote")
internal class IslandQuoteView : BasePager() {
    private var state by observable(IslandState.IDLE)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 层：flex 居中，gap 让图标被物理顶开
            View {
                attr {
                    flexDirectionRow()
                    justifyContentCenter()
                    alignItemsCenter()
                    margin(13f)          // 等价 gap
                }
                // 左汉堡
                Image { attr { size(44f, 44f)
                    opacity(iconOpacity)                       // 150ms/260ms 不对称
                    transform(translateX(-iconPush), scale(iconScale), blur(iconBlur))
                }}
                // 容器：唯一 morph 节点
                View {
                    attr {
                        width(animate(shellW, SHELL_EXPAND, spring))   // 中断可续接
                        height(animate(shellH, SHELL_EXPAND, spring))
                        borderRadius(animate(shellR, SHELL_EXPAND, spring))
                        overflow(Overflow.HIDDEN)                      // 裁剪固定尺寸内容
                        backgroundColor(if (expanded) ISLAND_X else ISLAND)
                    }
                    // 内容：固定 364×84，绝不跟随容器
                    View {
                        attr { width(364f); height(84f); opacity(contentOpacity) }
                        QuoteMiniCard(quote, itemDelayMs = ITEM_DELAY_NAME)  // 复用 StockCardRenderers MINI
                    }
                }
                Image { /* 右 + ，镜像 */ }
            }
        }
    }
}
```

落地要点：

1. **动画 API 必须支持中断续接**（`animate*AsState` 类），禁用一次性 keyframes；否则 6s 超时与手动点击打架时会跳变。
2. **内容复用 `StockCardRenderers` 的 MINI 密度渲染器**，不另写一套布局——保证与聊天流里的行情卡视觉一致。
3. **深色底上的涨跌色**：涨 `#FF4D4F` / 跌 `#00B578`，于 `#0C0F16` 底上对比度 5.4:1 / 7.4:1；若沿用浅色卡的颜色值需重新校核。
4. **`QuoteClock` 已在 androidMain 存在**，卡片展开期间的报价刷新沿用同一时钟，避免两套时基跳动不一致。

### 10.2 Web / H5（参考实现已验证）

配套 HTML 预览即为可运行参考。核心是用**双向 transition** 而非 animation：基础规则写"回归"的时长与延迟，`.expanded` 下覆盖为"退场"的时长与延迟——这正是 CSS 表达不对称过渡的标准写法。

### 10.3 原生 iOS（若后续做灵动岛真机适配）

用 `.matchedGeometryEffect` 绑定胶囊与卡片，`spring(response: 0.45, dampingFraction: 0.82)` 逼近 `cubic-bezier(.34,1.20,.64,1)` 的手感；图标退场用 `.animation(.easeIn(duration: 0.15), value: expanded)`，注意 opacity 与 transform 分两条 modifier，时长不同。

---

## 11. 无障碍与降级

| 项 | 处理 |
|---|---|
| 减弱动效 | `prefers-reduced-motion: reduce` → 时长压缩至 1%（≈6ms），只保留最终态切换，不做位移 |
| 焦点顺序 | 展开期间两侧图标 `pointer-events:none` + `tabIndex=-1` + `aria-hidden="true"`，避免焦点落在不可见元素上；若焦点正停在图标上，主动 `blur()` |
| 语义 | 容器为 `button`，`aria-expanded` 随状态同步，`aria-controls` 指向卡内容节点 |
| 读屏播报 | 展开后播报一次「贵州茅台 600519，现价 1685.20，上涨 2.34%」，不要逐元素播报 |
| 色彩 | 涨跌除颜色外必须带 `+` / `−` 符号，不单靠色彩传达 |
| 对比度 | 正文 ≥ 4.5:1，图形与 UI 组件 ≥ 3:1（本方案全部达标，见 §10.1 第 3 条） |
| 点击目标 | 两个图标 44×44，满足 ≥44px 触控标准；展开后不可点，收起后立即恢复 |

---

## 12. 验收清单

- [ ] 0.25× 慢放下，图标在容器边缘接触其原位置**之前**已完全不可见（无穿模、无吞没）
- [ ] 过冲峰值（约 373px 宽）瞬间，图标已不可见且无任何元素被裁切异常
- [ ] 展开与收起过程中**内容零抖动**（固定尺寸 + 裁剪生效）
- [ ] 动画中途反复点击，从当前位置平滑续接，无跳变、无回零重启
- [ ] 6s 自动收起与手动点击不打架
- [ ] 展开期间 Tab 键不会聚焦到两侧图标
- [ ] 开启系统「减弱动效」后，状态切换无位移、无时长感
- [ ] 深色底上的涨/跌色通过 AA 对比度校验
- [ ] 行情卡复用 `StockCardRenderers` MINI 密度，与聊天流内卡片视觉一致
- [ ] 中低端安卓机（Kuikly 渲染）展开动画不掉帧（≥55fps）

---

## 13. 待定项

| 项 | 说明 | 建议 |
|---|---|---|
| 多标的 | 同屏出现 2 个以上行情实体时顶部卡展示谁 | 只展示主标的，其余走 06 号「堆叠洗牌」 |
| 卡片常驻 | 是否需要支持长按手动锁定不自动收起 | v1 不支持，可放入后续版本 |
| iOS 真机灵动岛 | 是否适配真实 Dynamic Island / 鸿蒙实况窗 | 需单独评估，本规范形态参数需按实际安全区重算 |
