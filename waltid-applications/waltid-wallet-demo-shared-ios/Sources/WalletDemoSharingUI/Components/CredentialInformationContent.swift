import SwiftUI

/// Actual values use one identity row and push technical information within their current sheet.
public struct CredentialInformationContent: View {
    public let details: CredentialDetails
    private let onDismiss: () -> Void
    @State private var technicalOpen = false

    public init(details: CredentialDetails, onDismiss: @escaping () -> Void) {
        self.details = details
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            CredentialSummaryRow(summary: details.cardSummary)
            CredentialDetailsBody(details: details, onTechnicalDetails: { technicalOpen = true })
        }
        .walletDetailDestination(isPresented: $technicalOpen) {
            WalletDetailPage(String(localized: "Technical details", bundle: .module), onDismiss: onDismiss) {
                CredentialTechnicalInformation(details: details)
            }
        }
    }
}
