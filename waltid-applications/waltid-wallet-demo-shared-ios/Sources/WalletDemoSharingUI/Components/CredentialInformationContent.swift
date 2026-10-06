import SwiftUI

/// Actual values use one identity row and push technical information within their current sheet.
public struct CredentialInformationContent: View {
    public let details: CredentialDetails
    @State private var technicalOpen = false

    public init(details: CredentialDetails) {
        self.details = details
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            CredentialSummaryRow(summary: details.cardSummary, showsIssuer: false)
            CredentialDetailsBody(details: details, onTechnicalDetails: { technicalOpen = true })
        }
        .walletDetailDestination(isPresented: $technicalOpen) {
            WalletDetailPage(String(localized: "Technical details", bundle: .module)) {
                CredentialTechnicalInformation(details: details)
            }
        }
    }
}
