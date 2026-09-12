# ChatPage 最终收口实施规格 v1.0

> 2026-09-12。目标：不改变功能、文案、视觉、动画时长或原生桥接协议，将 `ChatPage` 收敛为 DSL 装配与 Effect adapter。

## 目标与完成定义

- `ChatPage` 不再拥有任何跨帧交互状态机、请求 revision、Timer 或流式缓冲；这些都由 `chat/<domain>/state` 的唯一 Coordinator 持有。
- DSL 物理归档到 `chat/<domain>/component`，组件只接受 Props / Actions，不读取 `ChatPage`、Provider、Store 或 Bridge。
- 页面可保留：Kuikly `body()` 装配、`ViewRef`、原生桥接注册、路由、Store/Provider Effect adapter、以及纯布局参数。
- 每刀都有 `StatePort + Coordinator + Scheduler/Port + Effect`（适用时）和 fake port 测试；保持 R1/R4/R5。

## 纵切与提交顺序

1. **Compare Insight / Card session**：对比候选、股票/术语对比卡、流式解读、卡片 focus/repair、drill、sub-thread。
2. **Message action / selection**：文本选择、复制/引用/追问菜单的 mounted → presented 时序；页面保留原生选择 ref。
3. **Composer focus / voice**：输入栏展开、键盘布局后的焦点恢复、录音生命周期、音量与取消手势；Bridge 只由 Effect adapter 调用。
4. **Composer assistant**：@ / 斜杠触发、候选远端搜索、参数面板 render key、mention/context 组装，拆为 state 与 DSL 组件。
5. **Session chrome**：回顶按钮、follow-up 两帧呈现、图片预览、玻璃模式、历史筛选及会话瞬态，合并为小状态域。
6. **DSL 物理归档**：Composer、Message action、Compare overlay、Session chrome 从 `ChatPage` 迁入 feature component；无行为改动。
7. **门禁与收尾**：增加 package/import warning 检查；Android/H5 手测并以独立提交收尾。

## 2026-09-12 已完成批次

- `abf05ca`：实体长按/拖拽/二义实体/行情预览状态机。
- `b0e5cf6`：股票与术语对比解读的候选、卡片与流式会话。
- `98e257a`：消息文本选择及复制/引用菜单的两拍呈现状态机。
- `1947a7a`：文字 Composer 展开、键盘避让后的焦点恢复。
- `d727fe3`：语音按住录制、上滑取消、转写、错误和布局恢复。
- `dcb2c8b`：卡片展开/焦点/修复锁、钻取与解读子线程；`SubThreadState` 回归 chat 域。
- `cce2da2`：@/命令参数共用的证券与板块候选行 DSL 组件。

当前 `ChatPage` 已不再持有以上领域的 Timer、revision 或流式状态；余下最大边界是
Composer assistant 的触发/远端检索/参数面板状态，以及会话 chrome 的小状态域。

## 不变行为

- Compare 解读的 pairKey/revision、逐字输出、错误重试与术语/股票互斥保持不变。
- 消息菜单及 Composer 的挂载/呈现两拍和动画注册顺序保持 R4/R5。
- 语音取消阈值、计时频率、输入栏焦点恢复延迟、原生 callback 形状保持原值。
- 所有页面 Observable 只能在 `attr`、`event`、`vif`、`vfor`、`vbind` 中读取。

## 验收

- 每刀：targeted fake-port tests、`compileDebugKotlinAndroid`、`compileKotlinJs`、`git diff --check`。
- 收尾：`testDebugUnitTest --rerun-tasks`、Android/H5 Chat 手测（输入、流式、语音、实体拖拽、股票/术语对比、消息复制/引用、历史切换）。
