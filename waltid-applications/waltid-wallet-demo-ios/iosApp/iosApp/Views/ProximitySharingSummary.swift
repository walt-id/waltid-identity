import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityPreparedSharingSummary: View {
    let sharing: ProximityPreparedSharing
    let credentialDetailsByID: [String: CredentialDetails]

    var body: some View {
        ReviewMetadataSection(title: "Ready for one share") {
            ProximityPreparedSharingCountdown(sharing: sharing)
            Text("Only the approved reader and data can be used. Cancel to withdraw this approval.")
            ProximityDisclosureSummary(review: sharing.review, submission: sharing.submission, credentialDetailsByID: credentialDetailsByID)
        }
    }
}

struct ProximityPreparedSharingCountdown: View {
    let sharing: ProximityPreparedSharing

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { _ in
            Text("Approval expires in \(sharing.remainingSeconds) seconds").font(.subheadline)
                .accessibilityIdentifier("proximity-prepared-countdown")
        }
    }
}

struct ProximityDisclosureSummary: View {
    let review: ProximityReview
    let submission: ProximitySubmission
    let credentialDetailsByID: [String: CredentialDetails]
    var initiallyExpanded = false

    var body: some View {
        let names = Array(Set(review.readerAuthentication.compactMap(\.displayName))).sorted()
        Text(names.isEmpty ? String(localized: "Reader identity unavailable") : names.joined(separator: ", ")).font(.headline)
        MetadataDisclosure(title: "Selected data", initiallyExpanded: initiallyExpanded) {
            ForEach(submission.documents, id: \.requestIndex) { selected in
                if let document = review.documents.first(where: { $0.requestIndex == selected.requestIndex }),
                   let credential = document.credentialOptions.first(where: { $0.credentialID == selected.credentialID }) {
                    let details = credentialDetailsByID[credential.credentialID]
                    CredentialSummaryRow(summary: details?.cardSummary ?? CredentialCardSummary(
                        title: credential.label ?? String(localized: "Credential"), issuer: credential.issuer ?? ""))
                    ForEach(Array(credential.requestedElements.filter { element in
                        selected.disclosedElements.contains {
                            $0.namespace == element.namespace && $0.elementIdentifier == element.elementIdentifier
                        }
                    }.enumerated()), id: \.offset) { _, element in
                        let labels = details?.mdocClaims(namespace: element.namespace, elementIdentifier: element.elementIdentifier).map(\.label) ?? []
                        Text(labels.isEmpty ? CredentialDisplayVocabulary.humanizedLabel(element.elementIdentifier) : labels.joined(separator: ", "))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if element.intentToRetain {
                            Text("The reader intends to retain this information.").font(.footnote).foregroundStyle(.red)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
        }
    }
}
