import { KuiklyNativeManager } from '@kuikly-open/render';
import Napi from 'libentry.so';
import hilog from '@ohos.hilog';

class MyNativeManager extends KuiklyNativeManager {
  protected loadNative(): number {
    hilog.info(0x0000, 'testTag', 'MyNativeManager.loadNative: calling Napi.initKuikly()');
    const handler = Napi.initKuikly();
    hilog.info(0x0000, 'testTag', 'MyNativeManager.loadNative: Napi.initKuikly() returned %{public}d', handler);
    return handler;
  }
}

// 导出一个全局的 KuiklyNativeManager 实例给 Kuikly 页面共用
const globalNativeManager = new MyNativeManager();

export default globalNativeManager;