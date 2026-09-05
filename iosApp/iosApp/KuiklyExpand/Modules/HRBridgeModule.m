#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#include <stdbool.h>

// Kuikly 2.25.0 exposes this OHOS-only symbol from its iOS framework.
// The iOS renderer already serializes these callbacks on its UI context.
bool com_tencent_kuikly_IsCurrentOnContextThread(const char *pagerId) {
    return true;
}

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@implementation HRBridgeModule

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)shareInterpretation:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"] ?: @"";
    if (content.length == 0) { return; }
    CGFloat width = 1080.0;
    CGFloat side = 84.0;
    NSDictionary *bodyAttributes = @{
        NSFontAttributeName: [UIFont systemFontOfSize:34.0 weight:UIFontWeightRegular],
        NSForegroundColorAttributeName: [UIColor colorWithRed:0.15 green:0.17 blue:0.22 alpha:1.0],
    };
    CGRect bodyBounds = [content boundingRectWithSize:CGSizeMake(width - side * 2, CGFLOAT_MAX)
                                               options:NSStringDrawingUsesLineFragmentOrigin | NSStringDrawingUsesFontLeading
                                            attributes:bodyAttributes
                                               context:nil];
    CGFloat height = MIN(MAX(720.0, ceil(bodyBounds.size.height) + 330.0), 20000.0);
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), YES, 1.0);
    [[UIColor colorWithRed:0.96 green:0.97 blue:0.98 alpha:1.0] setFill];
    UIRectFill(CGRectMake(0, 0, width, height));
    [[UIColor whiteColor] setFill];
    UIBezierPath *card = [UIBezierPath bezierPathWithRoundedRect:CGRectMake(42, 42, width - 84, height - 84) cornerRadius:36];
    [card fill];
    [@"股问 StockChat" drawAtPoint:CGPointMake(side, 90)
                      withAttributes:@{NSFontAttributeName: [UIFont systemFontOfSize:48 weight:UIFontWeightBold],
                                       NSForegroundColorAttributeName: [UIColor colorWithRed:0.10 green:0.36 blue:0.86 alpha:1.0]}];
    [content drawWithRect:CGRectMake(side, 185, width - side * 2, height - 310)
                 options:NSStringDrawingUsesLineFragmentOrigin | NSStringDrawingUsesFontLeading
              attributes:bodyAttributes
                 context:nil];
    [@"信息解释，不构成投资建议 · 数据以原始信源为准" drawAtPoint:CGPointMake(side, height - 105)
                                             withAttributes:@{NSFontAttributeName: [UIFont systemFontOfSize:24],
                                                              NSForegroundColorAttributeName: [UIColor colorWithWhite:0.45 alpha:1.0]}];
    UIImage *shareImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    UIActivityViewController *controller = [[UIActivityViewController alloc] initWithActivityItems:@[shareImage ?: content] applicationActivities:nil];
    UIViewController *presenter = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (presenter.presentedViewController) { presenter = presenter.presentedViewController; }
    if (controller.popoverPresentationController) {
        controller.popoverPresentationController.sourceView = presenter.view;
        controller.popoverPresentationController.sourceRect = CGRectMake(CGRectGetMidX(presenter.view.bounds), CGRectGetMidY(presenter.view.bounds), 1, 1);
    }
    [presenter presentViewController:controller animated:YES completion:nil];
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

- (void)hapticImpact:(NSDictionary *)args {
    UIImpactFeedbackGenerator *generator = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
    [generator prepare];
    [generator impactOccurred];
}

- (void)startVoiceRecording:(NSDictionary *)args {
    // iOS native recording is intentionally not wired until the Kuikly callback
    // contract is added here; commonMain converts the missing callback into
    // VoiceError.UNAVAILABLE after a short timeout.
}

- (NSString *)stopVoiceRecording:(NSDictionary *)args {
    return @"{\"path\":\"\"}";
}

- (void)cancelVoiceRecording:(NSDictionary *)args {
}

- (NSString *)getGlassMode:(NSDictionary *)args {
    if (UIAccessibilityIsReduceTransparencyEnabled() || UIAccessibilityIsReduceMotionEnabled()) {
        return @"simplified";
    }
    return @"realtime";
}

@end
