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

            ReviewMetadataSection(title: "Issuer") {
                MetadataIdentityView(
                    display: preview.issuer.display,
                    fallbackName: preview.issuer.credentialIssuer,
                    supportingText: preview.issuer.display?.name == preview.issuer.credentialIssuer
                        ? nil
                        : preview.issuer.credentialIssuer
                )
            }
            .accessibilityIdentifier(WalletAccessibilityID.offerIssuerSection)

            if !preview.offeredCredentials.isEmpty {
                ReviewMetadataSection(title: "Offered credentials") {
                    ForEach(Array(preview.offeredCredentials.enumerated()), id: \.offset) { index, credential in
                        if index > 0 {
                            Divider()
                        }
                        OfferedCredentialView(credential: credential)
                    }
                }
                .accessibilityIdentifier(WalletAccessibilityID.offerCredentialsSection)
            }

            if let requirement = preview.transactionCode {
                ReviewMetadataSection(title: "Transaction code") {
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
                .accessibilityIdentifier(WalletAccessibilityID.offerTransactionCodeSection)
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
                Text("Claims")
                    .font(.caption.weight(.medium))
                ForEach(Array(credential.claims.enumerated()), id: \.offset) { index, claim in
                    if index > 0 {
                        Divider()
                    }
                    let fallback = claim.path.joined(separator: ".")
                    let trimmedName = claim.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let name = trimmedName.flatMap { value in value.isEmpty ? nil : value } ?? fallback
                    Text(claim.mandatory == true ? "\(name) (required)" : name)
                        .font(.caption)
                }
            }
        }
    }
}
