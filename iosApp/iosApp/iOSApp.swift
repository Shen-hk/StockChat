import SwiftUI
import UserNotifications

final class StockChatAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // 本地 Mock 通知在 App 前台时也展示横幅；声音是否播放仍由通知内容与系统设置决定。
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

@main
struct iOSApp: App {
	@UIApplicationDelegateAdaptor(StockChatAppDelegate.self) private var appDelegate

	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}
