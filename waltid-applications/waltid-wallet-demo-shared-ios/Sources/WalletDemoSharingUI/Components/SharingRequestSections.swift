import SwiftUI
import WalletSDK

/// Renders requester identity plus the technical request facts revealed by tapping that box.
///
/// Only the concepts ``request`` actually carries are rendered. A transport that has no reader
/// authentication or no requester metadata gets no such section rather than a section saying the
/// request is anonymous or unauthenticated.
public struct SharingRequestSections: View {
    private let request: SharingRequest

    public init(request: SharingRequest, compact: Bool = false) {
        self.request = request
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VerifierMetadataCard(request: request)

            ForEach(request.transactionData) { group in
                ClaimGroupView(group: group, collapsible: false)
            }
        }
    }
}

private struct VerifierMetadataCard: View {
    let request: SharingRequest
    @State private var isExpanded = false

    private var requester: SharingRequester? { request.requester }

    private var verifiedOriginDetail: MetadataDetailItem? {
        guard let requester, !requester.verifiedOriginIsIdentityName,
              let origin = requester.verifiedOrigin?.presentableValue else { return nil }
        return MetadataDetailItem(label: SharingRequester.verifiedOriginLabel, value: origin)
    }

    private var requesterDetails: [MetadataDetailItem] {
        requester?.details.map { MetadataDetailItem(label: $0.label, value: $0.value, linkURI: $0.linkURI) }
            .filter(\.isVisible) ?? []
    }

    private var technicalDetails: [MetadataDetailItem] {
        request.technicalDetails
            .map { MetadataDetailItem(label: $0.label, value: $0.value, linkURI: $0.linkURI) }
            .filter(\.isVisible)
    }

    private var hasAnyContent: Bool {
        requester?.identityName != nil ||
            requester?.verifiedOrigin?.presentableValue != nil ||
            !requesterDetails.isEmpty ||
            request.readerTrust != nil ||
            request.responseProtection != .none ||
            !technicalDetails.isEmpty
    }

    var body: some View {
        if hasAnyContent {
            ExpandableMetadataCard(
                title: "Verifier",
                titleAccessibilityIdentifier: WalletAccessibilityID.presentationVerifierSection,
                toggleAccessibilityIdentifier: WalletAccessibilityID.presentationRequesterDetailsToggle,
                isExpanded: $isExpanded
            ) {
                if let identityName = requester?.identityName {
                    MetadataIdentityView(
                        display: requester?.display,
                        fallbackName: identityName,
                        supportingText: requester?.identityNameCaption
                    )
                } else {
                    Text(requester?.verifiedOrigin?.presentableValue ?? "Verifier")
                        .font(.body.weight(.semibold))
                }
            } details: {
                VStack(alignment: .leading, spacing: 12) {
                    let identityItems = [verifiedOriginDetail].compactMap { $0 } + requesterDetails
                    if !identityItems.isEmpty {
                        MetadataDetailList(items: identityItems)
                            .accessibilityIdentifier(WalletAccessibilityID.presentationRequesterDetails)
                    }
                    if let readerTrust = request.readerTrust {
                        if !identityItems.isEmpty { Divider() }
                        ReaderTrustDetails(readerTrust: readerTrust)
                    }
                    if !identityItems.isEmpty || request.readerTrust != nil { Divider() }
                    ResponseProtectionDetails(protection: request.responseProtection)
                    if !technicalDetails.isEmpty {
                        Divider()
                        Text("Technical request details")
                            .font(.caption.weight(.semibold))
                            .accessibilityIdentifier(WalletAccessibilityID.presentationTechnicalDetailsSection)
                        MetadataDetailList(items: technicalDetails)
                            .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
                    }
                }
            }
        }
    }
}

private struct ReaderTrustDetails: View {
    let readerTrust: SharingReaderTrust

    private var headline: String {
        switch readerTrust {
        case .notAuthenticated: return "Reader not authenticated"
        case .pendingVerification: return "Reader authentication will be verified before sharing"
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

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(headline).font(.subheadline.weight(.medium))
            Text(explanation).font(.caption).foregroundStyle(.secondary)
            if case .trusted(let readerIdentity) = readerTrust {
                MetadataDetailList(items: [MetadataDetailItem(label: "Reader identity", value: readerIdentity)])
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationReaderTrustSection)
    }
}

private struct ResponseProtectionDetails: View {
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
        VStack(alignment: .leading, spacing: 8) {
            Text("Response protection")
                .font(.caption.weight(.semibold))
            MetadataDetailList(items: items)
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationResponseProtectionSection)
    }
}
