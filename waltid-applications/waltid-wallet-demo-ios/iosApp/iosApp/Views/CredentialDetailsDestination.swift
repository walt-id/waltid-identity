import SwiftUI
import WalletDemoSharingUI

struct CredentialDetailsDestination: View {
    let detailsID: String
    let details: [CredentialDetails]
    var onDelete: ((String) -> Void)? = nil

    var body: some View {
        if let item = details.first(where: { $0.id == detailsID }) {
            CredentialDetailsScreen(details: item, storedCredentialActions: onDelete != nil, onDelete: onDelete)
        } else {
            Text("Credential details unavailable")
                .foregroundStyle(.secondary)
                .navigationTitle("Credential")
        }
    }
}
