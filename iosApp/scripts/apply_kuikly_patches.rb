# frozen_string_literal: true
#
# StockChat 对 Pods/OpenKuiklyIOSRender 的 iOS 渲染层补丁（幂等、可重复执行）。
#
# 背景：Kuikly iOS 渲染层要求所有 Kotlin→Native 调用都发生在 context queue 上，
# 否则 `assertContextQueue` 直接 SIGABRT（Android 无此断言，所以只在 iOS 暴露）。
# StockChat 的 AI 流式回调来自 Dispatchers.Default，历史上造成两类问题：
#   1) 发送首条消息 / 首屏若干页面直接 abort；
#   2) 布局测量到「原生从未创建过 shadow」的 tag（shadow can't be nil）。
#
# Kotlin 侧已按线程铁律把 observable 写入跳回核心线程（ChatViewModel.onCoreThread），
# 本脚本提供渲染层兜底：即使还有零星跨线程调用也不会崩，只会退化为一次日志。
#
# 用法：Podfile 的 post_install 自动调用；也可手动执行
#   ruby iosApp/scripts/apply_kuikly_patches.rb iosApp
# `pod install` 会还原 Pods 源码，本脚本负责在其后重新套用。
module KuiklyPatches
  CORE_FILE = 'Pods/OpenKuiklyIOSRender/core-render-ios/Core/KuiklyRenderCore.m'
  LAYER_FILE = 'Pods/OpenKuiklyIOSRender/core-render-ios/Handler/KuiklyRenderLayerHandler.mm'
  CANVAS_FILE = 'Pods/OpenKuiklyIOSRender/core-render-ios/Extension/AdvancedComps/KRCanvasView.m'

  # ---- 补丁 1：跨线程 toNative 改为异步 marshal 到 context queue ----
  CORE_ANCHOR = <<-'OBJC'
    [_contextHandler registerCallNativeWtihCallback:^id _Nullable(KuiklyRenderNativeMethod method, NSArray *_Nonnull args) {
        KR_STRONG_SELF_RETURN_NIL
        [KuiklyRenderThreadManager assertContextQueue]; // 线程断言，保证仅在Context线程回调
  OBJC

  CORE_PATCHED = <<-'OBJC'
    [_contextHandler registerCallNativeWtihCallback:^id _Nullable(KuiklyRenderNativeMethod method, NSArray *_Nonnull args) {
        KR_STRONG_SELF_RETURN_NIL
        // [StockChatPatch] crossThreadToNative：Kotlin 侧后台线程（协程 / AI 流式回调）
        // 直接 toNative 时原实现的 assert 会 abort。此类调用均为「单向通知」，改为异步
        // marshal 到 Context 队列执行，返回 nil 与原语义等价；核心线程上的常规调用
        // （含 syncCallNativeMethod）走原路径。
        if (![KuiklyRenderThreadManager isContextQueue]) {
            [KuiklyRenderThreadManager performOnContextQueueWithBlock:^{
                [strongSelf p_performNativeMethodWithMethod:method args:args];
            }];
            return nil;
        }
        // 执行KuiklyKotlin侧调用Native侧的事件
  OBJC

  # ---- 补丁 2：测量缺失 shadow 时返回零尺寸，不 abort ----
  # 注意：以下 heredoc 内容顶格书写（<<- 不做缩进剥离，必须与 Pods 源码逐字节一致）。
  LAYER_MEASURE_ANCHOR = <<-'OBJC'
- (CGSize)calculateRenderViewSizeWithTag:(NSNumber *)tag constraintSize:(CGSize)constraintSize {
    id<KuiklyRenderShadowProtocol> shadow = [self p_shadowHandlerWithTag:tag];
    return [shadow hrv_calculateRenderViewSizeWithConstraintSize:constraintSize];
}
  OBJC

  LAYER_MEASURE_PATCHED = <<-'OBJC'
- (CGSize)calculateRenderViewSizeWithTag:(NSNumber *)tag constraintSize:(CGSize)constraintSize {
    id<KuiklyRenderShadowProtocol> shadow = [self p_shadowHandlerWithTag:tag];
    // [StockChatPatch] missingShadowMeasure：shadow 缺失（视图已销毁 / 尚未创建）时
    // 返回零尺寸让布局继续；原实现直接对 nil 发消息，Debug 下由 NSAssert 中断进程。
    if (!shadow) { return CGSizeZero; }
    return [shadow hrv_calculateRenderViewSizeWithConstraintSize:constraintSize];
}
  OBJC

  # ---- 补丁 3：取 shadow 缺失只记日志，不 abort ----
  LAYER_LOOKUP_ANCHOR = <<-'OBJC'
- (id<KuiklyRenderShadowProtocol>)p_shadowHandlerWithTag:(NSNumber*)tag {
    id<KuiklyRenderShadowProtocol> shadow = _shadowRegistry[tag];
    NSAssert(shadow, @"shadow can't be nil");
    return shadow;
}
  OBJC

  LAYER_LOOKUP_PATCHED = <<-'OBJC'
- (id<KuiklyRenderShadowProtocol>)p_shadowHandlerWithTag:(NSNumber*)tag {
    id<KuiklyRenderShadowProtocol> shadow = _shadowRegistry[tag];
    // [StockChatPatch] missingShadowLookup：跨线程 / 生命周期竞态下可能出现「布局
    // 测量了未创建或已销毁的 shadow tag」。原实现 NSAssert 直接中断进程，这里退化为
    // nil（调用方已按 nil 处理），并留下日志便于定位。
    if (!shadow) {
        NSLog(@"[KRShadow][StockChatPatch] missing shadow tag=%@", tag);
    }
    return shadow;
}
  OBJC

  # ---- 补丁 4：Canvas batchDraw（批量绘制命令重放）----
  # 核心层 CanvasContext.batchDraw = true 时，一帧内的绘制命令会被打包成
  # [{m,p},...] 用一个 batchDraw 命令下发；iOS 渲染层 2.7.0 未实现该方法，
  # 命令被 hrv_callWithMethod 静默丢弃。Android 侧 KRCanvasView 已实现。
  CANVAS_BATCH_ANCHOR = <<-'OBJC'
- (void)css_beginPath:(NSDictionary *)args {
  OBJC

  CANVAS_BATCH_PATCHED = <<-'OBJC'
// [StockChatPatch] batchDraw：核心层「批量绘制」通道的重放实现。
// 触发条件：CanvasContext.batchDraw = true（本仓库为输入栏线条图标 LineIcons、
// 语音波形 VoiceBar、输入栏流光描边 renderComposerGradientRim 三处）。
// 上游 iOS 渲染层 2.7.0 没有 css_batchDraw，命令被静默丢弃 → 这些 Canvas 在 iOS
// 上整体空白（图表类 Canvas 未开批量模式、逐条下发，所以一直正常）。
// 这里按「与逐条下发完全相同」的路径重放：同一 hrv_callWithMethod → css_<m>，
// 画令顺序与参数原样保留，只多一次 JSON 解析，语义与 Android 渲染层一致。
- (void)css_batchDraw:(NSDictionary *)args {
    NSString *payload = args[KRC_PARAM_KEY];
    if (![payload isKindOfClass:[NSString class]] || payload.length == 0) {
        return;
    }
    NSArray *commands = [payload hr_stringToArray];
    if (![commands isKindOfClass:[NSArray class]]) {
        NSLog(@"[KRCanvas][StockChatPatch] batchDraw 负载解析失败，本帧跳过");
        return;
    }
    for (id entry in commands) {
        if (![entry isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSString *method = ((NSDictionary *)entry)[@"m"];
        if (![method isKindOfClass:[NSString class]] || method.length == 0) {
            continue;
        }
        // 无参命令（beginPath/closePath/stroke/fill/save/restore）不带 "p"，
        // 传 nil 与逐条下发的行为一致（宏内 if (params) 会跳过参数写入）。
        [self hrv_callWithMethod:method params:((NSDictionary *)entry)[@"p"] callback:nil];
    }
}

- (void)css_beginPath:(NSDictionary *)args {
  OBJC

  # ---- 补丁 5：clipPathDifference（差集裁剪）语义对齐 Android ----
  # Android: Canvas.clipPath(path, Region.Op.DIFFERENCE) → 裁剪区 = 当前裁剪 − path。
  # iOS 原实现一律走 CGContextEOClip(path)，等于「与 path 的交集」，语义不同。
  # 后果：输入栏流光描边（两个同心圆角矩形求差得到 1dp 环）在 iOS 上退化成整片填充。
  # 本补丁：交集裁剪时记住被裁剪的路径并压一层 gstate；差集裁剪时先弹回交集前的
  # 裁剪状态，再用 even-odd 对「交集路径 ∪ 当前路径」求环——同向子路径的 EO 相交
  # 即等于两者之差，与 Android 的 DIFFERENCE 等价。
  CANVAS_PROP_ANCHOR = <<-'OBJC'
@property (nonatomic, assign) CGFloat lineWidth;
  OBJC

  CANVAS_PROP_PATCHED = <<-'OBJC'
@property (nonatomic, assign) CGFloat lineWidth;

// [StockChatPatch] clipDifference：最近一次「交集裁剪」使用的路径。
// 差集裁剪需要它作为被减数（当前裁剪 = 该路径），与当前路径求环。
@property (nonatomic, assign) CGPathRef scClipBase;
  OBJC

  CANVAS_RESET_ANCHOR = <<-'OBJC'
- (void)css_reset:(NSDictionary *)args {
    self.renderActions = nil;
    if (_path) {
        CGPathRelease(_path);
    }
    _path = CGPathCreateMutable();
    [self.saveStack removeAllObjects];
  OBJC

  CANVAS_RESET_PATCHED = <<-'OBJC'
- (void)css_reset:(NSDictionary *)args {
    self.renderActions = nil;
    if (_path) {
        CGPathRelease(_path);
    }
    _path = CGPathCreateMutable();
    // [StockChatPatch] clipDifference：逐帧清理差集裁剪的基准路径。
    if (self.scClipBase) {
        CGPathRelease(self.scClipBase);
        self.scClipBase = NULL;
    }
    [self.saveStack removeAllObjects];
  OBJC

  CANVAS_CLIP_ANCHOR = <<-'OBJC'
- (void)css_clip:(NSDictionary *)args {
    NSDictionary *params = [args[KRC_PARAM_KEY] hr_stringToDictionary];
    BOOL intersect = [params[@"intersect"] boolValue];
    [self addRenderAction:^(CGContextRef context, CGMutablePathRef path) {
        CGContextAddPath(context, path);
        if (intersect) {
            CGContextClip(context);
        } else {
            CGContextEOClip(context);
        }
    }];
}
  OBJC

  CANVAS_CLIP_PATCHED = <<-'OBJC'
- (void)css_clip:(NSDictionary *)args {
    NSDictionary *params = [args[KRC_PARAM_KEY] hr_stringToDictionary];
    BOOL intersect = [params[@"intersect"] boolValue];
    KR_WEAK_SELF
    [self addRenderAction:^(CGContextRef context, CGMutablePathRef path) {
        KR_STRONG_SELF_RETURN_IF_NIL
        if (intersect) {
            // [StockChatPatch] clipDifference：交集裁剪。多压一层 gstate 记录「裁剪前」
            // 的状态，供随后的差集裁剪弹回；标记进 saveStack 以便 restore 正确配对。
            CGContextSaveGState(context);
            [strongSelf.saveStack addObject:@"clipdiff"];
            CGContextAddPath(context, path);
            CGContextClip(context);
            if (strongSelf.scClipBase) {
                CGPathRelease(strongSelf.scClipBase);
            }
            strongSelf.scClipBase = CGPathCreateCopy(path);
        } else {
            // [StockChatPatch] clipDifference：差集裁剪。语义 = 当前裁剪 − path。
            if ([strongSelf.saveStack.lastObject isEqualToString:@"clipdiff"]) {
                [strongSelf.saveStack removeLastObject];
                CGContextRestoreGState(context);
            }
            if (strongSelf.scClipBase) {
                CGContextAddPath(context, strongSelf.scClipBase);
            }
            CGContextAddPath(context, path);
            CGContextEOClip(context);
        }
    }];
}
  OBJC

  CANVAS_RESTORE_ANCHOR = <<-'OBJC'
- (void)css_restore:(NSDictionary *)args {
    __weak typeof(self) weakSelf = self;
    [self addRenderAction:^(CGContextRef context, CGMutablePathRef path) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) return;
        NSString *type = strongSelf.saveStack.lastObject;
  OBJC

  CANVAS_RESTORE_PATCHED = <<-'OBJC'
- (void)css_restore:(NSDictionary *)args {
    __weak typeof(self) weakSelf = self;
    [self addRenderAction:^(CGContextRef context, CGMutablePathRef path) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) return;
        // [StockChatPatch] clipDifference：先消费交集裁剪压入的中间 gstate，
        // Kotlin 侧的 save/restore 配对不受影响（该标记不占调用方的一次配对）。
        while ([strongSelf.saveStack.lastObject isEqualToString:@"clipdiff"]) {
            [strongSelf.saveStack removeLastObject];
            CGContextRestoreGState(context);
        }
        NSString *type = strongSelf.saveStack.lastObject;
  OBJC

  CANVAS_DEALLOC_ANCHOR = <<-'OBJC'
- (void)dealloc {

    if (_path) {
        CGPathRelease(_path);
    }
}
  OBJC

  CANVAS_DEALLOC_PATCHED = <<-'OBJC'
- (void)dealloc {

    if (_path) {
        CGPathRelease(_path);
    }
    // [StockChatPatch] clipDifference
    if (_scClipBase) {
        CGPathRelease(_scClipBase);
    }
}
  OBJC

  PATCHES = [
    # applied：已经套用过的变体特征串（历史上有诊断加强版，注释措辞与下面 replacement 不同）。
    # 命中任一即视为「已套用」直接跳过，避免误报「未匹配锚点」并把脚本判为失败。
    { name: 'crossThreadToNative', file: CORE_FILE,  anchor: CORE_ANCHOR,          replacement: CORE_PATCHED,
      applied: ['performOnContextQueueWithBlock'] },
    { name: 'missingShadowMeasure', file: LAYER_FILE, anchor: LAYER_MEASURE_ANCHOR, replacement: LAYER_MEASURE_PATCHED,
      applied: ['MISS-MEASURE'] },
    { name: 'missingShadowLookup', file: LAYER_FILE, anchor: LAYER_LOOKUP_ANCHOR,  replacement: LAYER_LOOKUP_PATCHED,
      applied: ['missing shadow tag='] },
    # upstream：上游自己实现了同名方法时跳过，避免重复定义导致编译错误。
    { name: 'batchDraw', file: CANVAS_FILE, anchor: CANVAS_BATCH_ANCHOR, replacement: CANVAS_BATCH_PATCHED,
      upstream: '- (void)css_batchDraw:' },
    { name: 'clipDifferenceProp', file: CANVAS_FILE, anchor: CANVAS_PROP_ANCHOR, replacement: CANVAS_PROP_PATCHED,
      applied: ['CGPathRef scClipBase;'] },
    { name: 'clipDifferenceReset', file: CANVAS_FILE, anchor: CANVAS_RESET_ANCHOR, replacement: CANVAS_RESET_PATCHED,
      applied: ['clipDifference：逐帧清理'] },
    { name: 'clipDifferenceClip', file: CANVAS_FILE, anchor: CANVAS_CLIP_ANCHOR, replacement: CANVAS_CLIP_PATCHED,
      applied: ['// [StockChatPatch] clipDifference：交集裁剪'] },
    { name: 'clipDifferenceRestore', file: CANVAS_FILE, anchor: CANVAS_RESTORE_ANCHOR, replacement: CANVAS_RESTORE_PATCHED,
      applied: ['// [StockChatPatch] clipDifference：先消费交集裁剪'] },
    { name: 'clipDifferenceDealloc', file: CANVAS_FILE, anchor: CANVAS_DEALLOC_ANCHOR, replacement: CANVAS_DEALLOC_PATCHED,
      applied: ['_scClipBase'] }
  ].freeze

  class << self
    # @param ios_app_dir [String] iosApp 目录（内含 Pods/）
    # @return [Boolean] 是否全部已套用（已套用视为成功）
    def apply!(ios_app_dir)
      core = File.join(ios_app_dir, CORE_FILE)
      unless File.exist?(core)
        warn '[kuikly-patches] 跳过：未找到 Pods 源码（请先 pod install）'
        return false
      end

      results = PATCHES.map { |spec| patch(File.join(ios_app_dir, spec[:file]), spec) }
      if results.all?
        puts '[kuikly-patches] 渲染层补丁就绪（跨线程兜底 + 缺失 shadow 容错 + Canvas batchDraw + clipPathDifference）'
      else
        warn '[kuikly-patches] 部分补丁未套用，请核对 OpenKuiklyIOSRender 版本！'
      end
      results.all?
    end

    private

    def patch(path, spec)
      marker = "[StockChatPatch] #{spec[:name]}"
      src = File.read(path, encoding: 'UTF-8')
      return true if src.include?(marker)

      # 历史变体识别：同一补丁可能已被套用，但注释措辞不同（例如诊断加强版）。
      # 命中特征串即视为已套用——此时锚点必然也已不匹配，若不提前返回会被误判失败。
      applied = Array(spec[:applied]).find { |probe| src.include?(probe) }
      if applied
        puts "[kuikly-patches] #{spec[:name]}: 已套用（变体特征 #{applied}），跳过"
        return true
      end

      # 上游已自带该能力（例如新版本渲染层实现了 css_batchDraw）时跳过，
      # 否则会在同文件里插入第二份同名方法，直接编译失败。
      if spec[:upstream] && src.include?(spec[:upstream])
        puts "[kuikly-patches] #{spec[:name]}: 上游已实现，跳过本补丁"
        return true
      end

      unless src.include?(spec[:anchor])
        warn "[kuikly-patches] #{spec[:name]}: 未匹配锚点（上游源码已变化），本补丁未套用"
        return false
      end

      # Pods 源码里部分文件是只读的（CocoaPods 交付属性），先补写权限。
      File.chmod(File.stat(path).mode | 0o200, path) unless File.writable?(path)
      File.write(path, src.sub(spec[:anchor], spec[:replacement]))
      puts "[kuikly-patches] #{spec[:name]}: 已套用"
      true
    end
  end
end

KuiklyPatches.apply!(ARGV[0]) if $PROGRAM_NAME == __FILE__ && ARGV[0]
