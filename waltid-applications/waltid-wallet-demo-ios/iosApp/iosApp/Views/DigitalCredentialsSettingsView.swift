import SwiftUI
import WalletDemoSharingUI

struct DigitalCredentialsSettingsView: View {
    @Binding var showWalletReview: Bool

    var body: some View {
        List {
            Section {
                Toggle("Show wallet review", isOn: $showWalletReview)
                    .accessibilityIdentifier(WalletAccessibilityID.settingsShowDcApiPreview)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Show the wallet review after you select a credential in the system picker. Turning this off skips only the wallet review. System consent and any required signing approval still apply.")
                    Text("Requires iOS 26 or later and a compatible app or browser. This setting controls wallet review only.")
                }
            }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Digital Credentials API")
        .navigationBarTitleDisplayMode(.inline)
    }
}
