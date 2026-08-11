import SwiftUI
import WalletDemoSharingUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails

    var body: some View {
        ScrollView {
            CredentialDetailsView(details: details)
                .padding()
        }
        .navigationTitle("Credential details")
        .navigationBarTitleDisplayMode(.inline)
    }
}
