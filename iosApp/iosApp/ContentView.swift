import SwiftUI
import UIKit
import stockchat

struct ContentView: View {

	var body: some View {
        // 验证钩子（simctl 冒烟用）：环境变量 KR_ROOT_PAGE 指定启动页（默认 ChatPage），
        // KR_ROOT_SYMBOL 透传给页面 params（详情页 symbol / 对话页 focusSymbol）。
        // KR_ROOT_QUESTION 透传问句；KR_ROOT_AUTOASK=1 时对话页注入问句后自动发送
        // （本机无辅助功能权限、无法驱动真实点击，只能靠这条链路跑通 AI 流式）。
        // KR_AI_KEY/KR_AI_ENDPOINT/KR_AI_MODEL：AI 配置写进共享层读取的 UserDefaults。
        // 用法：SIMCTL_CHILD_KR_ROOT_PAGE=MarketPage xcrun simctl launch <udid> <bundle>
        let env = ProcessInfo.processInfo.environment
        var data: [String: Any] = ["glassMode": (UIAccessibility.isReduceTransparencyEnabled || UIAccessibility.isReduceMotionEnabled) ? "simplified" : "realtime"]
        if let sym = env["KR_ROOT_SYMBOL"] { data["symbol"] = sym; data["focusSymbol"] = sym }
        if let q = env["KR_ROOT_QUESTION"] { data["question"] = q }
        if env["KR_ROOT_AUTOASK"] == "1" { data["autoAsk"] = "1" }
        if let d = env["KR_ROOT_AUTOASK_DELAY"] { data["autoAskDelay"] = d }
        // 冒烟钩子（simctl 专用）：自动走一次媒体入口 / 语音链路，验证 iOS 桥。
        if let m = env["KR_ROOT_SMOKE_MEDIA"] { data["smokeMedia"] = m }
        if env["KR_ROOT_SMOKE_VOICE"] == "1" { data["smokeVoice"] = "1" }
        // 冒烟钩子：知识库页直接落到二级词表（复现「浏览全部 N 个概念」入口，无需真实点击）。
        if let m = env["KR_ROOT_GLOSSARY_MODE"] { data["glossaryMode"] = m }
        if env["KR_ROOT_GLOSSARY_EXPAND"] == "1" { data["glossaryExpand"] = "1" }
        Self.injectAiConfigIfNeeded(env: env)
        return KuiklyRenderViewPage(
            pageName: env["KR_ROOT_PAGE"] ?? "ChatPage",
            data: data
        ).ignoresSafeArea()
	}

    /// 仅用于本机冒烟：把环境变量里的 AI 配置写入共享层 SharedPreferences 映射的
    /// UserDefaults。不传 KR_AI_KEY 时完全不生效，key 绝不落进源码。
    private static func injectAiConfigIfNeeded(env: [String: String]) {
        guard let key = env["KR_AI_KEY"], !key.isEmpty else { return }
        let config: [String: String] = [
            "endpoint": env["KR_AI_ENDPOINT"] ?? "https://api.deepseek.com/chat/completions",
            "model": env["KR_AI_MODEL"] ?? "deepseek-chat",
            "apiKey": key,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: config),
              let raw = String(data: data, encoding: .utf8) else { return }
        UserDefaults.standard.set(raw, forKey: "stockchat_ai_config_v1")
        UserDefaults.standard.synchronize()
        NSLog("[StockChat] AI config injected from env, model=\(config["model"] ?? "")")
    }
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
