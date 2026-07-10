import SwiftUI

struct CredentialDetailsDestination: View {
    let credentialID: String
    let details: [CredentialDetails]

    var body: some View {
        if let item = details.first(where: { $0.id == credentialID }) {
            CredentialDetailsScreen(details: item)
        } else {
            Text("Credential details unavailable")
                .foregroundStyle(.secondary)
                .navigationTitle("Credential")
        }
    }
}
