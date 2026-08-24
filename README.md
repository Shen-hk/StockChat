# 股问 StockChat

股问是一个基于 Kuikly / Kotlin Multiplatform 的中文股票解释助手。产品以对话为主入口，将自然语言、行情卡片、归因卡片、图表和追问建议混排展示；只提供信息解释，不提供买卖或仓位建议。

## 已实现

- Android 与 H5 共用业务、协议、数据和 UI 代码
- Markdown + `card:type` 流式协议，支持半包骨架和未知卡片降级
- 行情、分时、归因、洞察、术语、对比、资讯和追问卡片
- 股票/术语实体识别，以及股票 Context Bar 预览
- 股票详情页、指标区、图表、归因和资讯区
- 腾讯行情接口适配，失败时自动回退到内存缓存或确定性的离线演示数据
- DeepSeek OpenAI 兼容接口适配；未配置密钥时使用可流式演示的本地 AI Provider
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

腾讯行情不需要密钥。DeepSeek 密钥通过打开 `ChatPage` 时的页面参数 `deepSeekApiKey` 注入，工程不会读取或提交硬编码密钥。密钥为空或在线请求失败时，AI 会自动回退到离线 Provider，行情请求失败时也会显示明确的离线数据标识。

## 产品边界

所有 AI 内容和归因结果都属于解释性信息，不能替代公告原文、持牌机构意见或个人投资决策。界面不会输出“建议买入”“目标价”或收益承诺。
