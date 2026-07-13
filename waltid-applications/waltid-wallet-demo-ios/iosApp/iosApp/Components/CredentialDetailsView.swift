import SwiftUI

struct CredentialDetailsView: View {
    let details: CredentialDetails

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Credential details")
                .font(.subheadline.weight(.semibold))
                .accessibilityIdentifier(WalletAccessibilityID.credentialDetails(details.id))

            CredentialOverviewView(details: details)

            if details.groups.isEmpty {
                Text("No credential details available")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach(details.groups) { group in
                ClaimGroupView(group: group)
            }
        }
    }
}

private struct CredentialOverviewView: View {
    let details: CredentialDetails

    private var summary: CredentialCardSummary {
        details.cardSummary
    }

    var body: some View {
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

                Text(details.issuer ?? "Unknown issuer")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    Text(details.format)
                    if let validityText = summary.validityText {
                        Text(validityText)
                    }
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier(WalletAccessibilityID.credentialOverview(details.id))
    }
}
