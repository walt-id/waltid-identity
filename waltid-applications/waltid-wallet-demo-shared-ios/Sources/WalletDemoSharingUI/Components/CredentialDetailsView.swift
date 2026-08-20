import SwiftUI
import WalletSDK

public struct CredentialDetailsView: View {
    public let details: CredentialDetails

    public init(details: CredentialDetails) {
        self.details = details
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CredentialOverviewView(details: details)
            CredentialInformationView(details: details)
        }
    }
}

/// Credential fields without the repeated card/issuer summary, for embedding in an information island.
public struct CredentialInformationView: View {
    public let details: CredentialDetails

    public init(details: CredentialDetails) {
        self.details = details
    }

    public var body: some View {
        let systemInfoGroup = details.systemInfoGroup

        VStack(alignment: .leading, spacing: 12) {
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

    private var summary: CredentialCardSummary {
        details.cardSummary
    }

    private var issuerFallback: String {
        summary.issuer
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                CredentialPortraitView(summary: summary, size: 64)

                VStack(alignment: .leading, spacing: 5) {
                    Text(summary.title)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)

                    if let credentialType = summary.credentialType {
                        Text(credentialType)
                            .font(.caption)
                            .foregroundStyle(.tint)
                    }

                    if let holderName = summary.holderName {
                        Text(holderName)
                            .font(.caption)
                    }

                    HStack(spacing: 8) {
                        if let validityText = summary.validityText {
                            Text(validityText)
                        }
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
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
