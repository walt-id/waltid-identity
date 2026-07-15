import SwiftUI

struct CredentialDetailsDestination: View {
    let detailsID: String
    let details: [CredentialDetails]

    var body: some View {
        if let item = details.first(where: { $0.id == detailsID }) {
            CredentialDetailsScreen(details: item)
        } else {
            Text("Credential details unavailable")
                .foregroundStyle(.secondary)
                .navigationTitle("Credential")
        }
    }
}
