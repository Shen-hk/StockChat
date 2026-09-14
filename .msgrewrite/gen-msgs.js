const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, 'msgs');
fs.mkdirSync(outDir, { recursive: true });

const msgs = {
  'bfbcfda733cda541a7bdcb684987e75a695c6a27': `docs: 过程记录改为引用源文档`,
  '90f09aea3eaaa3dc8196d109a3a37c285244f062': `build: 发布前升级 Android targetSdk`,
  '4bb54b8cc89b771d96448561d9ea994edbab3cb0': `chore: 清理过时的调试产物`,
  'b770070fff12c3957aaea88e5046ed12487943ba': `fix(ohos): 稳定 AI 分发流程并同步项目文档`,
  'e6ed964df1859024ae5d0c7a9ca0c3386f1f3d14': `feat(chat): 打磨聊天体验细节并补充单测`,
  'fac42b1d81a67bd53bb6e8eae0a10d45af7a0f71': `refactor(chat): 抽取输入栏外框 DSL`,
  'f782701edf0e9a0356fa19174c9d4c878fb7194f': `refactor(chat): 抽取输入栏输入行 DSL`,
  'ada33d202118fd455411191a020bd2574bc5bb6b': `refactor(chat): 抽取输入栏操作行 DSL`,
  '26eb8389b02284f94cb9d62ee0c67c9884a4d906': `refactor(chat): 抽取行情降级提示 DSL`,
  'fa6df98a62fd0d884b1254353ad4a97cefed9761': `refactor(chat): 隔离组件数据查找逻辑`,
  '16f4f20cf112f5637901b711eed399acae6dd371': `refactor(chat): 抽取对比浮层 DSL`,
  '75ff18c0f26ebd90a291ba7899cde45ce6594997': `refactor(chat): 抽取上下文浮层 DSL`,
  '20a040b9e35aa4f7d790f659342f45701bade419': `refactor(chat): 抽取指令参数面板组件`,
  '389c90a50810aa55a83fa02b0c66a12ceb30b644': `refactor(chat): 抽取助手候选面板 DSL`,
  '10a361dcfca1cadcbeb03bc937f1033eaf4491e6': `refactor(chat): 抽取行情降级超时逻辑`,
  '787b59d03c36c7c006ff728d6e8f8925c80c8415': `refactor(chat): 抽取输入栏视觉时间线`,
  'c3a33b0951c617abd3f4d5ff0392a6a5e0bdb485': `refactor(chat): 迁移会话操作浮层`,
  '05073d4560b17b947c2f1da3d2a60e292919ecb6': `refactor(chat): 收拢输入栏远程搜索协调逻辑`,
  '751547f78e821684cc5a1f5ee9cc11f761b921ac': `refactor(chat): 集中输入栏助手上下文`,
  '9b9e49a658fb4e340b9f90084f1aa1de712d73f3': `refactor(chat): 抽取会话外框状态`,
  '3e9b260d4532a9723ac824d30b005d2a88f5c312': `refactor(chat): 抽取图片预览状态`,
  '7f53762e3a97a2d698f09651f002d7d3b4912cc4': `refactor(chat): 抽取回到顶部协调器`,
  'ca9385fde32ea225b2927d4def331cf3e0cc566f': `refactor(chat): 抽取追问展示协调器`,
  '80431b052aa96e04d040c8b2ecee51bc1845408a': `refactor(chat): 迁移助手异步状态归属`,
  'e9823202ed310161c1fc143af1d6996e5738a942': `refactor(chat): 集中输入栏助手状态`,
  '63ea557894e47be37491a95156631617cc1573e5': `docs(agents): 补记录 R8——ohos 构建改由 Bash 驱动，hdc install 需用不带路径的文件名

- PowerShell 工具会话会拦截原生 exe 调用，gradle/hvigor/hdc 改由 Bash 驱动，并导出 OHOS_SDK_HOME/DEVECO_SDK_HOME。
- hdc install 传入 Windows 绝对路径会被路径转换搞乱，需改传不带路径的裸文件名。`,
  '6abe82ba44af5681596921dc5ba7954790274155': `fix(data): 腾讯行情 K线/分时多主机降级 + 编译修复

web.ifzq.gtimg.cn 的 fqkline 接口被 WAF 拦截（返回 501 HTML）；改为按顺序尝试 ifzq.gtimg.cn 与 proxy.finance.qq.com，取第一个有效 JSON。snapshot/timeline/kLines 统一走共享的请求辅助函数。

修复尾随 lambda 绑定问题：将 hostIndex 移到 onResult 之前，使尾随 lambda 正确绑定到回调而不是 hostIndex（此前 Android 与 ohos 均编译失败，各 21 处错误）。`,
  '445a1a5c1fc9ced078c56e0a8bbac1e93b0046a6': `docs(chat): 记录模块化拆分进度`,
  'cce2da234e32a6943a72824859f3f1468c00e9bd': `refactor(chat): 抽取输入栏候选行 DSL`,
  'dcb2c8b9105a94c2d2430015f524255082295452': `refactor(chat): 抽取卡片交互协调器`,
  'd727fe30a73353964a74110450bf8cf92ee97b34': `refactor(chat): 抽取语音输入协调器`,
  '1947a7a77f424098e1327222dc88a16c136c89a8': `refactor(chat): 抽取输入栏焦点协调器`,
  '98e257add49a783611dfae570a18bbe36cdff1eb': `refactor(chat): 抽取消息操作协调器`,
  'b0e5cf6f32d71e219e240a1411299801e9320fdd': `refactor(chat): 抽取对比洞察协调器`,
  'abf05cade6c3b302908c4d20385072ae89602c6a': `refactor(chat): 迁移实体交互状态机`,
  '8057f7e24938665ca1dee03f2a0a091bb99d509f': `feat(market): 打磨资讯、自选与风险模块的交互细节`,
  'deb79c5f3bdf2578d5ecc9d8bd7ea20e603112ba': `feat(chat): 提升输入栏、欢迎区与流式输出的健壮性`,
  '6781c4381d65b2c70eb10056ae45ecfc0831a4a5': `feat(detail): 打磨图表查看交互并拆分展示层`,
  'e80a3d171e4fb03dd1a8b322fccb626766c592f2': `feat(data): 持久化行情数据源选择并加固行情缓存`,
  '7db9cefc28e385b579412d80c5044ac01b64d128': `feat(app): 详情页行情预取、全局搜索 v2 与属性修复加固

- detail: 分页级 QuotePrefetchStore（LRU16，60s 窗口），创建即预取并接入 ChartLoadingSkeleton
- search: 市场分 tab 浏览、全市场关键词搜索，并加代际校验防止过期远程结果覆盖
- ui: CardShell/ApiConfigPage/NewsTape 的条件属性改为无条件赋值，清理残留边框
- topbar: 自选星标直接切换；设置页返回键层级修复
- market: 叙事轴分层 canvas；新闻跑马灯胶囊修复
- data: 扩充证券目录种子行情；补充腾讯行情解析测试`,
  '220c2aabd4dfddd070412524b0121a3951e0336c': `feat(riskmap): 收敛为两层结构、聊天式选股思维导图与流式 AI 解读块

- SkyLayer 收敛为抱团/牵连两层（用户评审：颠簸/日程/热度没用），其视觉元素转为常驻装饰；删除时间刷组件与相关状态
- 拖星牵引放宽到两图层（长按武装后）；页侧 LINK 守卫移除
- 卡底新增 AI 详细解读：DeepSeek 流式通路 + 打字机平滑，事实就绪自动生成一次，未配置/失败回落端侧速览并如实标注
- 导图重做：ChatThinkingProfile 六环标准选股思路 × 用户聊天关键词归档（端侧零 LLM），缺失环节可点一键补课问句；ChatSessionStore 新增 peekAllMessages 无副作用读取
- ChatThinkingProfileTest 5 例 + StarLayoutTest 11 例全绿；Android 目标编译通过（JS 目标暂被并行会话 QuotePrefetchStore WIP 阻塞）`,
  '13eaea1ec6d3d06f290bb5658878a2b8dc099c80': `feat(riskmap): 长按武装后才允许拖星 + 导图视图模式

- 拖星牵引改为长按 500ms 确认后武装（对齐 doc 32 §2 原型），修复单击选中仍可拖动的误触
- 起拖时收掉 Context Bar（牵引意图优先于提问条）
- 主卡顶部新增星图/导图分段切换器（持久化 sky_view_v1）
- 新增决策链路导图视图：三段思维链 + 真实事实分支，点节点跳星图对应图层`,
  '024d3dfbf09e541dfb83bd7a56e9b2628a0c8061': `docs: 记录 R7 面板重渲染规则与输入栏 S5/P5 规格更新`,
  '9ce28520befe6d6f9cc28c787e3f626664354870': `feat(ohos): 接入网络传输层、依赖与 ohosArm64 运行脚本

新增 PlatformHttpClient/Motion/Navigation/QuoteClock 的 ohos 实现，为 NetworkKMM 接入 pbcurlwrapper，切换到 KBA-native 版协程/序列化依赖，申请 INTERNET 权限，锁定 targetSdkVersion，并补充 THIRD_PARTY_NOTICES 与 DevEco 运行脚本。`,
  '3f3d2f068f03b37e7927f513f62dbacd53cf32fe': `fix(appearance): 子页面通过 themeRebuildKey 重建主题子树

AppTopBar 与 Scroller 子树会把主题状态缓存为首帧快照（R1），导致设置页切换主题/字号返回后不生效。改为在设置、API 配置、卡片画廊、预警中心、全局搜索、热点、财报日历与自选页统一用 vbind(themeRebuildKey) 包裹；Scroller 在重建后保留滚动位置。`,
  '0de97ab295bc736e53e131a58d0595a79b7ba799': `feat(detail): 对齐新闻跑马灯 v2 并加固圈选/AI 降级

详情页改用带自身 33ms 节拍的 NewsMarquee/NewsSummaryBar。未配置 AI 时不再静默无反应，改为明确的错误态；圈选与整页 AI 均设 12s 首字超时；圈选意图一旦检测到明显横向位移，立即取消短按计时，避免分时 scrub 抢走圈选手势。`,
  '1539145deccc822e0dc10caa6dff5148889a03c7': `feat(market): 新闻跑马灯 v2 + 速览弹层与流式 AI 摘要

页面级 33ms 节拍驱动无边框跑马灯（点击暂停，reduceMotion 下静止）；点击后打开两段式速览弹层，与 AI 复盘卡同一线程节拍下流式展示摘要。NewsTape 把摘要拆到共享的 NewsSummaryBar；新增 NewsMarquee 组件。`,
  '9f685b30b773e3b19a897261e8a666a0075119ab': `fix(welcome): 页面消失时复位市场 tab 滑块

420ms 的自动复位计时器无法在 openPage 跳转到市场页后存活；cancelMarketTasks 让 marketTabSelected 卡在 true，导致滑块返回时停在半程。改为在页面消失时直接复位。`,
  '0f5c553210441b97e30659b6a1c89b65d2b91989': `feat(composer): @ 候选实时化、远程搜索接入与参数面板重建

Spec 10 S5/P5：东财 suggest 接口喂给会话级远程候选池（板块/指数/港美股，剔除基金），行情侧补齐候选涨跌幅，移除静态目录 mock 数据；「/」参数状态复用「@」的取消/清位管线。面板改用 vfor key 重建（修复 R7 中 vif 内容卡死不更新的问题）。ChatPage 同时接入附件快照与 ComposerGuideRow 引导 chip（按主导特征分组）。`,
  '30e79bae5e10628e0bce41495c9c0cbbab241afd': `feat(chat): 输入栏附件快照进用户气泡

MessageAttachment 接入 ChatMessage/SendPayload，仅用于气泡回显（本轮不做持久化）；用户气泡渲染图片缩略图与文件 chip。桥接方法 openComposerMediaSource 不再要求回调才能唤起选择器。`,
  'cb04b5cb12256b755386d3445e9a96409b741efe': `refactor(data): 将 SSE 流式处理迁入 PlatformHttpClient 抽象层

PlatformHttpClient 收敛为一个各平台自持的小接口（get/postStream）；AiProvider 改为消费该接口，不再直接调用 Ktor。ohos 的实现在 ohos 相关提交中落地。`,
  '203cc25fb1e63120ef81472488222c32dc36193d': `refactor(welcome): 迁移欢迎区组件位置`,
  'f42bc4059077587aacc42f51eb1bb11ed5899f40': `docs(architecture): 补充项目演进蓝图`,
  '9d9a65d7c248ef2414ce4f21177a5d0f7d75e4b7': `docs(chat): 更新重构交接状态`,
  'c9af8d8d3637fb2e1e15cc0141e3c1713f900f53': `test(data): 对齐模拟分时数据的测试预期`,
  '9c5b0a114c24cdcda0685850baef739e0d57dd8a': `refactor(chat): 隔离输入栏媒体状态`,
  '7abbada8089dcf1ebde0ba9c3d222a928d78fa3c': `refactor(chat): 隔离卡片面板协调器`,
  'f8746f2fa8e7765c51f5ce408d43065ccb58f14a': `refactor(chat): 隔离滚动跟随协调器`,
  '5ac725b140eb255cc0fa648fc0e31ab52cd6e96c': `refactor(chat): 抽取欢迎区协调器`,
  'a25e3c43c59bc3b13ef94531e4473480552ae71c': `chore: 初始提交`,
  '1d06ddafcb050a01c79cd9d8630825682faa3d7f': `fix: 会话标题改为整段摘要生成、ohos 网络库初始化脱敏，并补充架构边界测试`,
  '87075b3cde9e8d200c8777beadc54b5d538fe02f': `fix: 问AI跳转补充标的与返回栈收拢标记、分时/K线在线失败回落离线数据（架构重构前的收尾修复）`,
  'a9cb0330beb57779a8493023708ccfa8c47ebbd0': `fix(chat): 收窄底部实色垫层范围，仅遮住胶囊下方空白`,
  '983f1101911841d053090b0714040d98b6879644': `feat(chat): 输入栏与顶栏底部加实色垫层，消除虚化边缘的内容穿透`,
  '2d0581e596983a36bd71f0f6ccd6db3b9f0d9556': `chore: 补充 .gitignore 忽略规则`,
};

for (const [hash, msg] of Object.entries(msgs)) {
  fs.writeFileSync(path.join(outDir, hash + '.txt'), msg + '\n', 'utf8');
}
console.log('wrote', Object.keys(msgs).length, 'message files to', outDir);
