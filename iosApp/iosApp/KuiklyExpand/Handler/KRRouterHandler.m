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
    if (islandExpand) {
        renderViewController.view.alpha = 0.0;
        [controller.navigationController pushViewController:renderViewController animated:NO];
        [UIView animateWithDuration:0.24
                              delay:0.0
                            options:UIViewAnimationOptionCurveEaseOut | UIViewAnimationOptionAllowUserInteraction
                         animations:^{ renderViewController.view.alpha = 1.0; }
                         completion:nil];
    } else {
        [controller.navigationController pushViewController:renderViewController animated:YES];
    }
}

- (void)closePage:(UIViewController *)controller {
    [controller.navigationController popViewControllerAnimated:YES];
}

@end