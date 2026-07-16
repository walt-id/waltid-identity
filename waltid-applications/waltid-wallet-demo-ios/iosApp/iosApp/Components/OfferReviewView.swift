import SwiftUI

struct OfferPreview {
    let credentialIssuer: String
    let offeredCredentials: [String]
    let transactionCodeRequired: Bool
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

            VStack(alignment: .leading, spacing: 8) {
                Text("Issuer")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(preview.credentialIssuer.isEmpty ? "Unknown issuer" : preview.credentialIssuer)
                    .font(.subheadline)

                if !preview.offeredCredentials.isEmpty {
                    Text("Offered credentials")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.top, 4)
                    ForEach(preview.offeredCredentials, id: \.self) { credentialType in
                        Text(credentialType)
                            .font(.caption)
                    }
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 8))

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
