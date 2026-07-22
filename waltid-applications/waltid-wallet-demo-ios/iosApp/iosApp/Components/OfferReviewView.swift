import SwiftUI
import WalletSDK

struct OfferReviewView: View {
    let preview: OfferResolution
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
                    display: preview.issuer.display,
                    fallbackName: preview.issuer.credentialIssuer,
                    supportingText: preview.issuer.display?.name == preview.issuer.credentialIssuer
                        ? nil
                        : preview.issuer.credentialIssuer
                )
            }

            if !preview.offeredCredentials.isEmpty {
                ReviewMetadataSection(
                    title: "Offered credentials",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerCredentialsSection
                ) {
                    ForEach(Array(preview.offeredCredentials.enumerated()), id: \.offset) { index, credential in
                        if index > 0 {
                            Divider()
                        }
                        OfferedCredentialView(credential: credential)
                    }
                }
            }

            if let requirement = preview.transactionCode {
                ReviewMetadataSection(
                    title: "Transaction code",
                    titleAccessibilityIdentifier: WalletAccessibilityID.offerTransactionCodeSection
                ) {
                    Text(requirement.description ?? "Enter the transaction code provided by the issuer.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField(
                        "Code",
                        text: Binding(get: { txCode }, set: onTxCodeChange)
                    )
                    .textContentType(.oneTimeCode)
                    .keyboardType(requirement.inputMode == .numeric ? .numberPad : .asciiCapable)
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
                Button("Accept", action: onAccept)
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
}

private struct OfferedCredentialView: View {
    let credential: OfferedCredentialMetadata

    private var title: String {
        credential.display?.name ?? credential.vct ?? credential.doctype ?? credential.configurationID
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            MetadataIdentityView(
                display: credential.display,
                fallbackName: title,
                supportingText: credential.display?.description
            )
            let details = [
                MetadataDetailItem(label: "Format", value: credential.format),
                MetadataDetailItem(label: "Type", value: credential.vct ?? credential.doctype),
            ].filter(\.isVisible)
            if !details.isEmpty {
                Divider()
                MetadataDetailList(items: details)
            }
            if !credential.claims.isEmpty {
                Divider()
                MetadataDisclosure(
                    title: "Supported claims (\(credential.claims.count))",
                    initiallyExpanded: false,
                    accessibilityIdentifier: WalletAccessibilityID.offerSupportedClaims
                ) {
                    ForEach(Array(credential.claimDisplayGroups.enumerated()), id: \.offset) { groupIndex, group in
                        if groupIndex > 0 {
                            Divider()
                        }
                        Text(group.title)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.tint)
                        ForEach(Array(group.claims.enumerated()), id: \.offset) { index, claim in
                            if index > 0 {
                                Divider()
                            }
                            VStack(alignment: .leading, spacing: 1) {
                                Text(claim.label).font(.caption)
                                Text(claim.inclusion)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
