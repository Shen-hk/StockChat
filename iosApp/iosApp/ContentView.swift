import SwiftUI
import UIKit
import stockchat

struct ContentView: View {

	var body: some View {
        KuiklyRenderViewPage(
            pageName: "ChatPage",
            data: ["glassMode": (UIAccessibility.isReduceTransparencyEnabled || UIAccessibility.isReduceMotionEnabled) ? "simplified" : "realtime"]
        ).ignoresSafeArea()
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
