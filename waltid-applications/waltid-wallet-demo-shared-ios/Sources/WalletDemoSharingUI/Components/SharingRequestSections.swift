import SwiftUI
import WalletSDK

/// Renders the request concepts of a sharing review: requester, transaction authorization, reader
/// trust, response protection and technical details.
///
/// Only the concepts ``request`` actually carries are rendered. A transport that has no reader
/// authentication or no requester metadata gets no such section rather than a section saying the
/// request is anonymous or unauthenticated - that distinction is what makes an absent section
/// readable as "the protocol has no such notion" instead of "the answer was bad".
public struct SharingRequestSections: View {
    private let request: SharingRequest

    /// Renders the request concepts of a sharing review.
    public init(request: SharingRequest) {
        self.request = request
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let requester = request.requester {
                RequesterSection(requester: requester)
            }

            ForEach(request.transactionData) { group in
                ClaimGroupView(group: group)
            }

            if let readerTrust = request.readerTrust {
                ReaderTrustSection(readerTrust: readerTrust)
            }

            ResponseProtectionSection(protection: request.responseProtection)

            if request.technicalDetails.contains(where: { $0.value?.isPresentableValue == true }) {
                TechnicalDetailsSection(details: request.technicalDetails)
            }
        }
    }
}

/// Who is asking, headed by whatever the request lets the wallet name truthfully.
struct RequesterSection: View {
    let requester: SharingRequester

    private var verifiedOriginDetail: MetadataDetailItem? {
        guard !requester.verifiedOriginIsIdentityName,
              let origin = requester.verifiedOrigin?.presentableValue else { return nil }
        return MetadataDetailItem(label: SharingRequester.verifiedOriginLabel, value: origin)
    }

    private var requesterDetails: [MetadataDetailItem] {
        requester.details.map { MetadataDetailItem(label: $0.label, value: $0.value, linkURI: $0.linkURI) }
            .filter(\.isVisible)
    }

    var body: some View {
        let verifiedOriginDetail = verifiedOriginDetail
        let requesterDetails = requesterDetails
        if requester.identityName != nil || verifiedOriginDetail != nil || !requesterDetails.isEmpty {
            ReviewMetadataSection(
                title: "Requester",
                titleAccessibilityIdentifier: WalletAccessibilityID.presentationVerifierSection
            ) {
                if let identityName = requester.identityName {
                    MetadataIdentityView(
                        display: requester.display,
                        fallbackName: identityName,
                        supportingText: requester.identityNameCaption
                    )
                    if verifiedOriginDetail != nil || !requesterDetails.isEmpty {
                        Divider()
                    }
                }
                if let verifiedOriginDetail {
                    MetadataDetailList(items: [verifiedOriginDetail])
                    if !requesterDetails.isEmpty {
                        Divider()
                    }
                }
                if !requesterDetails.isEmpty {
                    MetadataDisclosure(
                        title: "Requester details",
                        initiallyExpanded: false,
                        accessibilityIdentifier: WalletAccessibilityID.presentationRequesterDetailsToggle
                    ) {
                        MetadataDetailList(items: requesterDetails)
                            .accessibilityIdentifier(WalletAccessibilityID.presentationRequesterDetails)
                    }
                }
            }
        }
    }
}

/// Whether the wallet can name the reader, and why it cannot when it cannot.
struct ReaderTrustSection: View {
    let readerTrust: SharingReaderTrust

    private var headline: String {
        switch readerTrust {
        case .notAuthenticated: return "Reader not authenticated"
        case .pendingVerification: return "Reader authentication will be verified before sharing"
        // Deliberately not phrased as a signature failure: a failed signature never reaches review at
        // all, so saying so here would misdescribe a verified but unrecognised reader.
        case .untrusted: return "Reader identity not trusted by this wallet"
        case .trusted: return "Trusted reader"
        }
    }

    private var explanation: String {
        switch readerTrust {
        case .notAuthenticated:
            return "The request carried no reader signature, so this wallet cannot tell you who is asking."
        case .pendingVerification:
            return "The reader signature is checked when you share, and nothing is sent if it fails."
        case .untrusted(let reason):
            return reason
        case .trusted:
            return "The reader signature was verified and this wallet recognises the reader."
        }
    }

    private var identity: MetadataDetailItem? {
        guard case .trusted(let readerIdentity) = readerTrust else { return nil }
        return MetadataDetailItem(label: "Reader identity", value: readerIdentity)
    }

    var body: some View {
        ReviewMetadataSection(
            title: "Reader authentication",
            titleAccessibilityIdentifier: WalletAccessibilityID.presentationReaderTrustSection
        ) {
            Text(headline)
                .font(.subheadline)
            Text(explanation)
                .font(.caption)
                .foregroundStyle(.secondary)
            if let identity {
                Divider()
                MetadataDetailList(items: [identity])
            }
        }
    }
}

/// What protects the response the wallet is about to produce.
struct ResponseProtectionSection: View {
    let protection: SharingResponseProtection

    private var items: [MetadataDetailItem] {
        var items = [
            MetadataDetailItem(
                label: "Message-level encryption",
                value: protection == .none ? "Not requested" : "Required"
            ),
        ]
        if case let .encrypted(mechanism, keyManagement, contentEncryption, keyID, thumbprint) = protection {
            items += [
                MetadataDetailItem(label: "Encryption mechanism", value: mechanism.displayName),
                MetadataDetailItem(label: "Key management algorithm", value: keyManagement),
                MetadataDetailItem(label: "Content encryption algorithm", value: contentEncryption),
                MetadataDetailItem(label: "Verifier key ID", value: keyID),
                MetadataDetailItem(label: "Verifier key thumbprint", value: thumbprint),
            ]
        }
        return items
    }

    var body: some View {
        ReviewMetadataSection(
            title: "Response protection",
            titleAccessibilityIdentifier: WalletAccessibilityID.presentationResponseProtectionSection
        ) {
            MetadataDetailList(items: items)
        }
    }
}

/// Protocol-level values, collapsed so they inform without competing with the decision.
struct TechnicalDetailsSection: View {
    let details: [SharingDetail]
    @State private var expanded = false

    var body: some View {
        ReviewMetadataSection(
            title: "Technical request details",
            titleAccessibilityIdentifier: WalletAccessibilityID.presentationTechnicalDetailsSection,
            contentInsets: expanded
                ? EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
                : EdgeInsets(top: 2, leading: 16, bottom: 2, trailing: 16)
        ) {
            Button {
                expanded.toggle()
            } label: {
                HStack {
                    Text(expanded ? "Hide details" : "Show details")
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetailsToggle)

            if expanded {
                Divider()
                MetadataDetailList(
                    items: details.map { MetadataDetailItem(label: $0.label, value: $0.value, linkURI: $0.linkURI) }
                )
                .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
            }
        }
    }
}
