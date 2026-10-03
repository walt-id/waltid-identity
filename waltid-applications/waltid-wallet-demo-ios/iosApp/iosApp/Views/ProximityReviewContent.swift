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

            ForEach(review.documents) { document in
                ProximityDocumentContent(
                    document: document,
                    selection: selections.first(where: { $0.requestIndex == document.requestIndex }),
                    credentialDetailsByID: credentialDetailsByID,
                    onSelectCredential: onSelectCredential,
                    onToggleElement: onToggleElement
                )
            }

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
            .accessibilityIdentifier(WalletAccessibilityID.proximityContinueAfterResponse)
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityReview)
    }
}

private struct ProximityDocumentContent: View {
    let document: ProximityDocumentReview
    let selection: ProximityDocumentSelection?
    let credentialDetailsByID: [String: CredentialDetails]
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Credential to share")) {
            if document.credentialOptions.count > 1 {
                Text("Choose a credential").font(.headline)
            }
            ForEach(document.credentialOptions) { credential in
                let details = credentialDetailsByID[credential.credentialID]
                Button {
                    onSelectCredential(document.requestIndex, credential.credentialID)
                } label: {
                    HStack(alignment: .center, spacing: 12) {
                        if document.credentialOptions.count > 1 {
                            Image(
                                systemName: selection?.credentialID == credential.credentialID
                                    ? "largecircle.fill.circle" : "circle"
                            )
                        }
                        if let details {
                            CredentialCardView(details: details, compact: true)
                        } else {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(credential.label ?? String(localized: "Wallet credential"))
                                if let issuer = credential.issuer {
                                    Text(issuer).font(.caption).foregroundStyle(.secondary)
                                }
                                Text("Valid until \(credential.validUntil.formatted(date: .abbreviated, time: .omitted))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(
                    WalletAccessibilityID.proximityCredential(
                        requestIndex: document.requestIndex,
                        credentialID: credential.credentialID
                    )
                )
            }
            if let credential = selectedCredential {
                let details = credentialDetailsByID[credential.credentialID]
                Divider()
                Text("Data to share").font(.headline)
                if !document.requiredElements.allSatisfy({ required in
                    credential.requestedElements.contains {
                        $0.namespace == required.namespace && $0.elementIdentifier == required.elementIdentifier
                    }
                }) {
                    Text("This credential cannot provide all the required data. Choose another credential or decline sharing.")
                        .font(.footnote).foregroundStyle(.red)
                }
                ForEach(Array(credential.requestedElements.enumerated()), id: \.offset) { _, element in
                    if let reference = try? ProximityElementReference(
                        namespace: element.namespace,
                        elementIdentifier: element.elementIdentifier
                    ) {
                        Toggle(isOn: binding(for: reference)) {
                            VStack(alignment: .leading, spacing: 3) {
                                let claims = details?.mdocClaims(
                                    namespace: element.namespace,
                                    elementIdentifier: element.elementIdentifier
                                ) ?? []
                                if !claims.isEmpty {
                                    ForEach(claims) { claim in
                                        ClaimValueRow(item: claim)
                                    }
                                } else {
                                    Text(CredentialDisplayVocabulary.humanizedLabel(element.elementIdentifier))
                                        .font(.caption.weight(.semibold))
                                    Text("Value preview unavailable")
                                        .font(.caption)
                                        .foregroundStyle(.red)
                                }
                                if element.intentToRetain {
                                    Text("Reader intends to retain this data")
                                        .font(.caption)
                                        .foregroundStyle(.red)
                                }
                                if document.requiredElements.contains(reference) {
                                    Text("Required for this identity check").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .toggleStyle(ReviewCheckboxToggleStyle())
                        .disabled(document.requiredElements.contains(reference))
                        .accessibilityIdentifier(
                            WalletAccessibilityID.proximityElement(
                                requestIndex: document.requestIndex,
                                namespace: element.namespace,
                                elementIdentifier: element.elementIdentifier
                            )
                        )
                    } else {
                        Text("This requested field cannot be shared.").foregroundStyle(.red)
                    }
                }
                MetadataDisclosure(title: "Technical details", initiallyExpanded: false) {
                    MetadataDetailList(items: technicalDetails(for: credential))
                }
            }
        }
    }

    private var selectedCredential: ProximityCredentialOption? {
        document.credentialOptions.first { $0.credentialID == selection?.credentialID }
    }

    private func binding(for element: ProximityElementReference) -> Binding<Bool> {
        Binding(
            get: { selection?.disclosedElements.contains(element) == true },
            set: { _ in onToggleElement(document.requestIndex, element) }
        )
    }

    private func technicalDetails(for credential: ProximityCredentialOption) -> [MetadataDetailItem] {
        [
            MetadataDetailItem(label: "Document type", value: document.documentType),
            MetadataDetailItem(
                label: "Device authentication",
                value: credential.deviceAuthentication.label
            ),
        ] + credential.requestedElements.map { element in
            MetadataDetailItem(
                label: "Requested element",
                value: "\(element.namespace) / \(element.elementIdentifier)"
            )
        }
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
