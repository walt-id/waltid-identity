import SwiftUI
import WalletDemoSharingUI

struct EmptyCredentialsView: View {
    let onScan: () -> Void
    var scanEnabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "wallet.pass")
                .font(.title)
                .foregroundStyle(.secondary)
            Text("No credentials yet")
                .font(.headline)
            Text("Scan a credential offer to add your first one.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button {
                onScan()
            } label: {
                Label("Scan a code", systemImage: "qrcode.viewfinder")
            }
            .buttonStyle(.borderedProminent)
            .tint(.waltBlue)
            .disabled(!scanEnabled)
            .accessibilityIdentifier(WalletAccessibilityID.scanEmptyAction)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier(WalletAccessibilityID.credentialsEmpty)
    }
}
