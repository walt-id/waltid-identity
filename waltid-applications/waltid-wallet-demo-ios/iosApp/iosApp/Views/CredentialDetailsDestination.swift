import SwiftUI
import WalletDemoSharingUI

struct CredentialDetailsDestination: View {
    let detailsID: String
    let details: [CredentialDetails]
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    var body: some View {
        if let item = details.first(where: { $0.id == detailsID }) {
            CredentialDetailsScreen(
                details: item,
                rawCredentialJSON: viewModel.credentials.first(where: { $0.id == item.credentialId })?.credentialDataJSON,
                onDelete: {
                    viewModel.deleteCredential(id: item.credentialId)
                    selectedDetailsID = nil
                }
            )
        } else {
            Text("Credential details unavailable")
                .foregroundStyle(.secondary)
                .navigationTitle("Credential")
        }
    }
}
