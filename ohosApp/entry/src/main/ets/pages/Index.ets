import { ErrorReason, KRRecord, Kuikly, KuiklyRenderBaseView } from '@kuikly-open/render';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';
import hilog from '@ohos.hilog';

@Entry
@Component
struct Index {
  private static readonly DEFAULT_PAGE_NAME: string = 'ChatPage';
  kuiklyViewDelegate = new KuiklyViewDelegate();
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = "";

  aboutToAppear() {
    AppStorage.setOrCreate<UIContext>("context", this.getUIContext());

    // 真机实测（2026-09-13）：Kuikly 组件首次 onAreaChange 会同步触发
    // doOnAttach -> doDidInit 调用 native，若此时 initKuikly() 还没跑完，
    // native 侧 DefaultRenderNativeContextHandler 的断言直接 SIGABRT 整个进程
    // （Assertion failed: make sure initKuikly() has been called!）。
    // 在组件树 build() 之前同步跑一次 internalDoLoad，把 native 初始化提到
    // 首帧布局回调之前，消掉这个初始化顺序竞争。
    hilog.info(0x0000, 'testTag', 'Index.aboutToAppear: before internalDoLoad');
    globalNativeManager.internalDoLoad();
    hilog.info(0x0000, 'testTag', 'Index.aboutToAppear: after internalDoLoad');

    const params = router.getParams() as Record<string, Object>;
    this.pageName = params?.pageName as string;
    this.pageData = params?.pageData as KRRecord;
  }

  build() {
    Stack() {
      Kuikly({
        pagerName: this.pageName ?? Index.DEFAULT_PAGE_NAME,
        pagerData: this.pageData ?? {},
        // The locked OHOS renderer does not resolve assets:// URLs unless the
        // host supplies the unpacked resource root. Page assets are mirrored
        // into rawfile at build time and exposed below context.resourceDir.
        assetsDir: getContext(this).resourceDir,
        // Keep API/model configuration in HarmonyOS Preferences. The legacy
        // shared-preferences bridge is not reliable on the native renderer.
        useOhSharedPreferences: true,
        delegate: this.kuiklyViewDelegate,
        nativeManager: globalNativeManager,
        onRenderException: (exception: Error, reason: ErrorReason) => {
          this.exception = `${exception.name}:\n${exception.message}`;
        },
      })
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }
}
