#import "KuiklyRenderViewController.h"
#import "UINavigationController+FDFullscreenPopGesture.h"
#import <OpenKuiklyIOSRender/KuiklyRenderViewControllerBaseDelegator.h>
#import <OpenKuiklyIOSRender/KuiklyRenderContextProtocol.h>

#define HRWeakSelf __weak typeof(self) weakSelf = self;
@interface KuiklyRenderViewController()<KuiklyRenderViewControllerBaseDelegatorDelegate>

@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;

@end

@implementation KuiklyRenderViewController {
    NSDictionary *_pageData;
}

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    if (self = [super init]) {
        pageData = [self p_mergeExtParamsWithOriditalParam:pageData];
        _pageData = pageData;
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName pageData:pageData];
        _delegator.delegate = self;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.fd_prefersNavigationBarHidden = YES;
    self.view.backgroundColor = [UIColor whiteColor];
    [_delegator viewDidLoadWithView:self.view];
    [self.navigationController setNavigationBarHidden:YES animated:NO];

}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [_delegator viewDidLayoutSubviews];

}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [_delegator viewWillAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_delegator viewDidAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

#pragma mark - private

- (NSDictionary *)p_mergeExtParamsWithOriditalParam:(NSDictionary *)pageParam {
    NSMutableDictionary *mParam = [(pageParam ?: @{}) mutableCopy];

    return mParam;
}

#pragma mark - KuiklyRenderViewControllerDelegatorDelegate

- (UIView *)createLoadingView {
    UIView *loadingView = [[UIView alloc] init];
    loadingView.backgroundColor = [UIColor whiteColor];
    return loadingView;
}

- (UIView *)createErrorView {
    UIView *errorView = [[UIView alloc] init];
    errorView.backgroundColor = [UIColor whiteColor];
    return errorView;
}

- (void)fetchContextCodeWithPageName:(NSString *)pageName resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        // 返回对应framework名字
        callback(@"stockchat", nil);
    }
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end

#pragma mark - Kuikly 引擎网络错误弹窗抑制

// 背景：KRHttpRequestTool.m:253-260 对任何非 2xx 响应调
// +[KRLogModule logError:]，而 KRLogModule.m:135-140 会无条件调
// KRConvertUtil.hr_alertWithTitle 弹 UIAlertController（无 #if DEBUG，
// Release 同样弹）。行情接口（腾讯 WAF）偶发 501 时会弹出遮挡式模态框，
// 而业务侧已有三级域名降级兜底（TencentQuoteProvider.KLINE_HOSTS），
// 弹窗纯属噪音且打断用户。
//
// 范围收敛：引擎内共 25 处调用 +[KRLogModule logError:]，本 hook 只抑制
// 含 "non-success status code" 的网络类消息（仍打 NSLog 便于排查）；其余
// 24 处（框架配置错误、断言失败、渲染异常、PAG 素材缺失等）走原 IMP 保持
// 弹窗，避免掩盖开发期需要立刻发现的真问题。
//
// 实现要点：
//   - NSClassFromString + NSSelectorFromString：不依赖 Pods 头是否 public
//   - 保存并转发原 IMP：非网络类消息行为与引擎原生完全一致
//   - dispatch_once：多线程安全，仅执行一次
//   - 找不到类/方法时 early return，绝不影响启动

#import <objc/runtime.h>

static void KR_SuppressNetworkErrorAlert(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Class logClass = NSClassFromString(@"KRLogModule");
        SEL logSelector = NSSelectorFromString(@"logError:");
        if (!logClass || !logSelector) {
            return;
        }
        Method logMethod = class_getClassMethod(logClass, logSelector);
        if (!logMethod) {
            return;
        }

        IMP originalImp = method_getImplementation(logMethod);
        IMP replacementImp = imp_implementationWithBlock(^(id _self, NSString *message) {
            if ([message isKindOfClass:[NSString class]] &&
                [message containsString:@"non-success status code"]) {
                NSLog(@"[KRNetworkAlertSuppressed] %@", message);
                return;
            }
            ((void (*)(id, SEL, NSString *))originalImp)(_self, logSelector, message);
        });
        method_setImplementation(logMethod, replacementImp);
    });
}

__attribute__((constructor(101)))
static void KR_SuppressNetworkErrorAlert_Init(void) {
    KR_SuppressNetworkErrorAlert();
}
