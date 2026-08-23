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
    let route: Binding<ReviewRoute>?
    let showsTechnicalHeader: Bool

    init(
        preview: IssuanceOfferPreview,
        isAcceptEnabled: Bool,
        isReviewEnabled: Bool,
        txCode: String,
        showsActions: Bool = true,
        route: Binding<ReviewRoute>? = nil,
        showsTechnicalHeader: Bool = true,
        onTxCodeChange: @escaping (String) -> Void,
        onAccept: @escaping () -> Void,
        onDecline: @escaping () -> Void
    ) {
        self.preview = preview
        self.isAcceptEnabled = isAcceptEnabled
        self.isReviewEnabled = isReviewEnabled
        self.txCode = txCode
        self.showsActions = showsActions
        self.route = route
        self.showsTechnicalHeader = showsTechnicalHeader
        self.onTxCodeChange = onTxCodeChange
        self.onAccept = onAccept
        self.onDecline = onDecline
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ReviewIslandNavigationView(
                islands: preview.reviewIslands,
                showsModelExpandedValues: { _ in true },
                hasCustomExpandedContent: { island in
                    preview.credential(forReviewIslandID: island.id)?.claims.isEmpty == false ||
                        (island.kind == .requiredAction && preview.transactionCode != nil)
                },
                route: route,
                showsTechnicalHeader: showsTechnicalHeader
            ) { island in
                if island.kind == .credential,
                   let credential = preview.credential(forReviewIslandID: island.id) {
                    OfferedCredentialInformationContent(credential: credential)
                } else if island.kind == .requiredAction, let requirement = preview.transactionCode {
                    transactionCodeInput(requirement)
                }
            }

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

private struct OfferedCredentialInformationContent: View {
    let credential: IssuanceCredentialPreview

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(credential.claimDisplayGroups.enumerated()), id: \.offset) { groupIndex, group in
                if groupIndex > 0 { Divider() }
                Text(group.title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tint)
                ForEach(Array(group.claims.enumerated()), id: \.offset) { _, claim in
                    VStack(alignment: .leading, spacing: 1) {
                        Text(claim.label).font(.caption)
                        Text(claim.inclusion).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}

private struct IssuanceClaimDisplayGroup {
    let title: String
    let claims: [IssuanceClaimDisplay]
}

private struct IssuanceClaimDisplay {
    let label: String
    let inclusion: String
}

private extension IssuanceCredentialPreview {
    var claimDisplayGroups: [IssuanceClaimDisplayGroup] {
        let entries = claims.enumerated().map { index, claim in
            let semantics = MdocClaimDisplaySemantics.describe(format: format, path: claim.path)
            return (
                group: semantics?.group.rawValue,
                groupOrder: semantics?.group.order ?? 0,
                claimOrder: semantics?.sortOrder ?? Int.max,
                sourceOrder: index,
                display: IssuanceClaimDisplay(
                    label: claim.displayName?.presentableValue
                        ?? semantics?.label
                        ?? claim.path.last.map(CredentialDisplayVocabulary.humanizedLabel)
                        ?? "Field",
                    inclusion: claim.mandatory == true ? "Always included" : "May be included"
                )
            )
        }
        let grouped = Dictionary(grouping: entries) { $0.group }
        return grouped.map { group, entries in
            IssuanceClaimDisplayGroup(
                title: group ?? "Credential claims",
                claims: entries.sorted {
                    ($0.claimOrder, $0.sourceOrder) < ($1.claimOrder, $1.sourceOrder)
                }.map(\.display)
            )
        }.sorted { lhs, rhs in
            let lhsOrder = entries.first { ($0.group ?? "Credential claims") == lhs.title }?.groupOrder ?? 0
            let rhsOrder = entries.first { ($0.group ?? "Credential claims") == rhs.title }?.groupOrder ?? 0
            return lhsOrder < rhsOrder
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
        HStack(spacing: 8) {
            Button(action: onAccept) {
                Text(preview.grant == .authorizationCode ? "Continue" : "Add credential")
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(WalletPrimaryButtonStyle())
            .frame(maxWidth: .infinity)
            .disabled(!isAcceptEnabled)
            .accessibilityIdentifier(WalletAccessibilityID.offerAcceptButton)

            Button(action: onDecline) {
                Text("Decline")
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(WalletSecondaryButtonStyle())
            .frame(maxWidth: .infinity)
            .disabled(!isReviewEnabled)
            .accessibilityIdentifier(WalletAccessibilityID.offerDeclineButton)
        }
        .frame(height: 48)
        .frame(maxWidth: .infinity)
    }
}

extension IssuanceOfferPreview {
    var reviewIslands: [ReviewIsland] {
        var islands = [issuerReviewIsland]
        islands.append(contentsOf: credentialReviewIslands)
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
            subtitle: "Credential Issuer",
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

    var credentialReviewIslands: [ReviewIsland] {
        credentials.enumerated().map { index, credential in
            let title = credential.name?.presentableValue ?? "Credential"
            return ReviewIsland(
                id: credential.offerReviewIslandID(index: index),
                kind: .credential,
                context: .offered,
                title: title,
                 subtitle: "Offered credential",
                 visual: ReviewIslandVisual(
                     imageURI: credential.logoURI?.absoluteString,
                     contentDescription: credential.logoAltText,
                     fallbackText: title.first.map { String($0).uppercased() } ?? "C"
                 ),
                expandedValues: [ReviewValue(label: "Description", value: credential.descriptionText)],
                technicalSections: [
                    ReviewTechnicalSection(
                        id: "credential-identity",
                        title: "Credential identity",
                        values: [
                            ReviewValue(label: "Configuration identifier", value: credential.configurationID),
                            ReviewValue(label: "Format", value: credential.format),
                            ReviewValue(label: "Logo source", value: credential.logoURI?.absoluteString),
                        ]
                    ),
                    ReviewTechnicalSection(
                        id: "credential-information",
                        title: "Credential information",
                        values: credential.claims.flatMap { claim in
                            [
                                ReviewValue(
                                    label: claim.displayName?.presentableValue ?? claim.path.last ?? "Field",
                                    value: claim.path.joined(separator: ".")
                                ),
                                ReviewValue(
                                    label: "Inclusion",
                                    value: claim.mandatory == true ? "Always included" : "May be included"
                                ),
                            ]
                        }
                    ),
                ],
                initiallyExpanded: true
            )
        }
    }

    func credential(forReviewIslandID islandID: String) -> IssuanceCredentialPreview? {
        credentials.enumerated().first { index, credential in
            credential.offerReviewIslandID(index: index) == islandID
        }?.element
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
            visual: nil,
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

private extension IssuanceCredentialPreview {
    func offerReviewIslandID(index: Int) -> String { "credential-offered-\(index)" }
}

private extension String {
    var presentableValue: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
