import SwiftUI
import UIKit

/// Shared recovery copy and supported settings entry point for unlock and signing.
struct BiometricRecoverySection: View {
    let availability: DemoBiometricAvailability
    var kind: DemoBiometricKind = .generic
    var showSettingsAction = true

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(availability.explanation(kind: kind)).font(.callout).foregroundStyle(.secondary)
            if showSettingsAction && availability.offersSettings {
                Button("Open Settings", action: BiometricSettings.open)
                    .accessibilityIdentifier("wallet.biometricOpenSettings")
            }
        }
    }
}

@MainActor
enum BiometricSettings {
    static func open() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
