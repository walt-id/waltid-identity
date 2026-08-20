import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct OfferReviewView: View {
    let preview: IssuanceOfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let txCode: String
    let showsActions: Bool
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void

    init(
        preview: IssuanceOfferPreview,
        isAcceptEnabled: Bool,
        isReviewEnabled: Bool,
        txCode: String,
        showsActions: Bool = true,
        onTxCodeChange: @escaping (String) -> Void,
        onAccept: @escaping () -> Void,
        onDecline: @escaping () -> Void
    ) {
        self.preview = preview
        self.isAcceptEnabled = isAcceptEnabled
        self.isReviewEnabled = isReviewEnabled
        self.txCode = txCode
        self.showsActions = showsActions
        self.onTxCodeChange = onTxCodeChange
        self.onAccept = onAccept
        self.onDecline = onDecline
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Add credential")
                .font(.headline)

            ReviewIslandNavigationView(islands: preview.reviewIslands, expandedContent: { island in
                if island.kind == .requiredAction, let requirement = preview.transactionCode {
                    transactionCodeInput(requirement)
                }
            })

            if showsActions {
                OfferReviewActions(
                    preview: preview,
                    isAcceptEnabled: isAcceptEnabled,
                    isReviewEnabled: isReviewEnabled,
                    onAccept: onAccept,
                    onDecline: onDecline
                )
            }
        }
    }

    private func transactionCodeInput(_ requirement: IssuanceTransactionCode) -> some View {
        VStack(alignment: .leading, spacing: 8) {
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
}

struct OfferReviewActions: View {
    let preview: IssuanceOfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Button(action: onAccept) {
                Text(preview.grant == .authorizationCode ? "Continue" : "Add credential")
                    .frame(maxWidth: .infinity)
            }
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

private extension IssuanceOfferPreview {
    var reviewIslands: [ReviewIsland] {
        var islands = [issuerReviewIsland]
        if let credentialReviewIsland {
            islands.append(credentialReviewIsland)
        }
        if let requiredActionReviewIsland {
            islands.append(requiredActionReviewIsland)
        }
        return islands
    }

    var issuerReviewIsland: ReviewIsland {
        let title = issuer.name?.presentableValue ?? issuer.identifier
        return ReviewIsland(
            id: "issuer",
            kind: .issuer,
            context: .offered,
            title: title,
            subtitle: "Issuer",
            visual: ReviewIslandVisual(
                imageURI: issuer.logoURI?.absoluteString,
                contentDescription: issuer.logoAltText,
                fallbackText: title.first.map { String($0).uppercased() } ?? "I"
            ),
            technicalSections: [
                ReviewTechnicalSection(
                    id: "issuer-identity",
                    title: "Issuer identity",
                    values: [
                        ReviewValue(
                            label: "Credential Issuer",
                            value: issuer.identifier,
                            linkURI: issuer.identifier
                        ),
                        ReviewValue(label: "Selected display name", value: issuer.name),
                        ReviewValue(label: "Selected locale", value: issuer.locale),
                        ReviewValue(label: "Logo source", value: issuer.logoURI?.absoluteString),
                    ]
                ),
            ]
        )
    }

    var credentialReviewIsland: ReviewIsland? {
        guard let first = credentials.first else { return nil }
        let title: String
        if credentials.count == 1 {
            title = first.name?.presentableValue ?? "Credential"
        } else {
            title = "\(credentials.count) credentials"
        }
        return ReviewIsland(
            id: "credential",
            kind: .credential,
            context: .offered,
            title: title,
            subtitle: credentials.count == 1 ? "Offered credential" : "Offered credentials",
            visual: ReviewIslandVisual(
                imageURI: first.logoURI?.absoluteString,
                contentDescription: first.logoAltText,
                fallbackText: title.first.map { String($0).uppercased() } ?? "C"
            ),
            expandedValues: credentials.map {
                ReviewValue(
                    label: $0.name?.presentableValue ?? "Credential",
                    value: $0.descriptionText?.presentableValue ?? "Ready to add"
                )
            },
            technicalSections: credentials.enumerated().map { index, credential in
                ReviewTechnicalSection(
                    id: "credential-\(index)",
                    title: credential.name?.presentableValue ?? "Credential",
                    values: [
                        ReviewValue(label: "Configuration identifier", value: credential.configurationID),
                        ReviewValue(label: "Format", value: credential.format),
                        ReviewValue(label: "Logo source", value: credential.logoURI?.absoluteString),
                    ]
                )
            },
            initiallyExpanded: credentials.count > 1
        )
    }

    var requiredActionReviewIsland: ReviewIsland? {
        guard grant == .authorizationCode || transactionCode != nil else { return nil }
        let title = transactionCode == nil ? "Issuer sign-in" : "Transaction code"
        let subtitle = transactionCode?.descriptionText?.presentableValue
            ?? (grant == .authorizationCode
                ? "Continue securely in your browser"
                : "Enter the code provided by the Issuer")
        return ReviewIsland(
            id: "required-action",
            kind: .requiredAction,
            context: .offered,
            title: title,
            subtitle: subtitle,
            visual: ReviewIslandVisual(fallbackText: "→"),
            expandedValues: grant == .authorizationCode
                ? [ReviewValue(
                    label: "Next step",
                    value: "Continuing opens your browser to sign in with the Issuer before the credential is added."
                )]
                : [],
            technicalSections: [
                ReviewTechnicalSection(
                    id: "authorization-method",
                    title: "Authorization method",
                    values: [
                        ReviewValue(
                            label: "Grant",
                            value: grant == .authorizationCode ? "Authorization code" : "Pre-authorized code"
                        ),
                        ReviewValue(label: "Transaction code input", value: transactionCode?.inputMode),
                        ReviewValue(label: "Expected length", value: transactionCode?.length.map { String($0) }),
                    ]
                ),
            ],
            initiallyExpanded: true
        )
    }
}

private extension String {
    var presentableValue: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
