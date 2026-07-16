import SwiftUI

struct OfferPreview {
    let credentialIssuer: String
    let offeredCredentials: [String]
    let transactionCodeRequired: Bool
    let issuerMetadataJSON: String?

    var offeredCredentialDetails: [CredentialDetails] {
        offeredCredentials.enumerated().map { index, offeredCredential in
            CredentialDetails(
                id: "offer-preview-\(index)",
                title: CredentialDisplayVocabulary.readableCredentialType(offeredCredential)
                    ?? (offeredCredential.isEmpty ? "Credential" : offeredCredential),
                issuer: nil,
                subject: nil,
                format: "Credential offer",
                addedAt: nil,
                groups: []
            )
        }
    }
}

struct OfferReviewView: View {
    let preview: OfferPreview
    let isEnabled: Bool
    let isTxCodeEnabled: Bool
    let txCode: String
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Credential offer")
                .font(.headline)

            MetadataIdentityCardView(
                identity: CredentialDisplayNormalizer.metadataIdentity(
                    title: "Issuer",
                    rawJSON: preview.issuerMetadataJSON,
                    fallbackName: preview.credentialIssuer,
                    fallbackSubtitle: "Credential issuer"
                )
            )

            if !preview.offeredCredentialDetails.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Credential preview")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ForEach(preview.offeredCredentialDetails) { details in
                        CredentialCardView(details: details)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if preview.transactionCodeRequired {
                VStack(alignment: .leading, spacing: 6) {
                    Text("This offer requires a transaction code.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField(
                        "Transaction code",
                        text: Binding(
                            get: { txCode },
                            set: onTxCodeChange
                        )
                    )
                    .textContentType(.oneTimeCode)
                    .keyboardType(.asciiCapable)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .padding(8)
                    .frame(minHeight: 52)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(.separator), lineWidth: 1)
                    )
                    .disabled(!isTxCodeEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.txCodeInput)
                }
            }

            HStack(spacing: 8) {
                Button("Accept", action: onAccept)
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                    .disabled(!isEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerAcceptButton)

                Button("Decline", action: onDecline)
                    .buttonStyle(.bordered)
                    .disabled(!isEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerDeclineButton)
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.offerReview)
    }
}
