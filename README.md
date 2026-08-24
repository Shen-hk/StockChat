# 股问 StockChat

股问是一个基于 Kuikly / Kotlin Multiplatform 的中文股票解释助手。产品以对话为主入口，将自然语言、行情卡片、归因卡片、图表和追问建议混排展示；只提供信息解释，不提供买卖或仓位建议。

项目当前实施状态、已完成记录、已知缺口和后续开发顺序见：

- [项目实施记录与后续开发计划](docs/股问StockChat_项目实施记录与后续开发计划_v1.0.md)

## 已实现

- Android 与 H5 共用业务、协议、数据和 UI 代码
- Markdown + `card:type` 流式协议，支持半包骨架和未知卡片降级
- 行情、分时、归因、洞察、术语、对比、资讯和追问卡片
- Trie 股票/术语实体识别、会话优先消歧和人工标的选择；单击股票实体进入详情，长按则滑入复用 MINI Renderer 的行情预览
- 股票详情页、指标区、图表、归因和资讯区
- 腾讯行情接口适配，失败时自动回退到内存缓存或确定性的离线演示数据
- 统一 QuoteRepository：聊天卡片、实体预览条与详情页共享行情、TTL 缓存和数据模式
- DeepSeek OpenAI 兼容接口适配，以及应用内 API 配置和连接测试
- DeepSeek 真 SSE、可取消生成、多轮上下文裁剪和设备本地会话恢复
- 追问建议仅在最新成功回复中有效，避免历史建议继续触发
- 详情页分时 / 日 K / 周 K / 月 K 切换，腾讯复权日/周/月 K 线与 MA5/10/20 绘制
- 明暗主题、A 股红涨绿跌语义和风险提示

## 工程结构

- `shared/src/commonMain`：跨端页面、卡片、协议、数据 Provider 和算法
- `shared/src/commonTest`：协议、格式、图表、实体和行情解析测试
- `androidApp`：Android 壳工程
- `h5App`：Web 渲染入口

## 构建与验证

在 Windows PowerShell 中运行：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :shared:compileKotlinJs
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:jsBrowserDevelopmentExecutableDistribution
```

Android Debug APK 输出到：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

H5 需要同时提供 `shared` 的 `nativevue2.js` 与 `h5App` 产物。开发时可分别启动两个本地静态服务；`h5App/src/jsMain/resources/index.html` 默认从 `127.0.0.1:8083` 加载共享业务脚本。

## 在线服务配置

腾讯行情不需要密钥。DeepSeek 配置不写入源码、`local.properties`、URL 参数或 Android `BuildConfig`。启动应用后，从聊天页右上角进入“API 设置”，填写 API 地址、模型名称和 API Key，可先测试连接再保存。

配置只保存在当前设备。Android 本地配置不会进入 APK；H5 配置保存在浏览器本地存储中，但浏览器前端无法安全隐藏密钥，因此正式 Web 环境必须改为由自己的后端代理 DeepSeek 请求。未配置或接口调用失败时，页面会显示真实错误，不会自动用 Mock 回答冒充在线结果。

## 产品边界

所有 AI 内容和归因结果都属于解释性信息，不能替代公告原文、持牌机构意见或个人投资决策。界面不会输出“建议买入”“目标价”或收益承诺。
