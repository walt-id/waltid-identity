import SwiftUI

public struct CredentialDetailsView: View {
    public let details: CredentialDetails
    @State private var technicalOpen = false

    public init(details: CredentialDetails) {
        self.details = details
    }

    public var body: some View {
        let technicalGroups = details.groups.filter { $0.id == "technical" } + [details.systemInfoGroup].compactMap { $0 }

        VStack(alignment: .leading, spacing: 12) {
            CredentialOverviewView(details: details)

            if details.groups.isEmpty && technicalGroups.isEmpty {
                Text("No credential details available")
                     .font(.body)
                    .foregroundStyle(.secondary)
            }

            ForEach(details.groups.filter { $0.id != "technical" && $0.id != "requested" }) { group in
                ClaimGroupView(group: group)
            }

            if !technicalGroups.isEmpty {
                WalletSection {
                    WalletNavigationRow(String(localized: "Technical details", bundle: .module)) { technicalOpen = true }
                        .accessibilityIdentifier("credential-technical-details")
                }
            }
        }
        .sheet(isPresented: $technicalOpen) {
            WalletDetailSheet(String(localized: "Technical details", bundle: .module), onDismiss: { technicalOpen = false }) {
                ForEach(technicalGroups) { group in ClaimGroupView(group: group, collapsible: false) }
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
                     .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier(WalletAccessibilityID.credentialOverview(details.id))
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}
