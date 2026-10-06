import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct OfferReviewView: View {
    @Environment(\.walletDemoBranding) private var branding
    let preview: IssuanceOfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let copies: [String: Int]
    let onCopiesChange: (String, Int) -> Void
    let txCode: String
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void
    var showActions: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            IssuanceIssuerSection(issuer: preview.issuer)

            if !preview.credentials.isEmpty {
                WalletSection("Offered credentials", titleIdentifier: WalletAccessibilityID.offerCredentialsSection) {
                    VStack(spacing: 0) {
                        ForEach(preview.credentials, id: \.configurationID) { credential in
                            OfferedCredentialRow(credential: credential,
                                issuerName: preview.issuer.name ?? preview.issuer.identifier,
                                issuerIdentifier: preview.issuer.identifier,
                                copies: Binding(get: { copies[credential.configurationID] ?? 1 },
                                                set: { onCopiesChange(credential.configurationID, $0) }),
                                limit: preview.batchSize ?? 1, enabled: isReviewEnabled, largeArt: preview.credentials.count == 1)
                            if credential.configurationID != preview.credentials.last?.configurationID { Divider() }
                        }
                    }
                }
                Text(selectionSummary).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
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

    private var selectionSummary: String {
        let selected = preview.credentials.map { copies[$0.configurationID] ?? 1 }.filter { $0 > 0 }
        return selected.isEmpty ? String(localized: "Select at least one credential to continue.")
            : String(format: String(localized: "Selected: %d · Copies: %d"), selected.count, selected.reduce(0, +))
    }


}

struct OfferReviewActions: View {
    let requiresIssuerAuthentication: Bool
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        WalletActions(
            primary: WalletAction(requiresIssuerAuthentication ? "Continue to sign in" : "Accept",
                enabled: isAcceptEnabled, identifier: WalletAccessibilityID.offerAcceptButton, perform: onAccept),
            secondary: WalletAction("Decline", enabled: isReviewEnabled,
                identifier: WalletAccessibilityID.offerDeclineButton, perform: onDecline)
        )
    }
}
