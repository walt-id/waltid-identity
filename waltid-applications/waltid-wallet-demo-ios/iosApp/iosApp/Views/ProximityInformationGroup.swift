import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityInformationGroup: View {
    let document: ProximityDocumentReview
    let credential: ProximityCredentialOption
    let selection: ProximityDocumentSelection
    let sharedElements: Set<ProximityElementReference>
    let details: CredentialDetails?
    let onToggleElement: (Int, ProximityElementReference) -> Void
    var enabled: Bool = true

    var body: some View {
        ReviewInformationGroup(title: details?.cardSummary.title ?? credential.label ?? String(localized: "Wallet credential"),
            issuer: details?.cardSummary.issuer ?? credential.issuer, details: details,
            detailsIdentifier: "proximity-details:\(document.requestIndex)",
            claimStatus: { item in
                sharedElements.contains { element in
                    Array(item.pathComponents.prefix(2)) == [element.namespace, element.elementIdentifier]
                } ? "Requested" : "Not shared"
            }) {
            if !document.requiredElements.allSatisfy({ required in
                credential.requestedElements.contains { $0.namespace == required.namespace && $0.elementIdentifier == required.elementIdentifier }
            }) {
                Text("This credential cannot provide all the required data. Choose another credential or decline sharing.")
                    .font(.footnote).foregroundStyle(.red)
            }
            ForEach(Array(credential.requestedElements.enumerated()), id: \.offset) { index, element in
                if index > 0 { Divider() }
                if let reference = try? ProximityElementReference(namespace: element.namespace, elementIdentifier: element.elementIdentifier) {
                    let included = selection.disclosedElements.contains(reference)
                    let required = document.requiredElements.contains(reference)
                    if required { fieldContent(element, included: included, required: true) }
                    else {
                        Toggle(isOn: Binding(get: { included }, set: { _ in onToggleElement(document.requestIndex, reference) })) {
                            fieldContent(element, included: included, required: false)
                        }.toggleStyle(ReviewCheckboxToggleStyle()).disabled(!enabled)
                            .accessibilityIdentifier(WalletAccessibilityID.proximityElement(
                                requestIndex: document.requestIndex, namespace: element.namespace, elementIdentifier: element.elementIdentifier))
                    }
                } else { Text("This requested field cannot be shared.").foregroundStyle(.red) }
            }
            MetadataDisclosure(title: "Technical details", initiallyExpanded: false) {
                MetadataDetailList(items: [
                    MetadataDetailItem(label: "Document type", value: document.documentType),
                    MetadataDetailItem(label: "Device authentication", value: credential.deviceAuthentication.label),
                ] + credential.requestedElements.map {
                    MetadataDetailItem(label: "Requested element", value: "\($0.namespace) / \($0.elementIdentifier)")
                })
            }
        }
    }

    private func fieldContent(_ element: ProximityRequestedElement, included: Bool, required: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            let claims = details?.mdocClaims(namespace: element.namespace, elementIdentifier: element.elementIdentifier) ?? []
            if claims.isEmpty {
                Text(CredentialDisplayVocabulary.humanizedLabel(element.elementIdentifier)).font(.caption.weight(.semibold))
                Text("Value preview unavailable").font(.footnote).foregroundStyle(.red)
            } else { ForEach(claims) { ClaimValueRow(item: $0) } }
            if !included { Text("Not shared").font(.footnote).foregroundStyle(.secondary) }
            if element.intentToRetain { Text("Reader intends to retain this data").font(.footnote).foregroundStyle(.red) }
            if required { Text("Required for this identity check").font(.footnote).foregroundStyle(.secondary) }
        }
    }
}
