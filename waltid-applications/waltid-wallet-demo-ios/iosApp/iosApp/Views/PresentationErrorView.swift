import SwiftUI
import WalletSDK
import WalletDemoSharingUI

struct PresentationErrorView: View {
    @Environment(\.walletDemoBranding) private var branding
    let error: PresentationPreviewError
    let isEnabled: Bool
    let onNotifyVerifier: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("This request cannot be completed")
                .font(.headline)
            SharingRequestSections(request: error.request.sharingRequest())
            Text(error.message)
            Text("OpenID4VP error: \(error.code.rawValue)")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("You can notify the verifier or dismiss the request without sending a response.")
                .font(.caption)
                .foregroundStyle(.secondary)

            WalletActions(
                primary: WalletAction("Notify verifier", enabled: isEnabled,
                    identifier: WalletAccessibilityID.presentationErrorNotifyButton, perform: onNotifyVerifier),
                secondary: WalletAction("Dismiss", enabled: isEnabled,
                    identifier: WalletAccessibilityID.presentationErrorDismissButton, perform: onDismiss)
            )
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(WalletAccessibilityID.presentationError)
    }
}
