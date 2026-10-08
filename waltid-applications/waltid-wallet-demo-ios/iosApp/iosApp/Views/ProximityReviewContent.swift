import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityReviewContent: View {
    let review: ProximityReview
    let selections: [ProximityDocumentSelection]
    let credentialDetailsByID: [String: CredentialDetails]
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void
    let continueAfterResponse: Bool
    let onContinueAfterResponseChange: (Bool) -> Void
    let allowContinuation: Bool
    var enabled: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ProximityReaderMetadataCard(
                authentications: review.readerAuthentication,
                summary: review.readerAuthenticationSummary,
                documents: review.documents,
                credentialDetailsByID: credentialDetailsByID
            )

            ForEach(review.useCases) { useCase in
                ReviewMetadataSection(title: String(localized: "Reader-stated purpose")) {
                    Text("Use case \(useCase.index + 1)\(useCase.mandatory ? " (mandatory)" : "")")
                        .font(.headline)
                    if useCase.purposeHints.isEmpty {
                        Text("No purpose hint was supplied.")
                    } else {
                        ForEach(Array(useCase.purposeHints.enumerated()), id: \.offset) { _, hint in
                            Text("\(hint.type): \(hint.code) (claimed by reader)")
                        }
                    }
                }
            }

            ForEach(review.applicationAuthorizations, id: \.profileID) { authorization in
                ReviewMetadataSection(title: authorization.displayTitle) {
                    Text("Validated application request")
                        .font(.headline)
                    MetadataDetailList(
                        items: authorization.details.map {
                            MetadataDetailItem(label: $0.label, value: $0.value)
                        }
                    )
                }
            }

            WalletSection("Select credentials to share") {
                VStack(spacing: 0) {
                    ForEach(review.documents) { document in
                        ForEach(Array(document.credentialOptions.enumerated()), id: \.element.id) { index, credential in
                            if index > 0 { Divider() }
                            ReviewCredentialChoice(
                                selected: selections.first(where: { $0.requestIndex == document.requestIndex })?.credentialID == credential.credentialID,
                                enabled: enabled, onSelect: { onSelectCredential(document.requestIndex, credential.credentialID) }) {
                                if let details = credentialDetailsByID[credential.credentialID] {
                                    CredentialSummaryRow(summary: details.cardSummary)
                                } else {
                                    Text(credential.label ?? String(localized: "Wallet credential"))
                                    if let issuer = credential.issuer { Text(issuer).font(.footnote).foregroundStyle(.secondary) }
                                    Text("Valid until \(credential.validUntil.formatted(date: .abbreviated, time: .omitted))").font(.footnote)
                                }
                            }.accessibilityIdentifier(WalletAccessibilityID.proximityCredential(
                                requestIndex: document.requestIndex, credentialID: credential.credentialID))
                        }
                    }
                }
            }
            WalletSection("Information to share") {
                VStack(spacing: 0) {
                    ForEach(review.documents) { document in
                        if let selection = selections.first(where: { $0.requestIndex == document.requestIndex }),
                           let credential = document.credentialOptions.first(where: { $0.credentialID == selection.credentialID }) {
                            ProximityInformationGroup(document: document, credential: credential, selection: selection,
                                sharedElements: Set(selections.filter { $0.credentialID == credential.credentialID }.flatMap(\.disclosedElements)),
                                details: credentialDetailsByID[credential.credentialID], onToggleElement: onToggleElement, enabled: enabled)
                        }
                    }
                }
            }.accessibilityIdentifier("review-information-to-share")

            if allowContinuation { Toggle(
                isOn: Binding(
                    get: { continueAfterResponse },
                    set: onContinueAfterResponseChange
                )
            ) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Stay connected for another request")
                    Text("You will review and approve each new request separately.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .disabled(!enabled)
            .accessibilityIdentifier(WalletAccessibilityID.proximityContinueAfterResponse)
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityReview)
    }
}

extension CredentialDetails {
    func mdocClaims(namespace: String, elementIdentifier: String) -> [ClaimItem] {
        groups
            .flatMap(\.items)
            .filter { claim in
                claim.pathComponents.first == namespace
                    && claim.pathComponents.dropFirst().first == elementIdentifier
            }
    }
}
