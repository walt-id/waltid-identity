import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct OfferReviewView: View {
    let preview: IssuanceOfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let txCode: String
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void
    var showActions: Bool = true
    @State private var issuerExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Credential offer")
                .font(.headline)

            ExpandableMetadataCard(
                title: "Issuer",
                titleAccessibilityIdentifier: WalletAccessibilityID.offerIssuerSection,
                toggleAccessibilityIdentifier: WalletAccessibilityID.offerIssuerDetailsToggle,
                isExpanded: $issuerExpanded
            ) {
                MetadataIdentityView(
                    display: issuerDisplay,
                    fallbackName: preview.issuer.identifier,
                    supportingText: nil
                )
            } details: {
                if issuerHasFriendlyName {
                    MetadataDetailList(items: [
                        MetadataDetailItem(
                            label: "Credential Issuer",
                            value: preview.issuer.identifier,
                            linkURI: preview.issuer.identifier
                        ),
                    ])
                    .accessibilityIdentifier(WalletAccessibilityID.offerIssuerDetails)
                }
            }

            if !preview.credentials.isEmpty {
                ReviewMetadataSection(
                    title: "Offered credentials",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerCredentialsSection
                ) {
                    ForEach(preview.credentials, id: \.configurationID) { credential in
                        CredentialCardArtView(summary: credential.cardSummary)
                    }
                }
            }

            if preview.grant == .authorizationCode {
                ReviewMetadataSection(
                    title: "Issuer sign-in",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerAuthorizationSection
                ) {
                    Text("Continuing opens your browser to sign in with the issuer before the credential is issued.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if let requirement = preview.transactionCode {
                ReviewMetadataSection(
                    title: "Transaction code",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerTransactionCodeSection
                ) {
                    Text(requirement.descriptionText ?? "Enter the transaction code provided by the issuer.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField(
                        "Code",
                        text: Binding(get: { txCode }, set: onTxCodeChange)
                    )
                    .textContentType(.oneTimeCode)
                    .keyboardType(requirement.inputMode?.lowercased() == "numeric" ? .numberPad : .asciiCapable)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .padding(8)
                    .frame(minHeight: 52)
                    .background(
                        isReviewEnabled ? Color(.systemBackground) : Color(.secondarySystemFill),
                        in: RoundedRectangle(cornerRadius: 8)
                    )
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(.separator), lineWidth: 1))
                    .disabled(!isReviewEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.txCodeInput)

                    if let length = requirement.length {
                        Text("\(length) characters")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            if showActions {
                OfferReviewActions(
                    requiresIssuerAuthentication: preview.grant == .authorizationCode,
                    isAcceptEnabled: isAcceptEnabled,
                    isReviewEnabled: isReviewEnabled,
                    onAccept: onAccept,
                    onDecline: onDecline
                )
            }
        }
    }

    private var issuerDisplay: MetadataDisplay? {
        MetadataDisplay(
            name: preview.issuer.name,
            locale: preview.issuer.locale,
            logoURI: preview.issuer.logoURI?.absoluteString,
            logoAltText: preview.issuer.logoAltText
        )
    }

    private var issuerHasFriendlyName: Bool {
        guard let name = preview.issuer.name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
            return false
        }
        return name != preview.issuer.identifier
    }
}

struct OfferReviewActions: View {
    let requiresIssuerAuthentication: Bool
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(requiresIssuerAuthentication ? "Continue to sign in" : "Accept", action: onAccept)
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(!isAcceptEnabled)
                .accessibilityIdentifier(WalletAccessibilityID.offerAcceptButton)

            Button("Decline", action: onDecline)
                .buttonStyle(.bordered)
                .disabled(!isReviewEnabled)
                .accessibilityIdentifier(WalletAccessibilityID.offerDeclineButton)
        }
    }
}

private extension IssuanceCredentialPreview {
    var cardSummary: CredentialCardSummary {
        .offered(from: self)
    }
}
