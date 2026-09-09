#import "KRRouterHandler.h"
#import "KuiklyRenderViewController.h"

@implementation KRRouterHandler

+ (void)load {
    [KRRouterModule registerRouterHandler:[self new]];
}

- (void)openPageWithName:(NSString *)pageName pageData:(NSDictionary *)pageData controller:(UIViewController *)controller {
    KuiklyRenderViewController *renderViewController = [[KuiklyRenderViewController alloc] initWithPageName:pageName pageData:pageData];
    // 容器变换交接（灵动岛下拉 → 详情）：发送页在形变 ~90% 处就触发路由，
    // 这里无动画 push + 整页 alpha 淡入，让详情页与卡片形变收尾重叠渐显；
    // 淡入期间发送页保持全屏玻璃帧作底。常规入口保持系统右侧推入。
    BOOL islandExpand = [pageData[@"krTransition"] isEqualToString:@"islandExpand"];
    UINavigationController *nav = controller.navigationController;
    BOOL askAiChat = [pageName isEqualToString:@"ChatPage"] &&
                     [pageData[@"openedViaAskAi"] isEqualToString:@"1"];
    if (islandExpand) {
        renderViewController.view.alpha = 0.0;
        [nav pushViewController:renderViewController animated:NO];
        [UIView animateWithDuration:0.24
                              delay:0.0
                            options:UIViewAnimationOptionCurveEaseOut | UIViewAnimationOptionAllowUserInteraction
                         animations:^{ renderViewController.view.alpha = 1.0; }
                         completion:nil];
    } else {
        [nav pushViewController:renderViewController animated:YES];
    }
    if (askAiChat) {
        [self p_collapseStackAfterAskAiPush:nav];
    }
}

/**
 * 「问AI」打开的对话页不参与无限叠层：push 动画收尾后收掉导航栈里根控制器
 * 之外的所有旧页，栈深封顶为「根对话页 + 当前对话页」。否则「详情 ⇄ 问AI
 * 到对话」反复横跳会把历史页一层层压栈，返回要逐页退完才能回到最初。
 * 根对话页实例保留，新对话页返回（侧滑）即回到它。
 */
- (void)p_collapseStackAfterAskAiPush:(UINavigationController *)nav {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.4 * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
        NSMutableArray *vcs = [NSMutableArray arrayWithArray:nav.viewControllers];
        if (vcs.count > 2) {
            [vcs removeObjectsInRange:NSMakeRange(1, vcs.count - 2)];
            [nav setViewControllers:vcs animated:NO];
        }
    });
}

- (void)closePage:(UIViewController *)controller {
    [controller.navigationController popViewControllerAnimated:YES];
}

@end