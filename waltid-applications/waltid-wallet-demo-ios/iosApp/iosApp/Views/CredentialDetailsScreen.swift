import SwiftUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails

    var body: some View {
        ScrollView {
            CredentialDetailsView(details: details, includeTechnicalDetails: true)
                .padding()
        }
        .navigationTitle("Credential details")
        .navigationBarTitleDisplayMode(.inline)
    }
}
