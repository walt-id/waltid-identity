import SwiftUI
import WalletSDK

public struct CredentialDetailsView: View {
    public let details: CredentialDetails
    public var onCardTap: (() -> Void)?
    public var showCard: Bool = true

    public init(details: CredentialDetails, onCardTap: (() -> Void)? = nil, showCard: Bool = true) {
        self.details = details
        self.onCardTap = onCardTap
        self.showCard = showCard
    }

    public var body: some View {
        let systemInfoGroup = details.systemInfoGroup

        VStack(alignment: .leading, spacing: 12) {
            CredentialOverviewView(details: details, onCardTap: onCardTap, showCard: showCard)

            if details.groups.isEmpty && systemInfoGroup == nil {
                Text("No credential details available")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach(details.groups) { group in
                ClaimGroupView(group: group)
            }

            if let systemInfoGroup {
                ClaimGroupView(group: systemInfoGroup)
            }
        }
    }
}

private struct CredentialOverviewView: View {
    let details: CredentialDetails
    var onCardTap: (() -> Void)?
    var showCard: Bool = true

    private var summary: CredentialCardSummary {
        details.cardSummary
    }

    private var issuerFallback: String {
        summary.issuer
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if showCard {
                CredentialCardView(details: details, compact: true)
                    .contentShape(Rectangle())
                    .onTapGesture { onCardTap?() }
            }

            if let issuerDisplay = details.issuerDisplay {
                let supporting = details.issuer?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .nonEmpty
                    .flatMap { issuer in
                        issuer == issuerDisplay.name ? nil : issuer
                    }
                MetadataIdentityView(
                    display: issuerDisplay,
                    fallbackName: issuerFallback,
                    supportingText: supporting
                )
            } else {
                Text("Issuer: \(issuerFallback)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier(WalletAccessibilityID.credentialOverview(details.id))
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}
