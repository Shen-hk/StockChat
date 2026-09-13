#include "napi/native_api.h"
#include "libshared_api.h"
#include <hilog/log.h>
#include <dlfcn.h>
#include <cstdint>

// =============================================================================
// KRRenderCValue / CallKotlin — 镜像 KuiklyUI core-render-ohos 的同名头文件。
// libshared.so 不注册 com_tencent_kuikly_SetCallKotlin（当前依赖的
// core:2.25.0-2.0.21-ohos 的 KSP 生成器漏掉了这步），所以 libkuikly.so 的
// callKotlin_ 全局始终为 NULL，第一次回调就 SIGABRT。
// 这里在 libentry.so 里手动 dlopen + dlsym SetCallKotlin 并装一个 no-op trampoline，
// 至少把崩溃消掉（Kuikly 页面会空白，等后续上完整 Kotlin 桥接再恢复交互）。
// =============================================================================
typedef struct KRRenderCValue {
    enum Type { NULL_VALUE, INT, LONG, FLOAT, DOUBLE, BOOL, STRING, BYTES, ARRAY } type;
    union Value {
        int32_t intValue;
        int64_t longValue;
        float   floatValue;
        double  doubleValue;
        int     boolValue;
        char   *stringValue;
        char   *bytesValue;
        struct KRRenderCValue *arrayValue;
    } value;
    int32_t size;
} KRRenderCValue;

typedef void (*CallKotlin)(int methodId,
                           KRRenderCValue arg0, KRRenderCValue arg1, KRRenderCValue arg2,
                           KRRenderCValue arg3, KRRenderCValue arg4, KRRenderCValue arg5);
typedef int (*SetCallKotlinFn)(CallKotlin callKotlin);

static void NoopCallKotlin(int /*methodId*/,
                           KRRenderCValue, KRRenderCValue, KRRenderCValue,
                           KRRenderCValue, KRRenderCValue, KRRenderCValue) {
    // 占位：libkuikly.so 后续所有回调都进这里，不做任何事。
    // TODO 替换为真正桥接到 BridgeManager.callKotlinMethod 的 trampoline。
}

static void InstallKuiklyCallKotlinBridge() {
    OH_LOG_INFO(LOG_APP, "InstallKuiklyCallKotlinBridge: enter");
    // libkuikly.so 可能不是 RTLD_GLOBAL 加载，先 dlopen 拿到自己的句柄，
    // 再用 RTLD_DEFAULT 在全局符号表里找（覆盖 har 已加载的情况）。
    void *h = dlopen("libkuikly.so", RTLD_NOW | RTLD_NOLOAD);
    if (h == nullptr) {
        // 还没在进程里：再走默认路径加载一次。ArkTS import 时通常会先拉起来。
        h = dlopen("libkuikly.so", RTLD_NOW);
    }
    OH_LOG_INFO(LOG_APP, "InstallKuiklyCallKotlinBridge: dlopen libkuikly.so handle=%{public}p",
                h);
    SetCallKotlinFn setCallKotlin =
        reinterpret_cast<SetCallKotlinFn>(dlsym(RTLD_DEFAULT, "com_tencent_kuikly_SetCallKotlin"));
    if (setCallKotlin == nullptr) {
        OH_LOG_ERROR(LOG_APP, "InstallKuiklyCallKotlinBridge: dlsym SetCallKotlin failed: %{public}s",
                     dlerror());
        return;
    }
    OH_LOG_INFO(LOG_APP, "InstallKuiklyCallKotlinBridge: dlsym SetCallKotlin=%{public}p",
                setCallKotlin);
    int rc = setCallKotlin(&NoopCallKotlin);
    OH_LOG_INFO(LOG_APP, "InstallKuiklyCallKotlinBridge: SetCallKotlin(no-op) returned %{public}d", rc);
}

static napi_value InitKuikly(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, "InitKuikly: enter");
    auto api = libshared_symbols();
    int handler = api->kotlin.root.initKuikly();
    OH_LOG_INFO(LOG_APP, "InitKuikly: kotlin.root.initKuikly() returned %{public}d", handler);
    napi_value result;
    napi_create_int32(env, handler, &result);
    // 关键：在 libshared 完成 initKuikly 之后，立刻给 libkuikly.so 装上回调，
    // 否则 doDidInit → CallKotlinMethod 会断言 callKotlin_==NULL 并 abort。
    InstallKuiklyCallKotlinBridge();
    return result;
}

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports)
{
    napi_property_descriptor desc[] = {
        {"initKuikly", nullptr, InitKuikly, nullptr, nullptr, nullptr, napi_default, nullptr}
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}
EXTERN_C_END

static napi_module demoModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "entry",
    .nm_priv = ((void*)0),
    .reserved = { 0 },
};

extern "C" __attribute__((constructor)) void RegisterEntryModule(void)
{
    napi_module_register(&demoModule);
}