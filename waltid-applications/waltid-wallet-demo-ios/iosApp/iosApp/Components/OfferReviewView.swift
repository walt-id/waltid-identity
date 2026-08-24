import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct OfferReviewView: View {
    @Environment(\.walletDemoBranding) private var branding
    let preview: IssuanceOfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let txCode: String
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Credential offer")
                .font(.headline)

            ReviewMetadataSection(
                title: "Issuer",
                titleAccessibilityIdentifier: WalletAccessibilityID.offerIssuerSection
            ) {
                MetadataIdentityView(
                    display: issuerDisplay,
                    fallbackName: preview.issuer.identifier,
                    supportingText: nil
                )
                if issuerHasFriendlyName {
                    Divider()
                    MetadataDisclosure(
                        title: "Issuer details",
                        initiallyExpanded: false,
                        accessibilityIdentifier: WalletAccessibilityID.offerIssuerDetailsToggle
                    ) {
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
            }

            if !preview.credentials.isEmpty {
                ReviewMetadataSection(
                    title: "Offered credentials",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerCredentialsSection
                ) {
                    ForEach(Array(preview.credentials.enumerated()), id: \.offset) { index, credential in
                        if index > 0 {
                            Divider()
                        }
                        OfferedCredentialView(credential: credential)
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

            HStack(spacing: 8) {
                Button(preview.grant == .authorizationCode ? "Continue to sign in" : "Accept", action: onAccept)
                    .buttonStyle(.borderedProminent)
                    .tint(branding.primary)
                    .disabled(!isAcceptEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerAcceptButton)

                Button("Decline", action: onDecline)
                    .buttonStyle(.bordered)
                    .disabled(!isReviewEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerDeclineButton)
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

private struct OfferedCredentialView: View {
    let credential: IssuanceCredentialPreview

    private var title: String {
        credential.name ?? credential.configurationID
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            MetadataIdentityView(
                display: MetadataDisplay(
                    name: credential.name,
                    locale: nil,
                    logoURI: credential.logoURI?.absoluteString,
                    logoAltText: credential.logoAltText
                ),
                fallbackName: title,
                supportingText: credential.descriptionText
            )
            let details = [
                MetadataDetailItem(label: "Format", value: credential.format),
            ].filter(\.isVisible)
            if !details.isEmpty {
                Divider()
                MetadataDetailList(items: details)
            }
        }
    }
}
