import SwiftUI

public struct ReviewCredentialInformation: View {
    let details: CredentialDetails
    let claimStatus: (ClaimItem) -> String?
    let onDismiss: () -> Void
    @Environment(\.walletReviewNavigationAvailable) private var hasNavigation
    @State private var technicalOpen = false

    public init(details: CredentialDetails, claimStatus: @escaping (ClaimItem) -> String? = { _ in nil },
                onDismiss: @escaping () -> Void) {
        self.details = details; self.claimStatus = claimStatus; self.onDismiss = onDismiss
    }

    public var body: some View {
        Group {
            if hasNavigation { WalletDetailPage(String(localized: "Credential information", bundle: .module)) { content } }
            else { WalletDetailSheet(String(localized: "Credential information", bundle: .module), onDismiss: onDismiss) { content } }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsDialog)
        .walletDetailDestination(isPresented: $technicalOpen) {
            WalletDetailPage(String(localized: "Technical details", bundle: .module)) { CredentialTechnicalInformation(details: details) }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 16) {
            CredentialSummaryRow(summary: details.cardSummary)
            CredentialDetailsBody(details: details, onTechnicalDetails: { technicalOpen = true }, claimStatus: claimStatus)
        }
    }
}
