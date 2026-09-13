# StockChat 源码参考

本目录是 GitHub 上保留的正式开发参考，内容以当前源码为准，不保存需求讨论、阶段汇报、施工任务书、调试记录或交互原型。

- [页面与功能](pages.md)：全部 Kuikly 页面、路由名、入口参数、主要功能和关键依赖。
- [通用组件 DSL](components-dsl.md)：组件分层、调用约定、响应式规则、通用 DSL 与业务组合组件索引。
- [架构门禁](../architecture/README.md)：Page → Component → State → Data 的依赖边界及自动检查。

## 维护约定

新增或删除 `@Page` 页面时同步更新 `pages.md`；新增可复用的 `ViewContainer`、`Attr`、`TextAttr`、`InputAttr` 或 `TextAreaAttr` 扩展时同步更新 `components-dsl.md`。过程性材料应放在个人工作区或外部协作系统，不放入仓库。

文档核对入口：

```bash
rg -n '@Page|object Routes' shared/src/commonMain/kotlin
rg -n 'fun (ViewContainer<\*, \*>|Attr|TextAttr|InputAttr|TextAreaAttr)\.' shared/src/commonMain/kotlin
```
