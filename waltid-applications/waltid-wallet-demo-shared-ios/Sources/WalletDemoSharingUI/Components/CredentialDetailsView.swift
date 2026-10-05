import SwiftUI

/// Standalone credential content presents its one technical-information destination.
public struct CredentialDetailsView: View {
    public let details: CredentialDetails
    @State private var technicalOpen = false

    public init(details: CredentialDetails) { self.details = details }

    public var body: some View {
        CredentialDetailsBody(details: details, onTechnicalDetails: { technicalOpen = true })
            .sheet(isPresented: $technicalOpen) {
                WalletDetailSheet(String(localized: "Technical details", bundle: .module), onDismiss: { technicalOpen = false }) {
                    CredentialTechnicalInformation(details: details)
                }
            }
    }
}
