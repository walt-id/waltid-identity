import SwiftUI

struct EmptyCredentialsView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "wallet.pass")
                .font(.title)
                .foregroundStyle(.secondary)
            Text("No credentials yet")
                .font(.headline)
            Text("Received credentials will appear here automatically.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier(WalletAccessibilityID.credentialsEmpty)
    }
}
