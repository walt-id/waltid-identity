import SwiftUI
import WalletDemoSharingUI

struct EmptyCredentialsView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("No credentials yet")
                .font(.headline)
            Text("Scan a credential offer to add your first credential.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .aspectRatio(id1AspectRatio, contentMode: .fit)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityIdentifier(WalletAccessibilityID.credentialsEmpty)
    }
}
