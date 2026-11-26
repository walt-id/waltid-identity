import SwiftUI
import WalletDemoSharingUI

struct DigitalCredentialsSettingsView: View {
    @Binding var showWalletReview: Bool

    var body: some View {
        List {
            Section {
                Toggle("Skip credential status checks", isOn: Binding(
                    get: { !showWalletReview }, set: { showWalletReview = !$0 }
                ))
                    .accessibilityIdentifier(WalletAccessibilityID.settingsShowDcApiPreview)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("When enabled, the wallet skips warnings about suspended or revoked credentials.")
                    Text("Review the requester and requested data, then tap Share. Requires iOS 26 or later and a compatible app or browser.")
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
