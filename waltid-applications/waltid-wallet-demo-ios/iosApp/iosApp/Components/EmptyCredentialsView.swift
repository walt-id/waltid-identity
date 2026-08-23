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
            HStack {
                Spacer()
                Button {
                    onScan()
                } label: {
                    Label("Scan a code", systemImage: "qrcode.viewfinder")
                }
                .buttonStyle(WalletPrimaryButtonStyle(compact: true))
                .disabled(!scanEnabled)
                .accessibilityIdentifier(WalletAccessibilityID.scanEmptyAction)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(.separator), lineWidth: 1))
        .accessibilityIdentifier(WalletAccessibilityID.credentialsEmpty)
    }
}
