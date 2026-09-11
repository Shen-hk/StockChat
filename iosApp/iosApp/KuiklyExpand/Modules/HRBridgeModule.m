#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import <OpenKuiklyIOSRender/KuiklyRenderThreadManager.h>
#import <PhotosUI/PhotosUI.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import <AVFoundation/AVFoundation.h>
#import <Speech/Speech.h>
#include <stdbool.h>

@class IOSVoiceSession;

// 单次语音识别会话。Speech/AVFoundation 回调都在后台线程，所有 Kuikly 回调
// 经 HRFireCallback 派发回 context queue。生命周期对齐 Android 的
// AndroidSpeechRecognitionSession：ready → amplitude/partial… → transcript|error。
@interface IOSVoiceSession : NSObject
@property (nonatomic, copy) KuiklyRenderCallback callback;
@property (nonatomic, copy) void (^onFinish)(IOSVoiceSession *);
@property (nonatomic, strong, nullable) SFSpeechRecognizer *recognizer;
@property (nonatomic, strong, nullable) SFSpeechAudioBufferRecognitionRequest *request;
@property (nonatomic, strong, nullable) AVAudioEngine *engine;
@property (nonatomic, strong) dispatch_queue_t workQueue;
@property (nonatomic, assign) BOOL delivered;   // transcript/error 已回传，之后静默
@property (nonatomic, assign) BOOL stopping;    // 松手后等待最终结果
@property (nonatomic, copy, nullable) NSString *latestPartial;
- (instancetype)initWithCallback:(KuiklyRenderCallback)callback
                        onFinish:(void (^)(IOSVoiceSession *session))onFinish;
- (void)start;
- (void)stop;
- (void)cancel;
@end

static IOSVoiceSession *_activeVoiceSession = nil;

static NSRecursiveLock *HRVoiceLock(void) {
    static dispatch_once_t onceToken;
    static NSRecursiveLock *lock = nil;
    dispatch_once(&onceToken, ^{
        lock = [[NSRecursiveLock alloc] init];
    });
    return lock;
}

/// Kuikly 回调统一派发到 context queue（铁律 A：Speech/AVFoundation 的回调
/// 都在后台线程，直调 Kuikly callback 会 assertContextQueue SIGABRT）。
static void HRFireCallback(KuiklyRenderCallback callback, NSDictionary *payload) {
    if (!callback || !payload) { return; }
    if ([KuiklyRenderThreadManager isContextQueue]) {
        callback(payload);
    } else {
        [KuiklyRenderThreadManager performOnContextQueueWithBlock:^{
            callback(payload);
        }];
    }
}

static NSString *HRComposerMediaDir(void) {
    NSString *dir = [NSTemporaryDirectory() stringByAppendingPathComponent:@"composer_media"];
    [[NSFileManager defaultManager] createDirectoryAtPath:dir
                              withIntermediateDirectories:YES
                                               attributes:nil error:nil];
    return dir;
}

static NSString *HRUniqueMediaPath(NSString *prefix, NSString *extension) {
    NSString *ext = extension.length > 0 ? extension : @"dat";
    NSString *name = [NSString stringWithFormat:@"%@_%lld_%@.%@",
                      prefix, (long long)([[NSDate date] timeIntervalSince1970] * 1000.0),
                      [[NSUUID UUID] UUIDString], ext];
    return [HRComposerMediaDir() stringByAppendingPathComponent:name];
}

static BOOL HRIsImageExtension(NSString *name) {
    static dispatch_once_t onceToken;
    static NSSet<NSString *> *imageExtensions = nil;
    dispatch_once(&onceToken, ^{
        imageExtensions = [NSSet setWithArray:@[@"jpg", @"jpeg", @"png", @"webp", @"gif", @"heic", @"heif", @"bmp", @"tiff"]];
    });
    NSString *ext = name.pathExtension.lowercaseString;
    return ext.length > 0 && [imageExtensions containsObject:ext];
}

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

@interface HRBridgeModule () <PHPickerViewControllerDelegate, UINavigationControllerDelegate, UIImagePickerControllerDelegate, UIDocumentPickerDelegate>
/// 页面注册的媒体选择结果回调（keepCallback，多次触发）。
@property (nonatomic, copy, nullable) KuiklyRenderCallback composerMediaCallback;
@end

@implementation HRBridgeModule

@synthesize hr_rootView;

// 兜底：共享层有大量 Android 宿主专有桥方法（抽屉手势、媒体选择、状态栏、
// 埋点等），iOS 宿主未逐一实现。父类 KRBaseModule 对未实现方法走
// NSAssert(false)——Debug 构建下首屏即 SIGABRT（ChatPage created 注册
// registerDrawerFlingHost 时触发）。这里改成与 Android else 分支同语义：
// 记日志 + 回错误码，页侧静默忽略；已实现的方法照常走父类派发。
- (id _Nullable)hrv_callWithMethod:(NSString *)method params:(id _Nullable)params callback:(KuiklyRenderCallback)callback {
    SEL selector = NSSelectorFromString([NSString stringWithFormat:@"%@:", method]);
    if ([self respondsToSelector:selector]) {
        return [super hrv_callWithMethod:method params:params callback:callback];
    }
    NSLog(@"[HRBridgeModule] 方法未实现（已忽略）: %@", method);
    if (callback) {
        callback(@{ @"code": @(-1), @"message": @"method does not exist" });
    }
    return nil;
}

// 抽屉手势宿主注册：iOS 暂不接入「大且快右滑」通道。必须显式空实现，
// 避免落入兜底分支立即回错误码、把 keepCallback 当结果触发一次抽屉。
- (void)registerDrawerFlingHost:(NSDictionary *)args {
}

// 输入栏媒体结果注册：宿主持有 callback（keepCallback），选择器产出后经
// {type:"ok", source, items:[{kind, path, name}...]} 回传，取消回 {type:"cancel"}。
// 与 Android 的 composerMediaResultHost 语义一致。
- (void)registerComposerMediaResult:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (callback) {
        self.composerMediaCallback = callback;
    }
}

// 输入栏媒体入口：library（多选图片）/ camera（拍照）/ document（单份文档）。
// 与 Android 对齐：callback 只用于错误上报，缺失不放弃拉起选择器。
- (void)openComposerMediaSource:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *source = [NSString stringWithFormat:@"%@", params[@"source"] ?: @"library"];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        [self p_openComposerSource:source callback:callback];
    });
}

- (void)p_openComposerSource:(NSString *)source callback:(KuiklyRenderCallback)callback {
    UIViewController *presenter = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (presenter.presentedViewController) { presenter = presenter.presentedViewController; }
    if (!presenter) {
        HRFireCallback(callback, @{@"type": @"cancel", @"source": source});
        return;
    }
    if ([source isEqualToString:@"camera"]) {
        if (![UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera]) {
            [self p_toastMessage:@"相机不可用"];
            HRFireCallback(callback, @{@"type": @"cancel", @"source": source});
            return;
        }
        UIImagePickerController *picker = [[UIImagePickerController alloc] init];
        picker.sourceType = UIImagePickerControllerSourceTypeCamera;
        picker.modalPresentationStyle = UIModalPresentationFullScreen;
        picker.delegate = self;
        [presenter presentViewController:picker animated:YES completion:nil];
        return;
    }
    if ([source isEqualToString:@"document"]) {
        UTType *itemType = [UTType typeWithIdentifier:@"public.item"];
        if (!itemType) { itemType = [UTType typeWithIdentifier:@"public.data"]; }
        if (!itemType) {
            [self p_finishComposerMediaWithSource:source items:@[]];
            return;
        }
        UIDocumentPickerViewController *picker =
            [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:@[itemType]];
        picker.allowsMultipleSelection = NO;
        picker.delegate = self;
        [presenter presentViewController:picker animated:YES completion:nil];
        return;
    }
    // library：PHPicker 出程选择器，无需相册权限即可多选图片。
    PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
    config.filter = [PHPickerFilter imagesFilter];
    config.selectionLimit = 9;
    PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
    picker.delegate = self;
    [presenter presentViewController:picker animated:YES completion:nil];
}

#pragma mark - PHPickerViewControllerDelegate

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    if (results.count == 0) {
        [self p_finishComposerMediaWithSource:@"library" items:@[]];
        return;
    }
    dispatch_group_t group = dispatch_group_create();
    NSMutableArray<NSDictionary *> *items = [NSMutableArray array];
    NSLock *itemsLock = [[NSLock alloc] init];
    for (PHPickerResult *result in results) {
        NSItemProvider *provider = result.itemProvider;
        NSString *typeId = nil;
        for (NSString *candidate in provider.registeredTypeIdentifiers) {
            if ([candidate hasPrefix:@"image/"]) { typeId = candidate; break; }
        }
        if (!typeId) { typeId = provider.registeredTypeIdentifiers.firstObject; }
        if (!typeId) { dispatch_group_leave(group); continue; }
        NSString *capturedTypeId = typeId;
        dispatch_group_enter(group);
        [provider loadFileRepresentationForTypeIdentifier:capturedTypeId
                                        completionHandler:^(NSURL *url, NSError *error) {
            if (url && [[NSFileManager defaultManager] fileExistsAtPath:url.path]) {
                // completion 返回后原文件即被清理，必须同步复制。
                NSString *extension = url.pathExtension.length > 0 ? url.pathExtension
                                                                   : capturedTypeId.pathExtension;
                NSString *target = HRUniqueMediaPath(@"pick", extension);
                NSError *copyError = nil;
                [[NSFileManager defaultManager] copyItemAtPath:url.path toPath:target error:&copyError];
                if (!copyError) {
                    BOOL isImage = [capturedTypeId hasPrefix:@"image/"] || HRIsImageExtension(target.lastPathComponent);
                    [itemsLock lock];
                    [items addObject:@{
                        @"kind": isImage ? @"image" : @"file",
                        @"path": target,
                        @"name": url.lastPathComponent.length > 0 ? url.lastPathComponent : target.lastPathComponent,
                    }];
                    [itemsLock unlock];
                }
            }
            dispatch_group_leave(group);
        }];
    }
    dispatch_group_notify(group, dispatch_get_main_queue(), ^{
        [self p_finishComposerMediaWithSource:@"library" items:[items copy]];
    });
}

#pragma mark - UIImagePickerControllerDelegate

- (void)imagePickerController:(UIImagePickerController *)picker
didFinishPickingMediaWithInfo:(NSDictionary<UIImagePickerControllerInfoKey, id> *)info {
    [picker dismissViewControllerAnimated:YES completion:nil];
    UIImage *image = info[UIImagePickerControllerOriginalImage];
    if (!image) {
        [self p_finishComposerMediaWithSource:@"camera" items:@[]];
        return;
    }
    NSString *target = HRUniqueMediaPath(@"camera", @"jpg");
    NSData *jpegData = UIImageJPEGRepresentation(image, 0.9);
    if (jpegData && [jpegData writeToFile:target atomically:YES]) {
        [self p_finishComposerMediaWithSource:@"camera" items:@[@{
            @"kind": @"image",
            @"path": target,
            @"name": target.lastPathComponent,
        }]];
    } else {
        [self p_finishComposerMediaWithSource:@"camera" items:@[]];
    }
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
    [picker dismissViewControllerAnimated:YES completion:nil];
    [self p_finishComposerMediaWithSource:@"camera" items:@[]];
}

#pragma mark - UIDocumentPickerDelegate

- (void)documentPicker:(UIDocumentPickerViewController *)controller
didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    if (urls.count == 0) {
        [self p_finishComposerMediaWithSource:@"document" items:@[]];
        return;
    }
    NSURL *url = urls.firstObject;
    BOOL accessing = [url startAccessingSecurityScopedResource];
    NSString *target = HRUniqueMediaPath(@"doc", url.pathExtension);
    NSError *error = nil;
    [[NSFileManager defaultManager] copyItemAtPath:url.path toPath:target error:&error];
    if (accessing) { [url stopAccessingSecurityScopedResource]; }
    if (error) {
        [self p_finishComposerMediaWithSource:@"document" items:@[]];
        return;
    }
    [self p_finishComposerMediaWithSource:@"document" items:@[@{
        @"kind": HRIsImageExtension(target.lastPathComponent) ? @"image" : @"file",
        @"path": target,
        @"name": url.lastPathComponent.length > 0 ? url.lastPathComponent : target.lastPathComponent,
    }]];
}

- (void)documentPickerWasCancelled:(UIDocumentPickerViewController *)controller {
    [self p_finishComposerMediaWithSource:@"document" items:@[]];
}

// 统一回传：items 为空视为取消（与 Android handleComposerMediaResult 对齐）。
- (void)p_finishComposerMediaWithSource:(NSString *)source items:(NSArray<NSDictionary *> *)items {
    KuiklyRenderCallback callback = self.composerMediaCallback;
    if (items.count == 0) {
        HRFireCallback(callback, @{@"type": @"cancel", @"source": source});
    } else {
        HRFireCallback(callback, @{
            @"type": @"ok",
            @"source": source,
            @"items": items,
        });
    }
}

- (void)p_toastMessage:(NSString *)message {
    if (message.length == 0) { return; }
    UIViewController *presenter = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (presenter.presentedViewController) { presenter = presenter.presentedViewController; }
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:nil
                                                                   message:message
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"知道了" style:UIAlertActionStyleDefault handler:nil]];
    [presenter presentViewController:alert animated:YES completion:nil];
}

// 把 composer_media 私有缓存转成单轮 AI 输入：图片 → data URL；PDF → 首页
// JPEG data URL；文本 → 正文截断。阈值与 Android 对齐（6MB / 256KB / 24000 字符）。
// 文件访问属宿主职责，共享层从不读任意路径。
- (void)prepareAiMedia:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSArray<NSDictionary *> *inputItems = params[@"items"] ?: @[];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSMutableArray<NSDictionary *> *output = [NSMutableArray array];
        for (NSDictionary *item in inputItems) {
            if (![item isKindOfClass:[NSDictionary class]]) { continue; }
            NSString *path = [NSString stringWithFormat:@"%@", item[@"path"] ?: @""];
            NSString *name = [NSString stringWithFormat:@"%@", item[@"name"] ?: @""];
            NSString *kind = [NSString stringWithFormat:@"%@", item[@"kind"] ?: @""];
            NSDictionary *fileInfo = [[NSFileManager defaultManager] attributesOfItemAtPath:path error:nil];
            if (!fileInfo) { continue; }
            if (name.length == 0) { name = path.lastPathComponent; }
            if ([kind isEqualToString:@"image"]) {
                unsigned long long size = [fileInfo fileSize];
                if (size > HRMaxAiImageBytes()) {
                    [output addObject:@{
                        @"name": name,
                        @"documentText": @"图片过大，未随请求上传（请控制在 6 MB 内）。",
                    }];
                } else {
                    NSString *dataUrl = HRImageDataUrl(path);
                    if (dataUrl) {
                        [output addObject:@{@"name": name, @"imageDataUrl": dataUrl}];
                    }
                }
            } else if ([path.pathExtension.lowercaseString isEqualToString:@"pdf"]) {
                NSString *dataUrl = HRPdfFirstPageDataUrl(path);
                if (dataUrl) {
                    [output addObject:@{@"name": [name stringByAppendingString:@"（第 1 页）"],
                                        @"imageDataUrl": dataUrl}];
                } else {
                    [output addObject:@{
                        @"name": name,
                        @"documentText": @"PDF 预览生成失败，请上传页面截图或复制关键段落。",
                    }];
                }
            } else {
                [output addObject:@{@"name": name, @"documentText": HRReadTextDocument(path)}];
            }
        }
        HRFireCallback(callback, @{@"items": [output copy]});
    });
}

static unsigned long long HRMaxAiImageBytes(void) {
    return 6ULL * 1024ULL * 1024ULL;
}

static NSString *HRImageDataUrl(NSString *path) {
    NSData *data = [NSData dataWithContentsOfFile:path];
    if (!data) { return nil; }
    NSString *mime = @"image/jpeg";
    NSString *ext = path.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"png"]) { mime = @"image/png"; }
    else if ([ext isEqualToString:@"webp"]) { mime = @"image/webp"; }
    else if ([ext isEqualToString:@"gif"]) { mime = @"image/gif"; }
    else if ([ext isEqualToString:@"heic"] || [ext isEqualToString:@"heif"]) { mime = @"image/heic"; }
    return [NSString stringWithFormat:@"data:%@;base64,%@", mime, [data base64EncodedStringWithOptions:0]];
}

static NSString *HRPdfFirstPageDataUrl(NSString *path) {
    CGPDFDocumentRef document = CGPDFDocumentCreateWithURL((__bridge CFURLRef)[NSURL fileURLWithPath:path]);
    if (!document) { return nil; }
    size_t pageCount = CGPDFDocumentGetNumberOfPages(document);
    if (pageCount == 0) {
        CGPDFDocumentRelease(document);
        return nil;
    }
    CGPDFPageRef page = CGPDFDocumentGetPage(document, 1);
    CGRect box = CGPDFPageGetBoxRect(page, kCGPDFMediaBox);
    CGFloat scale = MIN(2.0, MAX(1.0, 1440.0 / MAX(box.size.width, 1.0)));
    CGFloat width = MAX(1.0, floor(box.size.width * scale));
    CGFloat height = MAX(1.0, floor(box.size.height * scale));
    UIGraphicsImageRendererFormat *format = [[UIGraphicsImageRendererFormat alloc] init];
    format.opaque = YES;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(width, height) format:format];
    UIImage *image = [renderer imageWithActions:^(UIGraphicsImageRendererContext *rendererContext) {
        CGContextScaleCTM(rendererContext.CGContext, scale, scale);
        CGContextSetRGBFillColor(rendererContext.CGContext, 1.0, 1.0, 1.0, 1.0);
        CGContextFillRect(rendererContext.CGContext, box);
        CGContextDrawPDFPage(rendererContext.CGContext, page);
    }];
    CGPDFDocumentRelease(document);
    NSData *jpegData = UIImageJPEGRepresentation(image, 0.85);
    if (!jpegData || jpegData.length > HRMaxAiImageBytes()) { return nil; }
    return [NSString stringWithFormat:@"data:image/jpeg;base64,%@", [jpegData base64EncodedStringWithOptions:0]];
}

static NSString *HRReadTextDocument(NSString *path) {
    static dispatch_once_t onceToken;
    static NSSet<NSString *> *textExtensions = nil;
    dispatch_once(&onceToken, ^{
        textExtensions = [NSSet setWithArray:@[@"txt", @"md", @"markdown", @"csv", @"tsv", @"json", @"xml", @"html", @"htm", @"log"]];
    });
    NSString *extension = path.pathExtension.lowercaseString;
    if (![textExtensions containsObject:extension]) {
        return [NSString stringWithFormat:@"该文件为 .%@ 格式，当前会话无法安全提取正文。请上传 PDF 页面截图或复制关键段落，我可以继续解读。", extension];
    }
    NSDictionary *fileInfo = [[NSFileManager defaultManager] attributesOfItemAtPath:path error:nil];
    if (fileInfo && [fileInfo fileSize] > 256ULL * 1024ULL) {
        return @"文档超过 256 KB，仅支持上传更小的文本文件或复制关键段落。";
    }
    NSError *error = nil;
    NSString *content = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&error];
    if (error || !content) {
        return @"文档无法按 UTF-8 文本读取，请复制关键段落后重试。";
    }
    if (content.length > 24000) {
        return [content substringToIndex:24000];
    }
    return content;
}

// 页面耗时/实时埋点：iOS 暂未接上报通道。
- (void)reportDT:(NSDictionary *)args {
}

- (void)reportRealtime:(NSDictionary *)args {
}

- (void)reportPageCostTimeForCache:(NSDictionary *)args {
}

- (void)reportPageCostTimeForSuccess:(NSDictionary *)args {
}

- (void)reportPageCostTimeForError:(NSDictionary *)args {
}

// 状态栏图标明暗：iOS 由宿主 Info.plist/偏好控制，页侧同步忽略。
- (void)setStatusBarIconsDark:(NSDictionary *)args {
}

- (NSString *)currentTimestamp:(NSDictionary *)args {
    return [NSString stringWithFormat:@"%lld", (long long)([[NSDate date] timeIntervalSince1970] * 1000.0)];
}

- (NSString *)dateFormatter:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    long long timeStamp = [params[@"timeStamp"] longLongValue];
    NSString *format = params[@"format"] ?: @"";
    if (format.length == 0) { return @""; }
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = format;
    formatter.timeZone = [NSTimeZone localTimeZone];
    formatter.locale = [NSLocale localeWithLocaleIdentifier:@"zh_CN"];
    return [formatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:(timeStamp / 1000.0)]];
}

// 轻提示：最小 HUD 实现（页侧 toast）。
- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"] ?: @"";
    if (content.length == 0) { return; }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIView *toast = [[UILabel alloc] initWithFrame:CGRectZero];
        UILabel *label = (UILabel *)toast;
        label.text = content;
        label.font = [UIFont systemFontOfSize:14.0];
        label.textColor = [UIColor whiteColor];
        label.backgroundColor = [UIColor colorWithWhite:0.15 alpha:0.88];
        label.textAlignment = NSTextAlignmentCenter;
        label.numberOfLines = 0;
        label.layer.cornerRadius = 10.0;
        label.layer.masksToBounds = YES;
        CGFloat maxWidth = 240.0;
        CGSize fit = [label sizeThatFits:CGSizeMake(maxWidth - 32.0, CGFLOAT_MAX)];
        CGFloat width = MIN(maxWidth, fit.width + 32.0);
        CGFloat height = MAX(36.0, fit.height + 20.0);
        UIViewController *presenter = [UIApplication sharedApplication].keyWindow.rootViewController;
        while (presenter.presentedViewController) { presenter = presenter.presentedViewController; }
        if (!presenter.view.window) { return; }
        label.frame = CGRectMake((presenter.view.bounds.size.width - width) / 2.0,
                                 presenter.view.bounds.size.height - height - 120.0, width, height);
        label.alpha = 0.0;
        [presenter.view addSubview:label];
        [UIView animateWithDuration:0.2 animations:^{ label.alpha = 1.0; } completion:nil];
        [UIView animateWithDuration:0.25 delay:1.6 options:UIViewAnimationOptionCurveEaseIn animations:^{
            label.alpha = 0.0;
        } completion:^(BOOL finished){ [label removeFromSuperview]; }];
    });
}

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

// 语音链路（AVAudioEngine + SFSpeechRecognizer zh-CN）。事件契约与 Android 对齐：
// {type:"ready"} / {type:"amplitude", rms:0..1} / {type:"partial", text} /
// {type:"transcript", text} / {type:"error", error, message}。
// start/stop/cancel 都是页侧按住说话状态机驱动；所有回调经 HRFireCallback
// 派发回 context queue（铁律 A）。
- (void)startVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSRecursiveLock *lock = HRVoiceLock();
    [lock lock];
    IOSVoiceSession *previous = _activeVoiceSession;
    _activeVoiceSession = nil;
    [lock unlock];
    [previous cancel];
    if (!callback) { return; }
    IOSVoiceSession *session = [[IOSVoiceSession alloc] initWithCallback:callback
                                                               onFinish:^(IOSVoiceSession *finished) {
        NSRecursiveLock *finishLock = HRVoiceLock();
        [finishLock lock];
        if (_activeVoiceSession == finished) { _activeVoiceSession = nil; }
        [finishLock unlock];
    }];
    NSRecursiveLock *storeLock = HRVoiceLock();
    [storeLock lock];
    _activeVoiceSession = session;
    [storeLock unlock];
    [session start];
}

// 同步调用（页侧松手即返回），最终 transcript 经 callback 异步回传。
- (NSString *)stopVoiceRecording:(NSDictionary *)args {
    NSRecursiveLock *lock = HRVoiceLock();
    [lock lock];
    IOSVoiceSession *session = _activeVoiceSession;
    [lock unlock];
    [session stop];
    return @"{\"path\":\"\"}";
}

- (void)cancelVoiceRecording:(NSDictionary *)args {
    NSRecursiveLock *lock = HRVoiceLock();
    [lock lock];
    IOSVoiceSession *session = _activeVoiceSession;
    _activeVoiceSession = nil;
    [lock unlock];
    [session cancel];
}

- (NSString *)getGlassMode:(NSDictionary *)args {
    if (UIAccessibilityIsReduceTransparencyEnabled() || UIAccessibilityIsReduceMotionEnabled()) {
        return @"simplified";
    }
    return @"realtime";
}

@end

#pragma mark - IOSVoiceSession

@implementation IOSVoiceSession

- (instancetype)initWithCallback:(KuiklyRenderCallback)callback
                        onFinish:(void (^)(IOSVoiceSession *session))onFinish {
    self = [super init];
    if (self) {
        _callback = callback;
        _onFinish = onFinish;
        _workQueue = dispatch_queue_create("com.stockchat.voice.session", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

- (void)start {
    dispatch_async(self.workQueue, ^{
        SFSpeechRecognizerAuthorizationStatus status = [SFSpeechRecognizer authorizationStatus];
        if (status == SFSpeechRecognizerAuthorizationStatusNotDetermined) {
            [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus granted) {
                dispatch_async(self.workQueue, ^{
                    if (!self.delivered && !self.stopping) {
                        [self beginWithSpeechStatus:granted];
                    }
                });
            }];
        } else {
            [self beginWithSpeechStatus:status];
        }
    });
}

- (void)beginWithSpeechStatus:(SFSpeechRecognizerAuthorizationStatus)status {
    if (self.delivered || self.stopping) { return; }
    if (status != SFSpeechRecognizerAuthorizationStatusAuthorized) {
        [self failWithError:@"PERMISSION_DENIED" message:@"需要语音识别与麦克风权限"];
        return;
    }
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
        dispatch_async(self.workQueue, ^{
            if (self.delivered || self.stopping) { return; }
            if (granted) {
                [self beginRecording];
            } else {
                [self failWithError:@"PERMISSION_DENIED" message:@"需要麦克风权限"];
            }
        });
    }];
}

- (void)beginRecording {
    if (self.delivered || self.stopping) { return; }
    self.recognizer = [[SFSpeechRecognizer alloc] initWithLocale:[[NSLocale alloc] initWithLocaleIdentifier:@"zh-CN"]];
    if (!self.recognizer || !self.recognizer.isAvailable) {
        [self failWithError:@"UNAVAILABLE" message:@"当前设备没有可用语音识别服务"];
        return;
    }
    NSError *sessionError = nil;
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    [audioSession setCategory:AVAudioSessionCategoryPlayAndRecord
                         mode:AVAudioSessionModeMeasurement
                      options:AVAudioSessionCategoryOptionDefaultToSpeaker
                        error:&sessionError];
    if (sessionError || ![audioSession setActive:YES error:&sessionError] || sessionError) {
        [self failWithError:@"MIC_OCCUPIED" message:@"麦克风被占用"];
        return;
    }
    self.engine = [[AVAudioEngine alloc] init];
    AVAudioInputNode *inputNode = self.engine.inputNode;
    AVAudioFormat *inputFormat = [inputNode outputFormatForBus:0];
    if (!inputFormat || inputFormat.sampleRate <= 0) {
        [self failWithError:@"MIC_OCCUPIED" message:@"麦克风被占用"];
        return;
    }
    self.request = [[SFSpeechAudioBufferRecognitionRequest alloc] init];
    self.request.shouldReportPartialResults = YES;
    __weak typeof(self) wself = self;
    __block double lastAmplitudePost = 0;
    [inputNode installTapOnBus:0 bufferSize:2048 format:inputFormat
                         block:^(AVAudioPCMBuffer *buffer, AVAudioTime *when) {
        __strong typeof(wself) sself = wself;
        if (!sself || !buffer) { return; }
        [sself.request appendAudioPCMBuffer:buffer];
        // 真实 PCM RMS（float32），归一尺度对齐 Android 的 /2500@int16
        // （≈ ×13），页侧还有 ×2.5 增益；≥35ms 一拍。
        float *channelData = buffer.floatChannelData[0];
        NSUInteger frames = buffer.frameLength;
        double sum = 0;
        for (NSUInteger i = 0; i < frames; i++) {
            double v = channelData[i];
            sum += v * v;
        }
        double rms = sqrt(sum / MAX(frames, 1));
        double now = [NSDate date].timeIntervalSince1970;
        if (now - lastAmplitudePost >= 0.035) {
            lastAmplitudePost = now;
            double normalized = MIN(1.0, rms * 13.0);
            HRFireCallback(sself.callback, @{@"type": @"amplitude", @"rms": @(normalized)});
        }
    }];
    NSError *engineError = nil;
    if (![self.engine startAndReturnError:&engineError]) {
        [self failWithError:@"MIC_OCCUPIED" message:@"麦克风被占用"];
        return;
    }
    HRFireCallback(self.callback, @{@"type": @"ready"});
    __typeof(self) __weak weakSelf = self;
    [self.recognizer recognitionTaskWithRequest:self.request
                                   resultHandler:^(SFSpeechRecognitionResult *result, NSError *error) {
        __typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }
        dispatch_async(strongSelf.workQueue, ^{
            [strongSelf handleRecognitionResult:result error:error];
        });
    }];
}

- (void)handleRecognitionResult:(nullable SFSpeechRecognitionResult *)result
                          error:(nullable NSError *)error {
    if (self.delivered) { return; }
    if (result) {
        NSString *text = result.bestTranscription.formattedString ?: @"";
        if (result.isFinal) {
            [self deliverTranscript:text];
        } else if (text.length > 0) {
            self.latestPartial = text;
            HRFireCallback(self.callback, @{@"type": @"partial", @"text": text});
        }
        return;
    }
    if (self.stopping) {
        [self deliverTranscript:self.latestPartial ?: @""];
        return;
    }
    NSInteger code = error.code;
    if (code == 1) { return; } // cancelled：用户取消路径不回调
    NSString *mapped = @"UNAVAILABLE";
    NSString *message = @"语音识别失败";
    if (code == 2) {
        mapped = @"PERMISSION_DENIED";
        message = @"需要语音识别与麦克风权限";
    } else if (code == 3 || code == 1101 || code == 1107) {
        mapped = @"UNAVAILABLE";
        message = @"语音识别服务暂不可用";
    } else if (code == 1110) {
        mapped = @"NO_MATCH";
        message = @"没听清";
    }
    [self failWithError:mapped message:message];
}

- (void)stop {
    dispatch_async(self.workQueue, ^{
        if (self.delivered || self.stopping) { return; }
        self.stopping = YES;
        [self stopCapture];
        if (self.latestPartial.length > 0) {
            // 与 Android stop() 对齐：已有中间结果直接交付，避免卡转写态。
            [self deliverTranscript:self.latestPartial];
        } else {
            // 等最终结果；3 秒兜底，绝不把页面留在 TRANSCRIBING。
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(3.0 * NSEC_PER_SEC)),
                           self.workQueue, ^{
                if (!self.delivered) { [self deliverTranscript:@""]; }
            });
        }
    });
}

- (void)cancel {
    dispatch_async(self.workQueue, ^{
        self.delivered = YES; // 取消后不产生任何回调
        [self stopCapture];
        [self deactivateAudioSession];
        [self finish];
    });
}

- (void)deliverTranscript:(NSString *)text {
    if (self.delivered) { return; }
    self.delivered = YES;
    HRFireCallback(self.callback, @{@"type": @"transcript", @"text": text ?: @""});
    [self stopCapture];
    [self deactivateAudioSession];
    [self finish];
}

- (void)failWithError:(NSString *)error message:(NSString *)message {
    if (self.delivered) { return; }
    self.delivered = YES;
    HRFireCallback(self.callback, @{@"type": @"error", @"error": error ?: @"UNAVAILABLE", @"message": message ?: @""});
    [self stopCapture];
    [self deactivateAudioSession];
    [self finish];
}

- (void)stopCapture {
    [self.engine.inputNode removeTapOnBus:0];
    [self.request endAudio];
    [self.engine stop];
    self.request = nil;
    self.engine = nil;
}

- (void)deactivateAudioSession {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        [[AVAudioSession sharedInstance] setActive:NO
                                       withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
                                             error:nil];
    });
}

- (void)finish {
    if (self.onFinish) {
        void (^finishBlock)(IOSVoiceSession *) = self.onFinish;
        self.onFinish = nil;
        finishBlock(self);
    }
}

@end
