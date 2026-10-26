import SwiftUI

/// Credential content pushes technical information within its existing navigation owner.
public struct CredentialDetailsView: View {
    public let details: CredentialDetails
    @State private var technicalOpen = false

    public init(details: CredentialDetails) { self.details = details }

    public var body: some View {
        CredentialDetailsBody(details: details, onTechnicalDetails: { technicalOpen = true })
            .walletDetailDestination(isPresented: $technicalOpen) {
                WalletDetailPage(String(localized: "Technical details", bundle: .module)) {
                    CredentialTechnicalInformation(details: details)
                }
            }
    }
}
