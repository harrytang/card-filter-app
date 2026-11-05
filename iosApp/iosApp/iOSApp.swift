import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Compose applies the safe-area and keyboard insets itself.
                .ignoresSafeArea()
        }
    }
}
