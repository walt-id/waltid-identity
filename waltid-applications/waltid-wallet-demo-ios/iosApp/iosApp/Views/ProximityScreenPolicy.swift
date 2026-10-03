import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

@MainActor
final class ProximityScreenPolicy: ObservableObject {
    private var originalIdleTimerDisabled: Bool?
    private var originalBrightness: CGFloat?

    func update(active: Bool, qrVisible: Bool) {
        guard active else {
            restore()
            return
        }
        if originalIdleTimerDisabled == nil {
            originalIdleTimerDisabled = UIApplication.shared.isIdleTimerDisabled
        }
        UIApplication.shared.isIdleTimerDisabled = true

        if qrVisible {
            if originalBrightness == nil {
                originalBrightness = UIScreen.main.brightness
            }
            UIScreen.main.brightness = 1
        } else if let originalBrightness {
            UIScreen.main.brightness = originalBrightness
            self.originalBrightness = nil
        }
    }

    func restore() {
        if let originalIdleTimerDisabled {
            UIApplication.shared.isIdleTimerDisabled = originalIdleTimerDisabled
            self.originalIdleTimerDisabled = nil
        }
        if let originalBrightness {
            UIScreen.main.brightness = originalBrightness
            self.originalBrightness = nil
        }
    }

    // PresentView explicitly restores on disappearance and all lifecycle/state exits.
    // Deallocation may happen off MainActor and must not touch UIKit.
}
