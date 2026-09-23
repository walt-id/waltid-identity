import SwiftUI

@main
struct PhysicalHostApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 16) {
                Text("Disposable proximity test host").font(.title2)
                Text("Follow the local controller for permission and positioning steps.")
            }.padding()
        }
    }
}
